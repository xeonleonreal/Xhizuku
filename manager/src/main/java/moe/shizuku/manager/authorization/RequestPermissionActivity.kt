package moe.shizuku.manager.authorization

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.activity.compose.setContent
import androidx.appcompat.app.AlertDialog as AppCompatAlertDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.material3.Button as WearButton
import androidx.wear.compose.material3.Text as WearText
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import moe.shizuku.manager.Helps
import moe.shizuku.manager.R
import moe.shizuku.manager.app.AppActivity
import moe.shizuku.manager.ktx.toHtml
import moe.shizuku.manager.settings.ChoiceDialog
import moe.shizuku.manager.settings.ChoiceOption
import moe.shizuku.manager.settings.WearChoiceDialog
import moe.shizuku.manager.ui.compose.ShizukuExpressiveTheme
import moe.shizuku.manager.ui.compose.WearScreenScaffold
import moe.shizuku.manager.ui.compose.WearScreenTitle
import moe.shizuku.manager.ui.compose.WearShizukuTheme
import moe.shizuku.manager.ui.compose.htmlToPlainText
import moe.shizuku.manager.utils.Logger.LOGGER
import rikka.core.res.resolveColor
import rikka.html.text.HtmlCompat
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuApiConstants.REQUEST_PERMISSION_REPLY_ALLOWED
import rikka.shizuku.ShizukuApiConstants.REQUEST_PERMISSION_REPLY_IS_ONETIME
import rikka.shizuku.server.ServerConstants
import rikka.shizuku.server.ktx.workerHandler
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

private enum class GrantChoice {
    ALLOW_ALWAYS,
    ALLOW_ONCE,
    ALLOW_TEMP,
    DENY_TEMP,
    DENY
}

private val TEMP_DURATIONS = listOf(
    15 to R.string.grant_dialog_duration_15m,
    30 to R.string.grant_dialog_duration_30m,
    60 to R.string.grant_dialog_duration_1h,
    480 to R.string.grant_dialog_duration_8h,
    1440 to R.string.grant_dialog_duration_24h
)

class RequestPermissionActivity : AppActivity() {

    @Volatile
    private var resultSent = false

    private fun setResult(
        requestUid: Int,
        requestPid: Int,
        requestCode: Int,
        allowed: Boolean,
        onetime: Boolean,
        expiryMillis: Long = 0L
    ) {
        resultSent = true
        val data = Bundle()
        data.putBoolean(REQUEST_PERMISSION_REPLY_ALLOWED, allowed)
        data.putBoolean(REQUEST_PERMISSION_REPLY_IS_ONETIME, onetime)
        if (expiryMillis > 0L) {
            data.putLong(ServerConstants.REQUEST_PERMISSION_REPLY_EXPIRY, expiryMillis)
        }
        try {
            Shizuku.dispatchPermissionConfirmationResult(requestUid, requestPid, requestCode, data)
        } catch (e: Throwable) {
            LOGGER.e("dispatchPermissionConfirmationResult")
        }
    }

    private fun dispatchChoice(
        requestUid: Int,
        requestPid: Int,
        requestCode: Int,
        choice: GrantChoice,
        durationMinutes: Int
    ) {
        when (choice) {
            GrantChoice.ALLOW_ALWAYS -> setResult(requestUid, requestPid, requestCode, allowed = true, onetime = false)
            GrantChoice.ALLOW_ONCE -> setResult(requestUid, requestPid, requestCode, allowed = true, onetime = true)
            GrantChoice.ALLOW_TEMP -> setResult(
                requestUid, requestPid, requestCode, allowed = true, onetime = false,
                expiryMillis = System.currentTimeMillis() + durationMinutes * 60_000L
            )
            GrantChoice.DENY_TEMP -> setResult(
                requestUid, requestPid, requestCode, allowed = false, onetime = false,
                expiryMillis = System.currentTimeMillis() + durationMinutes * 60_000L
            )
            GrantChoice.DENY -> setResult(requestUid, requestPid, requestCode, allowed = false, onetime = false)
        }
    }

