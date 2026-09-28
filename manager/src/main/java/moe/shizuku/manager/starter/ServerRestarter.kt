package moe.shizuku.manager.starter

import android.Manifest.permission.WRITE_SECURE_SETTINGS
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.annotation.RequiresApi
import com.topjohnwu.superuser.Shell
import moe.shizuku.manager.AppConstants
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.ShizukuSettings.LaunchMethod
import moe.shizuku.manager.adb.AdbClient
import moe.shizuku.manager.adb.AdbKey
import moe.shizuku.manager.adb.AdbMdns
import moe.shizuku.manager.adb.PreferenceAdbKeyStore
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Starts the Xhizuku server using the last known launch method.
 * Used by the boot receiver and the auto-restart monitor.
 * Blocking calls — never invoke on the main thread.
 */
object ServerRestarter {

    fun restart(context: Context): Boolean {
        return when (ShizukuSettings.getLastLaunchMode()) {
            LaunchMethod.ROOT -> restartRoot()
            LaunchMethod.ADB -> restartAdb(context)
            else -> {
                Log.w(AppConstants.TAG, "No known launch mode, cannot restart")
                false
            }
        }
    }

    fun restartRoot(): Boolean {
        if (!Shell.getShell().isRoot) {
            Shell.getCachedShell()?.close()
            return false
        }
        return try {
            Shell.cmd(Starter.internalCommand).exec().isSuccess
        } catch (e: Throwable) {
            Log.w(AppConstants.TAG, "Root restart failed", e)
            false
        }
    }

    fun restartAdb(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        if (context.checkSelfPermission(WRITE_SECURE_SETTINGS) != PackageManager.PERMISSION_GRANTED) return false
        return try {
            adbStartInternal(context)
            true
        } catch (e: Throwable) {
            Log.w(AppConstants.TAG, "ADB restart failed", e)
            false
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun adbStartInternal(context: Context) {
        val cr = context.contentResolver
        Settings.Global.putInt(cr, "adb_wifi_enabled", 1)
        Settings.Global.putInt(cr, Settings.Global.ADB_ENABLED, 1)
        Settings.Global.putLong(cr, "adb_allowed_connection_time", 0L)
        val latch = CountDownLatch(1)
        val adbMdns = AdbMdns(context, AdbMdns.TLS_CONNECT) { (host, port) ->
            if (port <= 0) return@AdbMdns
            try {
                val keystore = PreferenceAdbKeyStore(ShizukuSettings.getPreferences())
                val key = AdbKey(keystore, "shizuku")
                val client = AdbClient(host, port, key)
                client.connect()
                client.shellCommand(Starter.internalCommand, null)
                client.close()
            } catch (_: Exception) {
            }
            latch.countDown()
        }
        if (Settings.Global.getInt(cr, "adb_wifi_enabled", 0) == 1) {
            adbMdns.start()
            latch.await(3, TimeUnit.SECONDS)
            adbMdns.stop()
        }
    }
}
