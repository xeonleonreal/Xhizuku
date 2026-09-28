package moe.shizuku.manager.monitor

import moe.shizuku.manager.ShizukuSettings

object MonitorSettings {

    private const val KEY_SERVER_NOTIFICATION = "monitor_server_notification"
    private const val KEY_XSTATUS = "monitor_xstatus"
    private const val KEY_SERVER_STARTED_AT = "monitor_server_started_at"
    private const val KEY_AUTO_RESTART = "monitor_auto_restart"
    private const val KEY_LAST_MANUAL_STOP = "monitor_last_manual_stop"
    private const val KEY_LAST_ADB_HOST = "monitor_last_adb_host"
    private const val KEY_LAST_ADB_PORT = "monitor_last_adb_port"

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

    /** Epoch millis when the server binder was last seen, 0 if stopped/unknown. */
    fun getServerStartedAt(): Long {
        return ShizukuSettings.getPreferences().getLong(KEY_SERVER_STARTED_AT, 0L)
    }

    fun setServerStartedAt(value: Long) {
        ShizukuSettings.getPreferences().edit().putLong(KEY_SERVER_STARTED_AT, value).apply()
    }

    fun isAutoRestartEnabled(): Boolean {
        return ShizukuSettings.getPreferences().getBoolean(KEY_AUTO_RESTART, false)
    }

    fun setAutoRestartEnabled(enabled: Boolean) {
        ShizukuSettings.getPreferences().edit().putBoolean(KEY_AUTO_RESTART, enabled).apply()
    }

    fun noteManualStop() {
        ShizukuSettings.getPreferences().edit().putLong(KEY_LAST_MANUAL_STOP, System.currentTimeMillis()).apply()
    }

    fun isManualStopRecent(): Boolean {
        val at = ShizukuSettings.getPreferences().getLong(KEY_LAST_MANUAL_STOP, 0L)
        return System.currentTimeMillis() - at < 60_000L
    }

    fun setLastAdb(host: String, port: Int) {
        ShizukuSettings.getPreferences().edit()
            .putString(KEY_LAST_ADB_HOST, host)
            .putInt(KEY_LAST_ADB_PORT, port)
            .apply()
    }

    fun clearLastAdb() {
        ShizukuSettings.getPreferences().edit()
            .remove(KEY_LAST_ADB_HOST)
            .remove(KEY_LAST_ADB_PORT)
            .apply()
    }

    fun getLastAdbPort(): Int {
        return ShizukuSettings.getPreferences().getInt(KEY_LAST_ADB_PORT, -1)
    }
}