    private fun checkSelfPermission(): Boolean {
        val permission = Shizuku.checkRemotePermission("android.permission.GRANT_RUNTIME_PERMISSIONS") == PackageManager.PERMISSION_GRANTED
        if (permission) return true

        val icon = getDrawable(R.drawable.ic_system_icon)
        icon?.setTint(theme.resolveColor(android.R.attr.colorAccent))

        val dialog = MaterialAlertDialogBuilder(this)
                .setIcon(icon)
                .setTitle("Shizuku: ${getString(R.string.app_management_dialog_adb_is_limited_title)}")
                .setMessage(getString(R.string.app_management_dialog_adb_is_limited_message, Helps.ADB.get()).toHtml(HtmlCompat.FROM_HTML_OPTION_TRIM_WHITESPACE))
                .setPositiveButton(android.R.string.ok, null)
                .setOnDismissListener { finish() }
                .create()
        dialog.setOnShowListener {
            (it as AppCompatAlertDialog).findViewById<TextView>(android.R.id.message)?.movementMethod = LinkMovementMethod.getInstance()
        }
        try {
            dialog.show()
        } catch (ignored: Throwable) {
        }
        return false
    }

    private fun waitForBinder(): Boolean {
        val countDownLatch = CountDownLatch(1)

        val listener = object : Shizuku.OnBinderReceivedListener {
            override fun onBinderReceived() {
                countDownLatch.countDown()
                Shizuku.removeBinderReceivedListener(this)
            }
        }

        Shizuku.addBinderReceivedListenerSticky(listener, workerHandler)

        return try {
            countDownLatch.await(5, TimeUnit.SECONDS)
            true
        } catch (e: TimeoutException) {
            LOGGER.e(e, "Binder not received in 5s")
            false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!waitForBinder()) {
            finish()
            return
        }

        val uid = intent.getIntExtra("uid", -1)
        val pid = intent.getIntExtra("pid", -1)
        val requestCode = intent.getIntExtra("requestCode", -1)
        val ai = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("applicationInfo", ApplicationInfo::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("applicationInfo")
        }
        if (uid == -1 || pid == -1 || ai == null) {
            finish()
            return
        }
        if (!checkSelfPermission()) {
            setResult(uid, pid, requestCode, allowed = false, onetime = true)
            return
        }

        // Dismissing without choosing (e.g. back press on watch) counts as deny.
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!resultSent) {
                    setResult(uid, pid, requestCode, allowed = false, onetime = true)
                }
                finish()
            }
        })

        val label = try {
            ai.loadLabel(packageManager)
        } catch (e: Exception) {
            ai.packageName
        }

        setContent {
            val isWatch = moe.shizuku.manager.utils.EnvironmentUtils.isWatch(this@RequestPermissionActivity)
            if (isWatch) {
                WearShizukuTheme {
                    WearGrantScreen(
                        label = label.toString(),
                        onChoose = { choice, minutes ->
                            if (choice == null) {
                                setResult(uid, pid, requestCode, allowed = false, onetime = true)
                            } else {
                                dispatchChoice(uid, pid, requestCode, choice, minutes)
                            }
                            finish()
                        }
                    )
                }
            } else {
                ShizukuExpressiveTheme {
                    PhoneGrantDialog(
                        label = label.toString(),
                        onCancel = {
                            setResult(uid, pid, requestCode, allowed = false, onetime = true)
                            finish()
                        },
                        onChoose = { choice, minutes ->
                            dispatchChoice(uid, pid, requestCode, choice, minutes)
                            finish()
                        }
                    )
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun GrantOptionRow(
        title: String,
        selected: Boolean,
        onClick: () -> Unit
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }

    @androidx.compose.runtime.Composable
    private fun PhoneGrantDialog(
        label: String,
        onCancel: () -> Unit,
        onChoose: (GrantChoice, Int) -> Unit
    ) {
        var choice by remember { mutableStateOf<GrantChoice?>(null) }
        var showDuration by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = {},
            icon = {
                Icon(
                    painter = painterResource(R.drawable.ic_system_icon),
                    contentDescription = null
                )
            },
            title = {
                Text(stringResource(R.string.app_name))
            },
            text = {
                Column {
                    Text(
                        text = htmlToPlainText(
                            getString(
                                R.string.permission_warning_template,
                                label,
                                getString(R.string.permission_group_description)
                            )
                        )
                    )
                    GrantOptionRow(
                        title = stringResource(R.string.grant_dialog_button_allow_always),
                        selected = choice == GrantChoice.ALLOW_ALWAYS,
                        onClick = { choice = GrantChoice.ALLOW_ALWAYS }
                    )
                    GrantOptionRow(
                        title = stringResource(R.string.grant_dialog_allow_once),
                        selected = choice == GrantChoice.ALLOW_ONCE,
                        onClick = { choice = GrantChoice.ALLOW_ONCE }
                    )
                    GrantOptionRow(
                        title = stringResource(R.string.grant_dialog_allow_temp),
                        selected = choice == GrantChoice.ALLOW_TEMP,
                        onClick = { choice = GrantChoice.ALLOW_TEMP }
                    )
                    GrantOptionRow(
                        title = stringResource(R.string.grant_dialog_deny_temp),
                        selected = choice == GrantChoice.DENY_TEMP,
                        onClick = { choice = GrantChoice.DENY_TEMP }
                    )
                    GrantOptionRow(
                        title = stringResource(R.string.grant_dialog_button_deny),
                        selected = choice == GrantChoice.DENY,
                        onClick = { choice = GrantChoice.DENY }
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = choice != null,
                    onClick = {
                        val selected = choice ?: return@Button
                        if (selected == GrantChoice.ALLOW_TEMP || selected == GrantChoice.DENY_TEMP) {
                            showDuration = true
                        } else {
                            onChoose(selected, 0)
                        }
                    }
                ) {
                    Text(stringResource(R.string.grant_dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = onCancel) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.extraLarge
        )

        if (showDuration) {
            ChoiceDialog(
                title = stringResource(R.string.grant_dialog_duration_title),
                choices = TEMP_DURATIONS.map {
                    ChoiceOption(title = getString(it.second))
                },
                selectedIndex = 1,
                onDismiss = { showDuration = false },
                onSelect = { index ->
                    val selected = choice
                    showDuration = false
                    if (selected == GrantChoice.ALLOW_TEMP || selected == GrantChoice.DENY_TEMP) {
                        onChoose(selected, TEMP_DURATIONS[index].first)
                    }
                }
            )
        }
    }

    @androidx.compose.runtime.Composable
    private fun WearGrantScreen(label: String, onChoose: (GrantChoice?, Int) -> Unit) {
        var tempChoice by remember { mutableStateOf<GrantChoice?>(null) }
        var showDuration by remember { mutableStateOf(false) }

        WearScreenScaffold { state ->
            TransformingLazyColumn(
                state = state,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    WearScreenTitle(
                        icon = R.drawable.ic_system_icon,
                        title = stringResource(R.string.app_name)
                    )
                }
                item {
                    WearText(
                        text = htmlToPlainText(
                            getString(
                                R.string.permission_warning_template,
                                label,
                                getString(R.string.permission_group_description)
                            )
                        )
                    )
                }
                item {
                    WearButton(
                        onClick = { onChoose(GrantChoice.ALLOW_ALWAYS, 0) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        WearText(stringResource(R.string.grant_dialog_button_allow_always))
                    }
                }
                item {
                    WearButton(
                        onClick = { onChoose(GrantChoice.ALLOW_ONCE, 0) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        WearText(stringResource(R.string.grant_dialog_allow_once))
                    }
                }
                item {
                    WearButton(
                        onClick = { tempChoice = GrantChoice.ALLOW_TEMP; showDuration = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        WearText(stringResource(R.string.grant_dialog_allow_temp))
                    }
                }
                item {
                    WearButton(
                        onClick = { tempChoice = GrantChoice.DENY_TEMP; showDuration = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        WearText(stringResource(R.string.grant_dialog_deny_temp))
                    }
                }
                item {
                    WearButton(
                        onClick = { onChoose(GrantChoice.DENY, 0) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        WearText(stringResource(R.string.grant_dialog_button_deny))
                    }
                }
            }
        }

        if (showDuration) {
            val pending = tempChoice
            WearChoiceDialog(
                title = stringResource(R.string.grant_dialog_duration_title),
                choices = TEMP_DURATIONS.map {
                    ChoiceOption(title = getString(it.second))
                },
                selectedIndex = 1,
                onDismiss = { showDuration = false; tempChoice = null },
                onSelect = { index ->
                    showDuration = false
                    tempChoice = null
                    if (pending == GrantChoice.ALLOW_TEMP || pending == GrantChoice.DENY_TEMP) {
                        onChoose(pending, TEMP_DURATIONS[index].first)
                    }
                }
            )
        }
    }
}
