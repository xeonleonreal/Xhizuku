package moe.shizuku.manager.monitor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.shizuku.manager.R
import moe.shizuku.manager.app.AppActivity
import moe.shizuku.manager.connector.ShizukuConnectorProvider
import moe.shizuku.manager.ui.compose.ExpressiveButtonSpec
import moe.shizuku.manager.ui.compose.ExpressiveButtons
import moe.shizuku.manager.ui.compose.ExpressiveCard
import moe.shizuku.manager.ui.compose.MonospaceLog
import moe.shizuku.manager.ui.compose.ShizukuExpressiveTheme
import moe.shizuku.manager.ui.compose.ShizukuLazyScaffold
import rikka.shizuku.Shizuku
import java.util.concurrent.TimeUnit

private enum class LogSource {
    APP, SERVER, ACTIVATOR
}

class ServerLogActivity : AppActivity() {

    private var logText by mutableStateOf("")
    private var loading by mutableStateOf(true)
    private var needsAuth by mutableStateOf(false)
    private var source by mutableStateOf(LogSource.SERVER)
    private var loadJob: kotlinx.coroutines.Job? = null

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
                    item {
                        ExpressiveButtons(
                            listOf(
                                ExpressiveButtonSpec(
                                    label = R.string.log_source_app,
                                    icon = R.drawable.ic_outline_info_24,
                                    primary = source == LogSource.APP,
                                    onClick = { source = LogSource.APP; loadLog() }
                                ),
                                ExpressiveButtonSpec(
                                    label = R.string.log_source_server,
                                    icon = R.drawable.ic_server_ok_24dp,
                                    primary = source == LogSource.SERVER,
                                    onClick = { source = LogSource.SERVER; loadLog() }
                                ),
                                ExpressiveButtonSpec(
                                    label = R.string.log_source_activator,
                                    icon = R.drawable.ic_baseline_link_24,
                                    primary = source == LogSource.ACTIVATOR,
                                    onClick = { source = LogSource.ACTIVATOR; loadLog() }
                                )
                            )
                        )
                    }
                    if (needsAuth && source == LogSource.SERVER) {
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
        loadJob?.cancel()
        loading = true
        logText = ""
        needsAuth = false
        val current = source
        loadJob = lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                when (current) {
                    LogSource.APP -> readLocalLogcat()
                    LogSource.SERVER -> collectServerLog()
                    LogSource.ACTIVATOR -> collectActivatorLog()
                }
            }
            if (current != source) return@launch
            if (result == AUTH_REQUIRED) {
                needsAuth = true
                logText = ""
            } else {
                logText = result
            }
            loading = false
        }
    }

    private fun readLocalLogcat(): String {
        return try {
            val process = ProcessBuilder("logcat", "-d", "-t", "1000", "-v", "time", "--pid=" + android.os.Process.myPid())
                .redirectErrorStream(true)
                .start()
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return ""
            }
            process.inputStream.bufferedReader().readText().take(60_000)
        } catch (_: Throwable) {
            ""
        }
    }

    private fun collectServerLog(): String {
        if (!Shizuku.pingBinder()) {
            return readLocalLogcat().ifBlank { getString(R.string.log_not_running) }
        }
        try {
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                return AUTH_REQUIRED
            }
        } catch (_: Throwable) {
            return AUTH_REQUIRED
        }
        var text = runServerLogcatCommand("logcat -d -t 1500 -v time --pid=\$(pidof shizuku_server 2>/dev/null | cut -d' ' -f1)")
        if (text.isBlank()) {
            text = runServerLogcatCommand(
                "logcat -d -t 1500 -v time -s Service:V ClientRecord:V UserServiceRecord:V " +
                    "ConfigPackageEntry:V ConfigManager:V UserServiceManager:V RemoteProcessHolder:V " +
                    "BinderSender:V NightDog:V shizuku_server:V shizuku_starter:V"
            )
        }
        if (text.isBlank()) {
            text = runServerLogcatCommand("logcat -d -t 1500 -v time")
        }
        return text
    }

    private fun runServerLogcatCommand(command: String): String {
        val result = XStatusCollector.exec(command)
        val text = result.stdout.trim()
        if (text.isNotBlank()) {
            return text.take(60_000)
        }
        return result.stderr.trim().take(60_000)
    }

    private fun collectActivatorLog(): String {
        return ShizukuConnectorProvider.getActivatorLog()
            .take(200)
            .mapNotNull { line ->
                val parts = line.split("|")
                if (parts.size != 2) return@mapNotNull null
                val time = parts[0].toLongOrNull()?.let { formatTime(it) } ?: "??:??:??"
                "$time ${parts[1]}"
            }
            .joinToString("\n")
    }

    companion object {
        private const val AUTH_REQUIRED = "\u0000AUTH_REQUIRED\u0000"

        private val timeFormat = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())

        private fun formatTime(millis: Long): String {
            return try {
                timeFormat.format(java.util.Date(millis))
            } catch (_: Throwable) {
                "??:??:??"
            }
        }
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
