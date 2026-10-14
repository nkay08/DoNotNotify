package com.donotnotify.donotnotify

import android.os.Handler
import android.os.Looper

/**
 * In-memory delayed-cancel scheduler for the auto-dismiss feature. Timers run on the main
 * looper; a keyed map lets the service cancel a pending dismissal when the notification
 * disappears earlier (user swipe, update, other rule).
 *
 * Limitation: timers live in the listener process — a process death drops pending
 * dismissals and the notification then stays. Acceptable for short seconds/minutes delays;
 * a persistent AlarmManager variant would be the follow-up.
 */
class DismissScheduler(looper: Looper = Looper.getMainLooper()) {

    private val handler = Handler(looper)
    private val pending = mutableMapOf<String, Runnable>()

    /** Schedule [action] after [delayMs], replacing any pending entry for [key]. */
    fun schedule(key: String, delayMs: Long, action: () -> Unit) {
        cancel(key)
        val runnable = Runnable {
            pending.remove(key)
            action()
        }
        pending[key] = runnable
        handler.postDelayed(runnable, delayMs)
    }

    /** Drop the pending timer for [key], if any. */
    fun cancel(key: String) {
        pending.remove(key)?.let { handler.removeCallbacks(it) }
    }
}
