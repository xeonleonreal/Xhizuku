package moe.shizuku.manager.onboarding

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.adb.AdbPairingTutorialActivity
import moe.shizuku.manager.app.AppActivity
import moe.shizuku.manager.monitor.ServerMonitorService
import moe.shizuku.manager.starter.Starter
import moe.shizuku.manager.starter.StarterActivity
import moe.shizuku.manager.ui.compose.ExpressiveCard
import moe.shizuku.manager.ui.compose.MonospaceLog
import moe.shizuku.manager.ui.compose.ShizukuExpressiveTheme
import moe.shizuku.manager.ui.compose.ShizukuScaffold
import moe.shizuku.manager.utils.EnvironmentUtils

object OnboardingPrefs {

    private const val KEY_ONBOARDING_DONE = "onboarding_completed"

    fun isDone(context: Context): Boolean {
        return ShizukuSettings.getPreferences().getBoolean(KEY_ONBOARDING_DONE, false)
    }

    fun setDone(context: Context) {
        ShizukuSettings.getPreferences().edit().putBoolean(KEY_ONBOARDING_DONE, true).apply()
    }
}

class OnboardingActivity : AppActivity() {

    private var notificationsGranted by mutableStateOf(false)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationsGranted = granted
        if (granted) {
            ServerMonitorService.startIfEnabled(this)
        } else {
            Toast.makeText(this, R.string.monitor_needs_permission, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        notificationsGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

        setContent {
            var page by mutableIntStateOf(0)

            ShizukuExpressiveTheme {
                ShizukuScaffold(
                    title = stringResource(R.string.onboarding_title),
                    bottomBar = {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            if (page > 0) {
                                TextButton(onClick = { page-- }) {
                                    Text(text = stringResource(R.string.onboarding_back))
                                }
                            } else {
                                TextButton(
                                    onClick = {
                                        OnboardingPrefs.setDone(this@OnboardingActivity)
                                        finish()
                                    }
                                ) {
                                    Text(text = stringResource(R.string.onboarding_skip))
                                }
                            }
                            if (page < LAST_PAGE) {
                                Button(onClick = { page++ }) {
                                    Text(text = stringResource(R.string.onboarding_next))
                                }
                            } else {
                                Button(
                                    onClick = {
                                        OnboardingPrefs.setDone(this@OnboardingActivity)
                                        finish()
                                    }
                                ) {
                                    Text(text = stringResource(R.string.onboarding_finish))
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentPadding = PaddingValues(
                            start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            LinearProgressIndicator(
                                progress = { (page + 1) / (LAST_PAGE + 1f) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        when (page) {
                            0 -> item {
                                ExpressiveCard(
                                    icon = R.drawable.ic_system_icon,
                                    title = stringResource(R.string.onboarding_title),
                                    body = stringResource(R.string.onboarding_welcome)
                                )
                            }
                            1 -> item { MigrateStep() }
                            2 -> item { StartMethodStep() }
                            3 -> item { BatteryStep() }
                            4 -> item { NotificationsStep() }
                            else -> item { DoneStep() }
                        }
                    }
                }
            }
        }
    }

    private fun findConflictingApp(): String? {
        val candidates = listOf("moe.shizuku.privileged.api", "kerneldroid.nightzuku")
        for (pkg in candidates) {
            try {
                packageManager.getPackageInfo(pkg, 0)
                if (pkg != packageName) return pkg
            } catch (_: PackageManager.NameNotFoundException) {
            }
        }
        return null
    }

    @androidx.compose.runtime.Composable
    private fun MigrateStep() {
        val conflicting = findConflictingApp()
        ExpressiveCard(
            icon = R.drawable.ic_warning_24,
            title = stringResource(R.string.onboarding_migrate_title),
            body = stringResource(R.string.onboarding_migrate_body),
            danger = conflicting != null
        ) {
            if (conflicting != null) {
                Text(text = stringResource(R.string.onboarding_migrate_found, conflicting))
                FilledTonalButton(
                    onClick = {
                        try {
                            startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:$conflicting")
                                )
                            )
                        } catch (_: Throwable) {
                        }
                    }
                ) {
                    Text(text = stringResource(R.string.onboarding_migrate_open))
                }
            } else {
                Text(text = stringResource(R.string.onboarding_migrate_clear))
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun StartMethodStep() {
        ExpressiveCard(
            icon = R.drawable.ic_server_start_24dp,
            title = stringResource(R.string.onboarding_start_root),
            body = stringResource(R.string.onboarding_start_root_summary),
            onClick = {
                startActivity(
                    Intent(this, StarterActivity::class.java).apply {
                        putExtra(StarterActivity.EXTRA_IS_ROOT, true)
                    }
                )
            }
        ) {
            FilledTonalButton(
                onClick = {
                    startActivity(
                        Intent(this@OnboardingActivity, StarterActivity::class.java).apply {
                            putExtra(StarterActivity.EXTRA_IS_ROOT, true)
                        }
                    )
                }
            ) {
                Text(text = stringResource(R.string.onboarding_start_root_go))
            }
        }
        ExpressiveCard(
            icon = R.drawable.ic_wadb_24,
            title = stringResource(R.string.onboarding_start_wireless),
            body = stringResource(R.string.onboarding_start_wireless_summary),
            onClick = {
                startActivity(Intent(this, AdbPairingTutorialActivity::class.java))
            }
        ) {
            FilledTonalButton(
                onClick = {
                    startActivity(
                        Intent(this@OnboardingActivity, AdbPairingTutorialActivity::class.java)
                    )
                }
            ) {
                Text(text = stringResource(R.string.onboarding_start_wireless_go))
            }
        }
        ExpressiveCard(
            icon = R.drawable.ic_adb_24dp,
            title = stringResource(R.string.onboarding_start_adb),
            body = stringResource(R.string.onboarding_start_adb_summary)
        ) {
            MonospaceLog(text = Starter.adbCommand)
            FilledTonalButton(
                onClick = {
                    getSystemService(ClipboardManager::class.java).setPrimaryClip(
                        ClipData.newPlainText("adb", Starter.adbCommand)
                    )
                    Toast.makeText(
                        this@OnboardingActivity,
                        R.string.home_diagnostics_copied,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            ) {
                Text(text = stringResource(R.string.onboarding_copy_command))
            }
        }
    }

    private fun isBatteryExemptionGranted(): Boolean {
        val powerManager = getSystemService(PowerManager::class.java) ?: return true
        return powerManager.isIgnoringBatteryOptimizations(packageName)
    }

    private fun requestBatteryExemption() {
        try {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")
                )
            )
        } catch (_: Throwable) {
            try {
                startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:$packageName")
                    )
                )
            } catch (_: Throwable) {
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun BatteryStep() {
        ExpressiveCard(
            icon = R.drawable.ic_warning_24,
            title = stringResource(R.string.battery_title),
            body = stringResource(R.string.battery_body)
        ) {
            FilledTonalButton(
                onClick = { requestBatteryExemption() }
            ) {
                Text(text = stringResource(R.string.battery_allow))
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun NotificationsStep() {
        ExpressiveCard(
            icon = R.drawable.ic_outline_notifications_active_24,
            title = stringResource(R.string.onboarding_notifications_title),
            body = stringResource(R.string.onboarding_notifications_body)
        ) {
            if (!notificationsGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                FilledTonalButton(
                    onClick = {
                        notificationPermissionLauncher.launch(
                            android.Manifest.permission.POST_NOTIFICATIONS
                        )
                    }
                ) {
                    Text(text = stringResource(R.string.onboarding_allow_notifications))
                }
            } else {
                Text(text = stringResource(R.string.onboarding_migrate_clear))
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun DoneStep() {
        ExpressiveCard(
            icon = R.drawable.ic_server_ok_24dp,
            title = stringResource(R.string.onboarding_done_title),
            body = stringResource(R.string.onboarding_done_body)
        )
    }

    companion object {
        private const val LAST_PAGE = 5

        fun shouldShow(context: Context): Boolean {
            if (EnvironmentUtils.isWatch(context) || EnvironmentUtils.isTV(context)) return false
            return !OnboardingPrefs.isDone(context)
        }
    }
}
