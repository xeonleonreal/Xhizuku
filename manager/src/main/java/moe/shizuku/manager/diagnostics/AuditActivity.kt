package moe.shizuku.manager.diagnostics

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.shizuku.manager.R
import moe.shizuku.manager.app.AppActivity
import moe.shizuku.manager.ui.compose.ExpressiveCard
import moe.shizuku.manager.ui.compose.ShizukuExpressiveTheme
import moe.shizuku.manager.ui.compose.ShizukuLazyScaffold
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AuditActivity : AppActivity() {

    private var refreshTick by mutableStateOf(0)

    override fun onResume() {
        super.onResume()
        refreshTick++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            var usage by mutableStateOf<List<AuditApi.UsageEntry>>(emptyList())
            var log by mutableStateOf<List<AuditApi.AuditEntry>>(emptyList())
            var serverRunning by mutableStateOf(true)
            var outdated by mutableStateOf(false)

            LaunchedEffect(refreshTick) {
                val running = withContext(Dispatchers.IO) {
                    try {
                        rikka.shizuku.Shizuku.pingBinder()
                    } catch (_: Throwable) {
                        false
                    }
                }
                serverRunning = running
                if (!running) return@LaunchedEffect
                val result = withContext(Dispatchers.IO) {
                    try {
                        AuditApi.getUsageStats() to AuditApi.getAuditLog()
                    } catch (_: Throwable) {
                        null
                    }
                }
                if (result == null) {
                    outdated = true
                    usage = emptyList()
                    log = emptyList()
                } else {
                    outdated = false
                    usage = result.first
                    log = result.second
                }
            }

            ShizukuExpressiveTheme {
                ShizukuLazyScaffold(
                    title = stringResource(R.string.audit_title),
                    onNavigateUp = { finish() }
                ) {
                    item {
                        ExpressiveCard(
                            icon = R.drawable.ic_outline_info_24,
                            title = stringResource(R.string.audit_usage_title),
                            body = usage.take(10).joinToString("\n") { entry ->
                                val name = entry.packageName.ifBlank { resolveName(entry.uid) }
                                "$name (uid ${entry.uid}) — ${entry.count}"
                            }.ifBlank { stringResource(R.string.audit_empty) }
                        )
                    }
                    if (!serverRunning) {
                        item {
                            ExpressiveCard(
                                icon = R.drawable.ic_warning_24,
                                title = stringResource(R.string.monitor_stopped_title),
                                body = stringResource(R.string.monitor_stopped_text)
                            )
                        }
                    }
                    if (serverRunning && outdated) {
                        item {
                            ExpressiveCard(
                                icon = R.drawable.ic_warning_24,
                                title = stringResource(R.string.audit_title),
                                body = stringResource(R.string.audit_outdated)
                            )
                        }
                    }
                    items(log) { entry ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatTime(entry.time),
                                style = MaterialTheme.typography.labelMedium,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.packageName.ifBlank { "uid ${entry.uid}" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "uid ${entry.uid} · ${entry.decision}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    if (serverRunning && !outdated && log.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.audit_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    private fun resolveName(uid: Int): String {
        return try {
            packageManager.getNameForUid(uid) ?: uid.toString()
        } catch (_: Throwable) {
            uid.toString()
        }
    }

    companion object {
        private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

        private fun formatTime(millis: Long): String {
            if (millis <= 0L) return "--:--:--"
            return try {
                timeFormat.format(Date(millis))
            } catch (_: Throwable) {
                "--:--:--"
            }
        }
    }
}
