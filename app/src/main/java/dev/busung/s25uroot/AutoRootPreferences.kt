package dev.busung.s25uroot

import android.content.Context

object AutoRootPreferences {
    private const val PREFS = "auto_root"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_AUTO_MODE = "auto_mode"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }

    fun isAutoMode(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_MODE, false)

    fun setAutoMode(context: Context, auto: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_MODE, auto)
            .apply()
    }

    fun setLastAttemptTime(context: Context, time: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong("last_attempt", time)
            .apply()
    }

    fun getLastAttemptTime(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong("last_attempt", 0L)

    fun incrementRetryCount(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putInt("retry_count", prefs.getInt("retry_count", 0) + 1).apply()
    }

    fun resetRetryCount(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt("retry_count", 0)
            .apply()
    }

    fun getRetryCount(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt("retry_count", 0)
}
