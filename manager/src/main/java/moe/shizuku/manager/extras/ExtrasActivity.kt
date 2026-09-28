package moe.shizuku.manager.extras

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.shizuku.manager.R
import moe.shizuku.manager.app.AppActivity
import moe.shizuku.manager.module.ModulesActivity
import moe.shizuku.manager.monitor.MonitorSettings
import moe.shizuku.manager.monitor.ServerLogActivity
import moe.shizuku.manager.settings.LabFeaturesActivity
import moe.shizuku.manager.shell.ShellTutorialActivity
import moe.shizuku.manager.shizutest.ShizuTestActivity
import moe.shizuku.manager.ui.compose.ExpressiveCard
import moe.shizuku.manager.ui.compose.ExpressiveSwitch
import moe.shizuku.manager.ui.compose.MainTab
import moe.shizuku.manager.ui.compose.MainTabBar
import moe.shizuku.manager.ui.compose.ShizukuExpressiveTheme
import moe.shizuku.manager.ui.compose.openMainTab
import moe.shizuku.manager.utils.EnvironmentUtils
import rikka.shizuku.Shizuku

private data class ServerInfoSnapshot(
    val running: Boolean,
    val uptime: String,
    val adbPort: String
)

class ExtrasActivity : AppActivity() {

    private var refreshTick by mutableStateOf(0)

    override fun onResume() {
        super.onResume()
        refreshTick++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            var serverInfo by remember { mutableStateOf(ServerInfoSnapshot(false, "—", "—")) }
            var autoRestart by remember { mutableStateOf(MonitorSettings.isAutoRestartEnabled()) }

            LaunchedEffect(refreshTick) {
                autoRestart = MonitorSettings.isAutoRestartEnabled()
                serverInfo = withContext(Dispatchers.IO) { collectServerInfo(this@ExtrasActivity) }
            }

            ShizukuExpressiveTheme {
                Scaffold(
                    modifier = Modifier,
                    contentWindowInsets = WindowInsets(0.dp),
                    topBar = {
                        TopAppBar(
                            title = { Text(text = stringResource(R.string.extras_title)) },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                            )
                        )
                    },
                    bottomBar = {
                        MainTabBar(selected = MainTab.EXTRAS, onSelect = ::openMainTab)
                    }
                ) { innerPadding ->
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            ExpressiveCard(
                                icon = if (serverInfo.running) {
                                    R.drawable.ic_server_ok_24dp
                                } else {
                                    R.drawable.ic_server_error_24dp
                                },
                                title = stringResource(R.string.extras_server_info_title),
                                body = "Uptime: ${serverInfo.uptime}\nADB port: ${serverInfo.adbPort}"
                            )
                        }
                        item {
                            ExpressiveCard(
                                icon = R.drawable.ic_server_restart,
                                title = stringResource(R.string.monitor_auto_restart_title),
                                body = stringResource(R.string.monitor_auto_restart_summary),
                                content = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        ExpressiveSwitch(
                                            checked = autoRestart,
                                            onCheckedChange = {
                                                MonitorSettings.setAutoRestartEnabled(it)
                                                autoRestart = it
                                            }
                                        )
                                    }
                                }
                            )
                        }
                        item {
                            ExpressiveCard(
                                icon = R.drawable.ic_adb_24dp,
                                title = stringResource(R.string.modules_title),
                                body = stringResource(R.string.modules_settings_summary),
                                onClick = {
                                    startActivity(Intent(this@ExtrasActivity, ModulesActivity::class.java))
                                }
                            )
                        }
                        item {
                            ExpressiveCard(
                                icon = R.drawable.ic_outline_info_24,
                                title = stringResource(R.string.log_title),
                                body = stringResource(R.string.log_summary),
                                onClick = {
                                    startActivity(Intent(this@ExtrasActivity, ServerLogActivity::class.java))
                                }
                            )
                        }
                        item {
                            ExpressiveCard(
                                icon = R.drawable.ic_baseline_link_24,
                                title = stringResource(R.string.lab_features_title),
                                body = stringResource(R.string.lab_features_summary),
                                onClick = {
                                    startActivity(Intent(this@ExtrasActivity, LabFeaturesActivity::class.java))
                                }
                            )
                        }
                        item {
                            ExpressiveCard(
                                icon = R.drawable.ic_terminal_24,
                                title = stringResource(R.string.home_terminal_title),
                                body = stringResource(R.string.home_terminal_description),
                                onClick = {
                                    startActivity(Intent(this@ExtrasActivity, ShellTutorialActivity::class.java))
                                }
                            )
                        }
                        item {
                            ExpressiveCard(
                                icon = R.drawable.ic_server_ok_24dp,
                                title = stringResource(R.string.extras_shizutest_title),
                                body = stringResource(R.string.extras_shizutest_summary),
                                onClick = {
                                    startActivity(Intent(this@ExtrasActivity, ShizuTestActivity::class.java))
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    companion object {

        private fun collectServerInfo(context: android.content.Context): ServerInfoSnapshot {
            val running = try {
                Shizuku.pingBinder()
            } catch (_: Throwable) {
                false
            }
            val startedAt = MonitorSettings.getServerStartedAt()
            val uptime = if (running && startedAt > 0L) {
                formatUptime(System.currentTimeMillis() - startedAt)
            } else {
                "—"
            }
            return ServerInfoSnapshot(running, uptime, resolveAdbPort(context))
        }

        /**
         * Wireless debugging does not publish its port in a system property,
         * so resolve it from several sources: the live property, the elevated
         * shell, the endpoint used at connect time, and finally the wireless
         * debugging switch state.
         */
        private fun resolveAdbPort(context: android.content.Context): String {
            try {
                val live = EnvironmentUtils.getAdbTcpPort()
                if (live > 0) return live.toString()
            } catch (_: Throwable) {
            }
            try {
                val elevated = moe.shizuku.manager.monitor.XStatusCollector
                    .runCommand("getprop service.adb.tcp.port; getprop persist.adb.tcp.port")
                    ?.lineSequence()
                    ?.mapNotNull { it.trim().toIntOrNull() }
                    ?.firstOrNull { it > 0 }
                if (elevated != null) return elevated.toString()
            } catch (_: Throwable) {
            }
            val remembered = MonitorSettings.getLastAdbPort()
            if (remembered > 0) return remembered.toString()
            return try {
                val wireless = android.provider.Settings.Global.getInt(
                    context.contentResolver, "adb_wifi_enabled", 0
                ) == 1
                if (wireless) "Wireless" else "—"
            } catch (_: Throwable) {
                "—"
            }
        }

        private fun formatUptime(elapsedMs: Long): String {
            if (elapsedMs <= 0L) return "—"
            val seconds = elapsedMs / 1000L
            val minutes = seconds / 60L
            val hours = minutes / 60L
            val days = hours / 24L
            return when {
                days > 0L -> "${days}d ${hours % 24}h"
                hours > 0L -> "${hours}h ${minutes % 60}m"
                minutes > 0L -> "${minutes}m ${seconds % 60}s"
                else -> "${seconds}s"
            }
        }
    }
}
