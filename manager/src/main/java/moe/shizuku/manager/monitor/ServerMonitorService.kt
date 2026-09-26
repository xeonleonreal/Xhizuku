package moe.shizuku.manager.monitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import moe.shizuku.manager.MainActivity
import moe.shizuku.manager.R
import rikka.shizuku.Shizuku

/**
 * Ongoing monitor for the Xhizuku server plus the XStatus device-stats readout.
 *
 * - Posts an ongoing status notification (running version/mode, or stopped).
 * - Posts a one-shot alert when a previously-running server dies.
 * - Posts an ongoing XStatus notification (CPU/GPU/RAM/swap) while enabled.
 *
 * Enable/disable via [MonitorSettings]; started from the UI, boot receiver
 * and [moe.shizuku.manager.home.HomeActivity.onResume].
 */
class ServerMonitorService : Service() {

    companion object {
        const val CHANNEL_STATUS = "x_server_status"
        const val CHANNEL_XSTATUS = "x_xstatus"

        private const val ID_STATUS = 100
        private const val ID_XSTATUS = 101
        private const val ID_DIED_ALERT = 102
        private const val ID_FALLBACK = 103

        const val ACTION_STOP_SERVER = "moe.shizuku.manager.monitor.STOP_SERVER"

        private const val XSTATUS_INTERVAL_MS = 3000L

        fun startIfEnabled(context: Context) {
            if (!MonitorSettings.isAnyEnabled()) return
            try {
                ContextCompat.startForegroundService(context, Intent(context, ServerMonitorService::class.java))
            } catch (_: Throwable) {
            }
        }

        fun refresh(context: Context) {
            startIfEnabled(context)
        }

        fun stopIfDisabled(context: Context) {
            if (!MonitorSettings.isAnyEnabled()) {
                try {
                    context.stopService(Intent(context, ServerMonitorService::class.java))
                } catch (_: Throwable) {
                }
            } else {
                refresh(context)
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var xStatusJob: Job? = null

    @Volatile
    private var lastKnownRunning: Boolean? = null

    @Volatile
    private var lastPostedStatusKey: String? = null

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        onServerStateChanged(running = true)
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        onServerStateChanged(running = false)
    }

    override fun onCreate() {
        super.onCreate()
        createChannels()
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        lastKnownRunning = null
        promoteToForeground()
        updateStatusNotification()
        updateXStatusLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVER) {
            try {
                Shizuku.exit()
            } catch (_: Throwable) {
            }
            updateStatusNotification()
            return START_STICKY
        }
        if (!MonitorSettings.isAnyEnabled()) {
            stopSelf()
            return START_NOT_STICKY
        }
        promoteToForeground()
        updateStatusNotification()
        updateXStatusLoop()
        return START_STICKY
    }

    override fun onDestroy() {
        try {
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
        } catch (_: Throwable) {
        }
        xStatusJob?.cancel()
        xStatusJob = null
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notificationManager(): NotificationManager {
        return getSystemService(NotificationManager::class.java)
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = notificationManager()
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_STATUS,
                getString(R.string.notification_channel_server_status),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                setSound(null, null)
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_XSTATUS,
                getString(R.string.notification_channel_xstatus),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setSound(null, null)
                setShowBadge(false)
            }
        )
    }

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private data class ServerInfo(val running: Boolean, val version: String, val root: Boolean)

    private fun readServerInfo(): ServerInfo {
        return try {
            if (!Shizuku.pingBinder()) return ServerInfo(false, "", false)
            val version = try {
                "v${Shizuku.getVersion()}.${Shizuku.getServerPatchVersion()}"
            } catch (_: Throwable) {
                ""
            }
            val root = try {
                Shizuku.getUid() == 0
            } catch (_: Throwable) {
                false
            }
            ServerInfo(true, version, root)
        } catch (_: Throwable) {
            ServerInfo(false, "", false)
        }
    }

