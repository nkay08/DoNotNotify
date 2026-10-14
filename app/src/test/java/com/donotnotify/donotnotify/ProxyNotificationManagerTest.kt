package com.donotnotify.donotnotify

import android.app.Notification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure planning and seam behaviour for [ProxyNotificationManager]. */
class ProxyNotificationManagerTest {

    private class FakePoster(
        var result: Boolean = true,
        var throwOnPost: Boolean = false
    ) : ProxyNotificationManager.ProxyPoster {
        var lastPlan: ProxyNotificationManager.PostPlan? = null
        var lastEntry: ProxyNotificationManager.Entry? = null
        val canceledIds = mutableListOf<Int>()

        override fun post(
            plan: ProxyNotificationManager.PostPlan,
            entry: ProxyNotificationManager.Entry,
            largeIcon: android.graphics.Bitmap?
        ): Boolean {
            if (throwOnPost) throw IllegalStateException("post failed")
            lastPlan = plan
            lastEntry = entry
            return result
        }

        override fun cancel(id: Int) {
            canceledIds.add(id)
        }
    }

    private fun entry(
        title: String? = "Title",
        text: String? = "Body",
        visibility: Int = Notification.VISIBILITY_PRIVATE,
        packageName: String = "p",
        appLabel: String = "App"
    ) = ProxyNotificationManager.Entry(
        title = title,
        text = text,
        contentIntent = null,
        sourceVisibility = visibility,
        packageName = packageName,
        appLabel = appLabel
    )

    // ---- planPost ------------------------------------------------------------

    @Test
    fun `plan uses title when non-blank`() {
        val plan = ProxyNotificationManager.planPost(entry(title = "Real title"))
        assertEquals("Real title", plan.title)
    }

    @Test
    fun `plan falls back to app label when title missing`() {
        val plan = ProxyNotificationManager.planPost(entry(title = null))
        assertEquals("App", plan.title)
    }

    @Test
    fun `plan falls back to app label when title blank`() {
        val plan = ProxyNotificationManager.planPost(entry(title = "   "))
        assertEquals("App", plan.title)
    }

    @Test
    fun `plan keeps each valid visibility`() {
        assertEquals(
            Notification.VISIBILITY_PUBLIC,
            ProxyNotificationManager.planPost(entry(visibility = Notification.VISIBILITY_PUBLIC)).visibility
        )
        assertEquals(
            Notification.VISIBILITY_SECRET,
            ProxyNotificationManager.planPost(entry(visibility = Notification.VISIBILITY_SECRET)).visibility
        )
    }

    @Test
    fun `plan normalizes unknown visibility to private`() {
        val plan = ProxyNotificationManager.planPost(entry(visibility = 42))
        assertEquals(Notification.VISIBILITY_PRIVATE, plan.visibility)
    }

    @Test
    fun `plan redacts when not public`() {
        assertTrue(ProxyNotificationManager.planPost(entry(visibility = Notification.VISIBILITY_PRIVATE)).redactPublic)
        assertTrue(ProxyNotificationManager.planPost(entry(visibility = Notification.VISIBILITY_SECRET)).redactPublic)
        assertFalse(ProxyNotificationManager.planPost(entry(visibility = Notification.VISIBILITY_PUBLIC)).redactPublic)
    }

    @Test
    fun `plan allocates ids from the reserved range and increments`() {
        val seen = mutableSetOf<Int>()
        repeat(5) { seen.add(ProxyNotificationManager.planPost(entry()).notificationId) }
        assertEquals(5, seen.size)
        assertTrue(seen.all { it >= 500_000 })
    }

    // ---- post ------------------------------------------------------------------

    @Test
    fun `post returns true and forwards plan and entry`() {
        val poster = FakePoster()
        assertTrue(ProxyNotificationManager.post(poster, entry(), null))
        assertEquals("Title", poster.lastPlan?.title)
        assertEquals("Body", poster.lastEntry?.text)
    }

    @Test
    fun `post returns false when poster refuses`() {
        assertFalse(ProxyNotificationManager.post(FakePoster(result = false), entry(), null))
    }

    @Test
    fun `post returns false when poster throws`() {
        assertFalse(ProxyNotificationManager.post(FakePoster(throwOnPost = true), entry(), null))
    }

    @Test
    fun `post passes the source package through for the launch intent fallback`() {
        val poster = FakePoster()
        ProxyNotificationManager.post(poster, entry(packageName = "com.example"), null)
        assertEquals("com.example", poster.lastEntry?.packageName)
        assertNull(poster.lastEntry?.contentIntent)
    }

    @Test
    fun `post forwards the silent flag`() {
        val poster = FakePoster()
        ProxyNotificationManager.post(poster, entry().copy(silent = true), null)
        assertEquals(true, poster.lastEntry?.silent)
        ProxyNotificationManager.post(poster, entry(), null)
        assertEquals(false, poster.lastEntry?.silent)
    }

    @Test
    fun `pre-planned post uses the caller-provided id`() {
        val poster = FakePoster()
        val plan = ProxyNotificationManager.PostPlan(
            notificationId = 777,
            title = "Planned",
            visibility = Notification.VISIBILITY_PRIVATE,
            redactPublic = true
        )
        assertTrue(ProxyNotificationManager.post(poster, plan, entry(), null))
        assertEquals(777, poster.lastPlan?.notificationId)
    }

    @Test
    fun `cancel delegates to the poster`() {
        val poster = FakePoster()
        poster.cancel(42)
        assertEquals(listOf(42), poster.canceledIds)
    }
}
