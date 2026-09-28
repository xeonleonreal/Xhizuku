package moe.shizuku.manager.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import moe.shizuku.manager.AppConstants
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.ShizukuSettings.LaunchMethod
import moe.shizuku.manager.monitor.ServerMonitorService
import moe.shizuku.manager.starter.ServerRestarter
import moe.shizuku.manager.utils.UserHandleCompat
import rikka.shizuku.Shizuku

class BootCompleteReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_LOCKED_BOOT_COMPLETED != intent.action
            && Intent.ACTION_BOOT_COMPLETED != intent.action) {
            return
        }

        ServerMonitorService.startIfEnabled(context)

        if (UserHandleCompat.myUserId() > 0 || Shizuku.pingBinder()) return

        if (ShizukuSettings.getLastLaunchMode() == LaunchMethod.ROOT) {
            CoroutineScope(Dispatchers.IO).launch {
                ServerRestarter.restartRoot()
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            && ShizukuSettings.getLastLaunchMode() == LaunchMethod.ADB) {
            val pending = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    ServerRestarter.restartAdb(context)
                } finally {
                    pending.finish()
                }
            }
        } else {
            Log.w(AppConstants.TAG, "No support start on boot")
        }
    }
}