    @Suppress("DEPRECATION")
    private fun channelBuilder(channel: String, legacyPriority: Int): Notification.Builder {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channel)
        } else {
            Notification.Builder(this).apply { setPriority(legacyPriority) }
        }
    }

    private fun buildStatusNotification(info: ServerInfo): Notification {
        val builder = channelBuilder(CHANNEL_STATUS, Notification.PRIORITY_DEFAULT)
            .setColor(getColor(R.color.notification))
            .setSmallIcon(R.drawable.ic_system_icon)
            .setContentIntent(openAppIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
        if (info.running) {
            val mode = getString(
                if (info.root) R.string.monitor_running_mode_root else R.string.monitor_running_mode_adb
            )
            builder.setContentTitle(getString(R.string.monitor_running_title))
            builder.setContentText(getString(R.string.monitor_running_text, info.version, mode))
            val stopIntent = PendingIntent.getService(
                this, 1,
                Intent(this, ServerMonitorService::class.java).setAction(ACTION_STOP_SERVER),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                Notification.Action.Builder(null, getString(R.string.monitor_stop_server), stopIntent).build()
            )
        } else {
            builder.setContentTitle(getString(R.string.monitor_stopped_title))
            builder.setContentText(getString(R.string.monitor_stopped_text))
        }
        return builder.build()
    }

    private fun startForegroundCompat(id: Int, notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(id, notification)
            }
        } catch (_: Throwable) {
        }
    }

    private fun buildFallbackNotification(): Notification {
        return channelBuilder(CHANNEL_XSTATUS, Notification.PRIORITY_LOW)
            .setColor(getColor(R.color.notification))
            .setSmallIcon(R.drawable.ic_system_icon)
            .setContentTitle(getString(R.string.monitor_xstatus_title))
            .setContentText(getString(R.string.monitor_stopped_text))
            .setContentIntent(openAppIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun statusKey(info: ServerInfo): String {
        return (if (info.running) "1" else "0") + "|" + info.version + "|" + info.root
    }

    private fun promoteToForeground() {
        // The server status notification is always the foreground (main) one.
        // XStatus stats are always a secondary plain notification, never the
        // foreground notification.
        if (MonitorSettings.isServerNotificationEnabled()) {
            notificationManager().cancel(ID_FALLBACK)
            val info = readServerInfo()
            if (lastKnownRunning == null) {
                lastKnownRunning = info.running
            }
            lastPostedStatusKey = statusKey(info)
            startForegroundCompat(ID_STATUS, buildStatusNotification(info))
        } else {
            notificationManager().cancel(ID_STATUS)
            notificationManager().cancel(ID_DIED_ALERT)
            lastPostedStatusKey = null
            startForegroundCompat(ID_FALLBACK, buildFallbackNotification())
        }
    }

    private fun onServerStateChanged(running: Boolean) {
        val previous = lastKnownRunning
        lastKnownRunning = running
        updateStatusNotification()
        if (previous == true && !running) {
            postDiedAlert()
        }
        if (running) {
            notificationManager().cancel(ID_DIED_ALERT)
        }
    }

    private fun updateStatusNotification() {
        if (!MonitorSettings.isServerNotificationEnabled()) {
            lastPostedStatusKey = null
            notificationManager().cancel(ID_STATUS)
            notificationManager().cancel(ID_DIED_ALERT)
            return
        }
        notificationManager().cancel(ID_FALLBACK)
        val info = readServerInfo()
        if (lastKnownRunning == null) {
            lastKnownRunning = info.running
        }
        // Only touch the notification when the server state actually changed
        // (running/stopped, version or root/adb mode).
        val key = statusKey(info)
        if (key == lastPostedStatusKey) return
        lastPostedStatusKey = key
        notificationManager().notify(ID_STATUS, buildStatusNotification(info))
    }

    private fun postDiedAlert() {
        if (!MonitorSettings.isServerNotificationEnabled()) return
        val notification = channelBuilder(CHANNEL_STATUS, Notification.PRIORITY_HIGH)
            .setColor(getColor(R.color.notification))
            .setSmallIcon(R.drawable.ic_system_icon)
            .setContentTitle(getString(R.string.monitor_died_title))
            .setContentText(getString(R.string.monitor_died_text))
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .build()
        try {
            notificationManager().notify(ID_DIED_ALERT, notification)
        } catch (_: Throwable) {
        }
    }

    private fun buildXStatusNotification(snapshot: XStatusCollector.Snapshot): Notification {
        val bigText = buildString {
            appendLine("CPU ${XStatusCollector.formatPercent(snapshot.cpuPercent)} (${snapshot.cpuCores} cores)")
            appendLine("GPU ${XStatusCollector.formatPercent(snapshot.gpuPercent)}")
            appendLine("RAM ${snapshot.memUsedMb}/${snapshot.memTotalMb} MB")
            appendLine("Swap ${snapshot.swapUsedMb}/${snapshot.swapTotalMb} MB")
            if (snapshot.loadAvg.isNotBlank()) {
                append("Load ${snapshot.loadAvg}")
            }
        }.trim()
        return channelBuilder(CHANNEL_XSTATUS, Notification.PRIORITY_MIN)
            .setColor(getColor(R.color.notification))
            .setSmallIcon(R.drawable.ic_system_icon)
            .setContentTitle("XStatus · CPU ${XStatusCollector.formatPercent(snapshot.cpuPercent)}")
            .setContentText(
                "RAM ${snapshot.memUsedMb}/${snapshot.memTotalMb} MB · " +
                    "Swap ${snapshot.swapUsedMb}/${snapshot.swapTotalMb} MB"
            )
            .setStyle(Notification.BigTextStyle().bigText(bigText))
            .setContentIntent(openAppIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateXStatusLoop() {
        xStatusJob?.cancel()
        xStatusJob = null
        notificationManager().cancel(ID_XSTATUS)
        if (!MonitorSettings.isXStatusEnabled()) return
        // Prime the CPU sampler so the first visible value is real.
        try {
            XStatusCollector.collect()
        } catch (_: Throwable) {
        }
        xStatusJob = scope.launch {
            while (isActive) {
                if (!MonitorSettings.isXStatusEnabled()) break
                try {
                    val snapshot = XStatusCollector.collect()
                    notificationManager().notify(ID_XSTATUS, buildXStatusNotification(snapshot))
                } catch (_: Throwable) {
                }
                delay(XSTATUS_INTERVAL_MS)
            }
        }
    }
}
