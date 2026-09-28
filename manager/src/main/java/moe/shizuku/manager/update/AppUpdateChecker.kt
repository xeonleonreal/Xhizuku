package moe.shizuku.manager.update

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.shizuku.manager.BuildConfig
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Basic in-app updater: checks the latest GitHub release once a day and posts
 * a notification the user can follow or dismiss.
 */
object AppUpdateChecker {

    private const val RELEASES_LATEST_URL = "https://api.github.com/repos/xeonleonreal/Xhizuku/releases/latest"
    private const val KEY_LAST_CHECK = "app_update_last_check"
    private const val KEY_LAST_NOTIFIED_TAG = "app_update_last_notified_tag"
    private const val CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L

    const val CHANNEL_UPDATES = "x_app_updates"
    private const val ID_UPDATE = 200

    data class ReleaseInfo(val tag: String, val url: String)

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private fun currentBaseVersion(): String {
        return BuildConfig.VERSION_NAME.substringBefore(".r")
    }

    private fun versionTuple(version: String): List<Int> {
        return version.trim().trimStart('v', 'V')
            .split(".", "-", "_")
            .mapNotNull { it.toIntOrNull() }
    }

    fun isNewer(latestTag: String, current: String = currentBaseVersion()): Boolean {
        val latest = versionTuple(latestTag)
        val base = versionTuple(current)
        if (latest.isEmpty() || base.isEmpty()) return false
        val size = maxOf(latest.size, base.size)
        for (i in 0 until size) {
            val l = latest.getOrElse(i) { 0 }
            val c = base.getOrElse(i) { 0 }
            if (l != c) return l > c
        }
        return false
    }

    suspend fun fetchLatest(): ReleaseInfo? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(RELEASES_LATEST_URL)
                .header("Accept", "application/vnd.github+json")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val json = JSONObject(body)
                val tag = json.optString("tag_name", "")
                val url = json.optString("html_url", "")
                if (tag.isBlank() || url.isBlank()) return@withContext null
                ReleaseInfo(tag, url)
            }
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Checks at most once a day. Posts a notification for a tag that has not
     * been announced yet. Never throws.
     */
    suspend fun checkAsync(context: Context) {
        try {
            val prefs = ShizukuSettings.getPreferences()
            val now = System.currentTimeMillis()
            if (now - prefs.getLong(KEY_LAST_CHECK, 0L) < CHECK_INTERVAL_MS) return
            prefs.edit().putLong(KEY_LAST_CHECK, now).apply()

            val latest = fetchLatest() ?: return
            if (!isNewer(latest.tag)) return
            if (prefs.getString(KEY_LAST_NOTIFIED_TAG, null) == latest.tag) return
            prefs.edit().putString(KEY_LAST_NOTIFIED_TAG, latest.tag).apply()

            postUpdateNotification(context.applicationContext, latest)
        } catch (_: Throwable) {
        }
    }

    private fun postUpdateNotification(context: Context, release: ReleaseInfo) {
        try {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_UPDATES,
                        context.getString(R.string.notification_channel_app_updates),
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply {
                        setSound(null, null)
                        setShowBadge(false)
                    }
                )
            }
            val openIntent = PendingIntent.getActivity(
                context, 0,
                Intent(Intent.ACTION_VIEW, Uri.parse(release.url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(context, CHANNEL_UPDATES)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(context).apply { setPriority(Notification.PRIORITY_DEFAULT) }
            }
            val notification = builder
                .setColor(context.getColor(R.color.notification))
                .setSmallIcon(R.drawable.ic_system_icon)
                .setContentTitle(context.getString(R.string.update_available_title))
                .setContentText(context.getString(R.string.update_available_text, release.tag))
                .setContentIntent(openIntent)
                .setAutoCancel(true)
                .addAction(
                    Notification.Action.Builder(null, context.getString(R.string.update_open), openIntent).build()
                )
                .build()
            nm.notify(ID_UPDATE, notification)
        } catch (_: Throwable) {
        }
    }
}
