package moe.shizuku.manager.diagnostics

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.shizuku.manager.R
import moe.shizuku.manager.app.AppActivity
import moe.shizuku.manager.authorization.AuthorizationManager
import moe.shizuku.manager.monitor.ServerLogActivity
import moe.shizuku.manager.starter.ServerRestarter
import moe.shizuku.manager.ui.compose.ExpressiveCard
import moe.shizuku.manager.ui.compose.ExpressiveSwitch
import moe.shizuku.manager.ui.compose.MainTab
import moe.shizuku.manager.ui.compose.MainTabBar
import moe.shizuku.manager.ui.compose.ShizukuExpressiveTheme
import moe.shizuku.manager.ui.compose.ShizukuLazyScaffold
import moe.shizuku.manager.ui.compose.openMainTab
import rikka.shizuku.Shizuku

class DebugMenuActivity : AppActivity() {

    private var refreshTick by mutableStateOf(0)

    override fun onResume() {
        super.onResume()
        refreshTick++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            var serverInfo by mutableStateOf("")
            var showRevokeConfirm by mutableStateOf(false)
            var working by mutableStateOf(false)
            var showWarning by mutableStateOf(shouldShowWarning())
            var spoofOnline by mutableStateOf(DevSpoof.spoofOnline())
            var spoofOutcomes by mutableStateOf(DevSpoof.spoofOutcomes())

            LaunchedEffect(refreshTick) {
                serverInfo = withContext(Dispatchers.IO) { collectServerInfo() }
            }

            ShizukuExpressiveTheme {
                ShizukuLazyScaffold(
                    title = stringResource(R.string.dev_title),
                    onNavigateUp = { finish() },
                    bottomBar = {
                        MainTabBar(selected = MainTab.DEBUG, onSelect = ::openMainTab)
                    }
                ) {
                    item {
                        ExpressiveCard(
                            icon = R.drawable.ic_server_ok_24dp,
                            title = stringResource(R.string.extras_server_info_title),
                            body = serverInfo
                        )
                    }
                    item {
                        ExpressiveCard(
                            icon = R.drawable.ic_server_ok_24dp,
                            title = stringResource(R.string.dev_spoof_online),
                            body = stringResource(R.string.dev_spoof_summary),
                            content = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    ExpressiveSwitch(
                                        checked = spoofOnline,
                                        onCheckedChange = {
                                            DevSpoof.setSpoofOnline(it)
                                            spoofOnline = it
                                        }
                                    )
                                }
                            }
                        )
                    }
                    item {
                        ExpressiveCard(
                            icon = R.drawable.ic_server_ok_24dp,
                            title = stringResource(R.string.dev_spoof_outcomes),
                            body = stringResource(R.string.dev_spoof_summary),
                            content = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    ExpressiveSwitch(
                                        checked = spoofOutcomes,
                                        onCheckedChange = {
                                            DevSpoof.setSpoofOutcomes(it)
                                            spoofOutcomes = it
                                        }
                                    )
                                }
                            }
                        )
                    }
                    item {
                        ExpressiveCard(
                            icon = R.drawable.ic_outline_info_24,
                            title = stringResource(R.string.log_title),
                            body = stringResource(R.string.log_summary),
                            onClick = {
                                startActivity(Intent(this@DebugMenuActivity, ServerLogActivity::class.java))
                            }
                        )
                    }
                    item {
                        ExpressiveCard(
                            icon = R.drawable.ic_server_restart,
                            title = stringResource(R.string.dev_restart_server),
                            body = "",
                            enabled = !working,
                            onClick = {
                                working = true
                                lifecycleScope.launch {
                                    val ok = withContext(Dispatchers.IO) {
                                        try {
                                            ServerRestarter.restart(this@DebugMenuActivity)
                                        } catch (_: Throwable) {
                                            false
                                        }
                                    }
                                    working = false
                                    refreshTick++
                                    Toast.makeText(
                                        this@DebugMenuActivity,
                                        if (ok) R.string.dev_done else R.string.notification_service_start_failed,
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        )
                    }
                    item {
                        ExpressiveCard(
                            icon = R.drawable.ic_warning_24,
                            title = stringResource(R.string.dev_revoke_all),
                            body = "",
                            danger = true,
                            enabled = !working,
                            onClick = { showRevokeConfirm = true }
                        )
                    }
                }

                if (showRevokeConfirm) {
                    AlertDialog(
                        onDismissRequest = { showRevokeConfirm = false },
                        title = { Text(stringResource(R.string.dev_revoke_all)) },
                        text = { Text(stringResource(R.string.dev_revoke_text)) },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showRevokeConfirm = false
                                    working = true
                                    lifecycleScope.launch {
                                        withContext(Dispatchers.IO) { revokeAll() }
                                        working = false
                                        refreshTick++
                                        Toast.makeText(
                                            this@DebugMenuActivity,
                                            R.string.dev_done,
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            ) {
                                Text(stringResource(android.R.string.ok))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showRevokeConfirm = false }) {
                                Text(stringResource(android.R.string.cancel))
                            }
                        }
                    )
                }

                if (showWarning) {
                    AlertDialog(
                        onDismissRequest = {
                            dismissWarning()
                            showWarning = false
                            finish()
                        },
                        title = { Text(stringResource(R.string.dev_warning_title)) },
                        text = { Text(stringResource(R.string.dev_warning_text)) },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    dismissWarning()
                                    showWarning = false
                                }
                            ) {
                                Text(stringResource(android.R.string.ok))
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    dismissWarning()
                                    showWarning = false
                                    finish()
                                }
                            ) {
                                Text(stringResource(android.R.string.cancel))
                            }
                        }
                    )
                }
            }
        }
    }

    companion object {
        private const val KEY_DEV_WARNING_SHOWN_AT = "dev_warning_shown_at"
        private const val WARNING_INTERVAL_MS = 24 * 60 * 60 * 1000L

        private fun shouldShowWarning(): Boolean {
            val last = moe.shizuku.manager.ShizukuSettings.getPreferences()
                .getLong(KEY_DEV_WARNING_SHOWN_AT, 0L)
            return System.currentTimeMillis() - last > WARNING_INTERVAL_MS
        }

        private fun dismissWarning() {
            moe.shizuku.manager.ShizukuSettings.getPreferences().edit()
                .putLong(KEY_DEV_WARNING_SHOWN_AT, System.currentTimeMillis())
                .apply()
        }

        private fun collectServerInfo(): String {
            return try {
                if (!Shizuku.pingBinder()) return ""
                "API ${Shizuku.getVersion()}.${Shizuku.getServerPatchVersion()} · uid ${Shizuku.getUid()}"
            } catch (_: Throwable) {
                ""
            }
        }

        private fun revokeAll() {
            try {
                val packages = AuthorizationManager.getPackages()
                for (pi in packages) {
                    try {
                        val uid = pi.applicationInfo?.uid ?: continue
                        AuthorizationManager.revoke(pi.packageName, uid)
                    } catch (_: Throwable) {
                    }
                }
            } catch (_: Throwable) {
            }
        }
    }
}
