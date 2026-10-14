package com.donotnotify.donotnotify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.util.concurrent.atomic.AtomicInteger

/**
 * Re-posts proxied notifications under our own package, one notification per source
 * notification (no grouping — that is STACK's job).
 *
 * Purpose: watch-companion apps (e.g. Garmin Connect) forward notifications per app, not per
 * content. By canceling the source notification and re-posting a copy as DoNotNotify's own,
 * the user can disable the source app in the companion app and enable DoNotNotify there
 * instead — content-based filtering then actually controls what reaches the watch, with no
 * cancel race to lose.
 *
 * The order is post-then-cancel (the service cancels the source only when [post] returned
 * true), so a notification is never lost to a failed re-post.
 *
 * Like [StackedNotificationManager], planning is a pure function over an Android-free
 * [ProxyPoster] seam, so the invariants are unit-testable on the JVM.
 */
object ProxyNotificationManager {

    private const val TAG = "ProxyNotificationManager"

    /** The single shared channel all proxied notifications post on. */
    const val CHANNEL_ID = "proxy_notifications"

    /** ID range kept clear of the STACK registry (100_000) and HealthCheckWorker. */
    private val idCounter = AtomicInteger(500_000)
    internal fun nextId(): Int = idCounter.getAndIncrement()

    data class Entry(
        val title: String?,
        val text: String?,
        val contentIntent: PendingIntent?,
        val sourceVisibility: Int,
        val packageName: String,
        val appLabel: String,
        val silent: Boolean = false
    )

    /** Immutable plan produced by the pure [planPost]. No Android calls. */
    data class PostPlan(
        val notificationId: Int,
        val title: String,
        val visibility: Int,
        val redactPublic: Boolean
    )

    /** Normalize an unknown/out-of-range visibility to the conservative PRIVATE. */
    private fun normalizeVisibility(v: Int): Int = when (v) {
        Notification.VISIBILITY_PUBLIC,
        Notification.VISIBILITY_PRIVATE,
        Notification.VISIBILITY_SECRET -> v
        else -> Notification.VISIBILITY_PRIVATE
    }

    /** Pure: derive everything needed to post from the source entry. */
    fun planPost(entry: Entry, idAllocator: () -> Int = ::nextId): PostPlan {
        val visibility = normalizeVisibility(entry.sourceVisibility)
        return PostPlan(
            notificationId = idAllocator(),
            title = entry.title?.takeIf { it.isNotBlank() } ?: entry.appLabel,
            visibility = visibility,
            redactPublic = visibility != Notification.VISIBILITY_PUBLIC
        )
    }

    /**
     * Post a proxy copy through the seam. Returns true when the copy is posted, false when
     * the poster refused (notifications disabled) or threw — the caller then leaves the
     * source notification untouched.
     */
    fun post(poster: ProxyPoster, entry: Entry, largeIcon: Bitmap?): Boolean =
        post(poster, planPost(entry), entry, largeIcon)

    /**
     * Post with a pre-planned [PostPlan], so the caller knows the notification id in advance
     * (e.g. to schedule a later cancel). Returns true when the copy is posted.
     */
    fun post(poster: ProxyPoster, plan: PostPlan, entry: Entry, largeIcon: Bitmap?): Boolean {
        return try {
            poster.post(plan, entry, largeIcon)
        } catch (e: Exception) {
            Log.w(TAG, "Proxy post failed for ${entry.packageName}", e)
            false
        }
    }

    /** Android-facing side-effect seam; faked in tests. */
    interface ProxyPoster {
        /** Post the plan; return false when nothing was posted (e.g. notifications disabled). */
        fun post(plan: PostPlan, entry: Entry, largeIcon: Bitmap?): Boolean

        /** Cancel a previously posted proxy notification by its id. */
        fun cancel(id: Int)
    }

    class AndroidProxyPoster(private val context: Context) : ProxyPoster {

        private val nmc = NotificationManagerCompat.from(context)

        init {
            ensureChannel()
        }

        /** Idempotent creation of the shared proxy channel (API 26+). */
        private fun ensureChannel() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        context.getString(R.string.proxy_channel_name),
                        NotificationManager.IMPORTANCE_DEFAULT
                    )
                )
            }
        }

        override fun post(plan: PostPlan, entry: Entry, largeIcon: Bitmap?): Boolean {
            if (!nmc.areNotificationsEnabled()) {
                Log.w(TAG, "Notifications disabled; leaving source intact for ${entry.packageName}")
                return false
            }
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_stack)
                .setContentTitle(plan.title)
                .setContentText(entry.text)
                .setAutoCancel(true)
                .setVisibility(plan.visibility)
                .setContentIntent(entry.contentIntent ?: sourceLaunchIntent(entry.packageName, plan.notificationId))
            largeIcon?.let { builder.setLargeIcon(it) }
            if (entry.silent) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    // Channel-independent silence: no sound, no vibration, regardless of
                    // the channel's settings (some OEM builds do not honour the per-channel
                    // vibration toggle reliably).
                    builder.setSilent(true)
                } else {
                    // Pre-channels: clear every alerting default explicitly.
                    builder.setDefaults(0)
                        .setSound(null)
                        .setVibrate(longArrayOf())
                }
            }
            if (plan.redactPublic) {
                builder.setPublicVersion(redactedPublic(entry))
            }
            nmc.notify(plan.notificationId, builder.build())
            return true
        }

        override fun cancel(id: Int) {
            nmc.cancel(id)
        }

        /** Lock-screen-safe public version: the app label only, never the source title/body. */
        private fun redactedPublic(entry: Entry): Notification =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_stack)
                .setContentTitle(entry.appLabel)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .build()

        private fun sourceLaunchIntent(packageName: String, requestCode: Int): PendingIntent {
            val launch = context.packageManager
                .getLaunchIntentForPackage(packageName)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                ?: context.packageManager
                    .getLaunchIntentForPackage(context.packageName)
                    ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                ?: Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return PendingIntent.getActivity(
                context,
                requestCode,
                launch,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
    }
}
