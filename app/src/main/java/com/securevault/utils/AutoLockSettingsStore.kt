package com.securevault.utils

import android.content.Context

/**
 * Owns the persisted Container Auto-Lock duration.
 *
 * All consumers must read and write this setting through this store.
 */
object AutoLockSettingsStore {
    private const val PREFS_NAME = "vault_prefs"
    private const val KEY_AUTO_LOCK_DURATION = "container_auto_lock_ms"

    const val DEFAULT_DURATION_MILLIS = 300_000L

    private val supportedDurations = setOf(
        0L,
        60_000L,
        300_000L,
        600_000L
    )

    fun getDurationMillis(context: Context): Long {
        val prefs = context.applicationContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

        val storedDuration = prefs.getLong(
            KEY_AUTO_LOCK_DURATION,
            DEFAULT_DURATION_MILLIS
        )

        if (storedDuration in supportedDurations) {
            return storedDuration
        }

        // Normalize unsupported legacy or corrupted values to the existing default.
        prefs.edit()
            .putLong(KEY_AUTO_LOCK_DURATION, DEFAULT_DURATION_MILLIS)
            .apply()

        return DEFAULT_DURATION_MILLIS
    }

    fun setDurationMillis(context: Context, durationMillis: Long): Boolean {
        if (durationMillis !in supportedDurations) {
            return false
        }

        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_AUTO_LOCK_DURATION, durationMillis)
            .apply()

        return true
    }
}
