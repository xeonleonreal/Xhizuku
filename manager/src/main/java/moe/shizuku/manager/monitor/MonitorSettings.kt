package moe.shizuku.manager.monitor

import moe.shizuku.manager.ShizukuSettings

object MonitorSettings {

    private const val KEY_SERVER_NOTIFICATION = "monitor_server_notification"
    private const val KEY_XSTATUS = "monitor_xstatus"

    fun isServerNotificationEnabled(): Boolean {
        return ShizukuSettings.getPreferences().getBoolean(KEY_SERVER_NOTIFICATION, true)
    }

    fun setServerNotificationEnabled(enabled: Boolean) {
        ShizukuSettings.getPreferences().edit().putBoolean(KEY_SERVER_NOTIFICATION, enabled).apply()
    }

    fun isXStatusEnabled(): Boolean {
        return ShizukuSettings.getPreferences().getBoolean(KEY_XSTATUS, false)
    }

    fun setXStatusEnabled(enabled: Boolean) {
        ShizukuSettings.getPreferences().edit().putBoolean(KEY_XSTATUS, enabled).apply()
    }

    fun isAnyEnabled(): Boolean {
        return isServerNotificationEnabled() || isXStatusEnabled()
    }
}
