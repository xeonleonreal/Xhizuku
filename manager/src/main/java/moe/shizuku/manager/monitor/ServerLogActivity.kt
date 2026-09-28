package moe.shizuku.manager.monitor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.shizuku.manager.R
import moe.shizuku.manager.app.AppActivity
import moe.shizuku.manager.ui.compose.ExpressiveButtonSpec
import moe.shizuku.manager.ui.compose.ExpressiveButtons
import moe.shizuku.manager.ui.compose.ExpressiveCard
import moe.shizuku.manager.ui.compose.MonospaceLog
import moe.shizuku.manager.ui.compose.ShizukuExpressiveTheme
import moe.shizuku.manager.ui.compose.ShizukuLazyScaffold
import rikka.shizuku.Shizuku

class ServerLogActivity : AppActivity() {

    private var logText by mutableStateOf("")
    private var loading by mutableStateOf(true)
    private var needsAuth by mutableStateOf(false)

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            loadLog()
        } else {
            needsAuth = true
            loading = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Shizuku.addRequestPermissionResultListener(permissionListener)

        setContent {
            ShizukuExpressiveTheme {
                ShizukuLazyScaffold(
                    title = stringResource(R.string.log_title),
                    onNavigateUp = { finish() }
                ) {
                    if (needsAuth) {
                        item {
                            ExpressiveCard(
                                icon = R.drawable.ic_outline_info_24,
                                title = stringResource(R.string.log_title),
                                body = stringResource(R.string.log_no_permission)
                            ) {
                                ExpressiveButtons(
                                    listOf(
                                        ExpressiveButtonSpec(
                                            label = R.string.log_authorize,
                                            icon = R.drawable.ic_server_ok_24dp,
                                            primary = true,
                                            onClick = { requestAccess() }
                                        )
                                    )
                                )
                            }
                        }
                    } else {
                        item {
                            MonospaceLog(
                                text = when {
                                    loading -> getString(R.string.notification_working)
                                    logText.isBlank() -> getString(R.string.log_empty)
                                    else -> logText
                                }
                            )
                        }
                        item {
                            ExpressiveButtons(
                                listOf(
                                    ExpressiveButtonSpec(
                                        label = R.string.home_refresh,
                                        icon = R.drawable.ic_server_restart,
                                        primary = true,
                                        onClick = { loadLog() }
                                    ),
                                    ExpressiveButtonSpec(
                                        label = R.string.home_diagnostics_copy,
                                        icon = R.drawable.ic_content_copy_24,
                                        enabled = logText.isNotBlank(),
                                        onClick = { copyLog() }
                                    ),
                                    ExpressiveButtonSpec(
                                        label = R.string.home_adb_dialog_view_command_button_send,
                                        icon = R.drawable.ic_outline_open_in_new_24,
                                        enabled = logText.isNotBlank(),
                                        onClick = { shareLog() }
                                    )
                                )
                            )
                        }
                    }
                }
            }
        }

        loadLog()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            Shizuku.removeRequestPermissionResultListener(permissionListener)
        } catch (_: Throwable) {
        }
    }

    private fun requestAccess() {
        if (!Shizuku.pingBinder()) {
            Toast.makeText(this, R.string.log_not_running, Toast.LENGTH_SHORT).show()
            return
        }
        try {
            Shizuku.requestPermission(1001)
            Toast.makeText(this, R.string.notification_working, Toast.LENGTH_SHORT).show()
        } catch (e: Throwable) {
            Toast.makeText(this, "Exception: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun loadLog() {
        loading = true
        logText = ""
        needsAuth = false
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { collectLog() }
            if (result == AUTH_REQUIRED) {
                needsAuth = true
                logText = ""
            } else {
                logText = result
            }
            loading = false
        }
    }

    private fun collectLog(): String {
        if (!Shizuku.pingBinder()) {
            return getString(R.string.log_not_running)
        }
        try {
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                return AUTH_REQUIRED
            }
        } catch (_: Throwable) {
            return AUTH_REQUIRED
        }
        val raw = XStatusCollector.runCommand("logcat -d -t 800") ?: return ""
        return raw.lineSequence()
            .filter { line -> line.contains("shizuku", ignoreCase = true) }
            .take(400)
            .joinToString("\n")
            .take(60_000)
    }

    companion object {
        private const val AUTH_REQUIRED = "\u0000AUTH_REQUIRED\u0000"
    }

    private fun copyLog() {
        val text = logText
        if (text.isBlank()) return
        getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText(getString(R.string.log_title), text))
        Toast.makeText(this, R.string.home_diagnostics_copied, Toast.LENGTH_SHORT).show()
    }

    private fun shareLog() {
        val text = logText
        if (text.isBlank()) return
        var intent = Intent(Intent.ACTION_SEND)
        intent.type = "text/plain"
        intent.putExtra(Intent.EXTRA_TEXT, text)
        intent = Intent.createChooser(
            intent,
            getString(R.string.home_adb_dialog_view_command_button_send)
        )
        startActivity(intent)
    }
}
