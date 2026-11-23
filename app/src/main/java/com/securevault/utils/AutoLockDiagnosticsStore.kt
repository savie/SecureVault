package com.securevault.utils

import android.content.Context
import android.os.Build
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Internal diagnostic history for Container Auto-Lock.
 * Never records passwords, tokens, or file contents.
 */
object AutoLockDiagnosticsStore {
    private const val PREFS_NAME = "securevault_autolock_diagnostics"
    private const val KEY_ENTRIES = "entries"
    private const val MAX_ENTRIES = 150
    private const val MAX_DETAIL_LENGTH = 240

    private val lock = Any()

    fun record(context: Context, event: String, detail: String = "") {
        synchronized(lock) {
            try {
                val safeEvent = event
                    .replace(Regex("[^A-Za-z0-9_.-]"), "_")
                    .take(60)

                val safeDetail = detail
                    .replace('\n', ' ')
                    .replace('\r', ' ')
                    .take(MAX_DETAIL_LENGTH)

                val timestamp = SimpleDateFormat(
                    "yyyy-MM-dd HH:mm:ss.SSS",
                    Locale.US
                ).format(Date())

                val line = "$timestamp | $safeEvent | $safeDetail"

                val prefs = context.applicationContext.getSharedPreferences(
                    PREFS_NAME,
                    Context.MODE_PRIVATE
                )

                val previous = prefs.getString(KEY_ENTRIES, "")
                    .orEmpty()
                    .lineSequence()
                    .filter { it.isNotBlank() }
                    .toList()

                val updated = (previous + line)
                    .takeLast(MAX_ENTRIES)
                    .joinToString("\n")

                prefs.edit()
                    .putString(KEY_ENTRIES, updated)
                    .commit()
            } catch (_: Exception) {
                // Diagnostics must never interrupt Auto-Lock.
            }
        }
    }

    fun getEntryCount(context: Context): Int {
        val entries = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ENTRIES, "")
            .orEmpty()

        return entries.lineSequence().count { it.isNotBlank() }
    }

    fun getReport(context: Context): String {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

        val version = try {
            @Suppress("DEPRECATION")
            appContext.packageManager
                .getPackageInfo(appContext.packageName, 0)
                .versionName ?: "unknown"
        } catch (_: Exception) {
            "unknown"
        }

        val flavor = if (appContext.packageName.endsWith(".pro")) {
            "PRO"
        } else {
            "FREE"
        }

        val entries = prefs.getString(KEY_ENTRIES, "")
            .orEmpty()
            .ifBlank { "(No diagnostic events recorded)" }

        return buildString {
            appendLine("SECUREVAULT AUTO-LOCK DIAGNOSTICS")
            appendLine("Flavor: $flavor")
            appendLine("Package: ${appContext.packageName}")
            appendLine("Version: $version")
            appendLine("Android SDK: ${Build.VERSION.SDK_INT}")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Entries: ${getEntryCount(appContext)}")
            appendLine("----------------------------------------")
            append(entries)
        }
    }

    fun clear(context: Context): Boolean {
        return context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_ENTRIES)
            .commit()
    }
}
