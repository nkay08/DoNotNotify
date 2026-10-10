package com.donotnotify.donotnotify

import android.content.Context

/**
 * Last-pass timestamps for the cooldown feature, one entry per package. SharedPreferences
 * keeps the state tiny and persistent: a service restart must not re-open the floodgates in
 * the middle of a burst. Entries are a single long each — pruning when rules are deleted is
 * deliberately left out (worst case is a few stale bytes).
 */
class CooldownStorage(context: Context) {

    private val prefs = context.getSharedPreferences("cooldown", Context.MODE_PRIVATE)

    private fun key(packageName: String) = "last_pass_$packageName"

    /** Epoch millis of the last pass for [packageName], or null when none was recorded. */
    fun lastPassAt(packageName: String): Long? {
        val value = prefs.getLong(key(packageName), -1L)
        return if (value >= 0) value else null
    }

    fun recordPass(packageName: String, now: Long) {
        prefs.edit().putLong(key(packageName), now).apply()
    }
}
