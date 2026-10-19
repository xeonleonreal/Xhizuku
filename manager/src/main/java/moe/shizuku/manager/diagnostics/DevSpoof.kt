package moe.shizuku.manager.diagnostics

import moe.shizuku.manager.ShizukuSettings

object DevSpoof {

    private const val KEY_SPOOF_ONLINE = "dev_spoof_online"
    private const val KEY_SPOOF_OUTCOMES = "dev_spoof_outcomes"

    fun spoofOnline(): Boolean {
        return ShizukuSettings.getPreferences().getBoolean(KEY_SPOOF_ONLINE, false)
    }

    fun setSpoofOnline(value: Boolean) {
        ShizukuSettings.getPreferences().edit().putBoolean(KEY_SPOOF_ONLINE, value).apply()
    }

    fun spoofOutcomes(): Boolean {
        return ShizukuSettings.getPreferences().getBoolean(KEY_SPOOF_OUTCOMES, false)
    }

    fun setSpoofOutcomes(value: Boolean) {
        ShizukuSettings.getPreferences().edit().putBoolean(KEY_SPOOF_OUTCOMES, value).apply()
    }
}
