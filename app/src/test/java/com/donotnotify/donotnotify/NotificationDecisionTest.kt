package com.donotnotify.donotnotify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Precedence matrix for the pure [RuleMatcher.planNotificationDecision]. */
class NotificationDecisionTest {

    private fun deny(t: String) =
        BlockerRule(packageName = "p", titleFilter = t, ruleType = RuleType.DENYLIST)

    private fun allow(t: String) =
        BlockerRule(packageName = "p", titleFilter = t, ruleType = RuleType.ALLOWLIST)

    private fun stack(t: String) =
        BlockerRule(packageName = "p", titleFilter = t, ruleType = RuleType.STACK)

    private fun allowProxy(t: String) =
        BlockerRule(
            packageName = "p",
            titleFilter = t,
            ruleType = RuleType.ALLOWLIST,
            advancedConfig = AdvancedRuleConfig(proxyEnabled = true)
        )

    @Test
    fun `stack only, matching, not ongoing - stacks, not blocked`() {
        val d = RuleMatcher.planNotificationDecision(listOf(stack("News")), "p", "News x", null, false)
        assertFalse(d.isBlocked)
        assertTrue(d.shouldStack)
        assertEquals(listOf(0), d.matchedRuleIndices)
    }

    @Test
    fun `stack only, non-matching - neither blocked nor stacked`() {
        val d = RuleMatcher.planNotificationDecision(listOf(stack("News")), "p", "Other", null, false)
        assertFalse(d.isBlocked)
        assertFalse(d.shouldStack)
        assertTrue(d.matchedRuleIndices.isEmpty())
    }

    @Test
    fun `denylist wins over stack`() {
        val rules = listOf(stack("Promo"), deny("Promo"))
        val d = RuleMatcher.planNotificationDecision(rules, "p", "Promo!", null, false)
        assertTrue(d.isBlocked)
        assertFalse(d.shouldStack)
        assertNull(d.matchedStackRule)
    }

    @Test
    fun `allowlist gating wins over stack`() {
        val rules = listOf(allow("OTP"), stack("Promo"))
        val d = RuleMatcher.planNotificationDecision(rules, "p", "Promo!", null, false)
        assertTrue(d.isBlocked) // allowlist present but unmatched
        assertFalse(d.shouldStack)
    }

    @Test
    fun `first enabled stack match wins`() {
        val first = stack("A")
        val second = stack("A")
        val d = RuleMatcher.planNotificationDecision(listOf(first, second), "p", "A here", null, false)
        assertTrue(d.shouldStack)
        assertSame(first, d.matchedStackRule)
        assertEquals(listOf(0), d.matchedRuleIndices)
    }

    @Test
    fun `ongoing notification is excluded from stacking`() {
        val d = RuleMatcher.planNotificationDecision(listOf(stack("News")), "p", "News x", null, true)
        assertFalse(d.shouldStack)
    }

    @Test
    fun `stack never gates - other package unaffected`() {
        val d = RuleMatcher.planNotificationDecision(listOf(stack("News")), "other", "News", null, false)
        assertFalse(d.isBlocked)
        assertFalse(d.shouldStack)
    }

    @Test
    fun `disabled stack rule ignored`() {
        val rule = stack("News").copy(isEnabled = false)
        val d = RuleMatcher.planNotificationDecision(listOf(rule), "p", "News", null, false)
        assertFalse(d.shouldStack)
    }

    // ---- proxy ---------------------------------------------------------------

    @Test
    fun `allowlist proxy, matching, not ongoing - proxied, not blocked`() {
        val d = RuleMatcher.planNotificationDecision(listOf(allowProxy("OTP")), "p", "OTP 123", null, false)
        assertFalse(d.isBlocked)
        assertFalse(d.shouldStack)
        assertTrue(d.shouldProxy)
    }

    @Test
    fun `proxy flag off - no proxy`() {
        val d = RuleMatcher.planNotificationDecision(listOf(allow("OTP")), "p", "OTP 123", null, false)
        assertFalse(d.shouldProxy)
    }

    @Test
    fun `blocked notification is never proxied`() {
        val rules = listOf(allowProxy("OTP"), deny("OTP"))
        val d = RuleMatcher.planNotificationDecision(rules, "p", "OTP 123", null, false)
        assertTrue(d.isBlocked)
        assertFalse(d.shouldProxy)
    }

    @Test
    fun `allowlist gating block wins over proxy`() {
        // Proxy flag on, but the notification fails the allowlist gate: blocked, not proxied.
        val d = RuleMatcher.planNotificationDecision(listOf(allowProxy("OTP")), "p", "Other", null, false)
        assertTrue(d.isBlocked)
        assertFalse(d.shouldProxy)
    }

    @Test
    fun `stack wins over proxy`() {
        val rules = listOf(allowProxy("News"), stack("News"))
        val d = RuleMatcher.planNotificationDecision(rules, "p", "News x", null, false)
        assertFalse(d.isBlocked)
        assertTrue(d.shouldStack)
        assertFalse(d.shouldProxy)
    }

    @Test
    fun `ongoing notification is excluded from proxying`() {
        val d = RuleMatcher.planNotificationDecision(listOf(allowProxy("OTP")), "p", "OTP 123", null, true)
        assertFalse(d.shouldProxy)
    }

    @Test
    fun `proxy flag on any enabled rule for the package applies`() {
        val flagged = deny("A").copy(advancedConfig = AdvancedRuleConfig(proxyEnabled = true))
        val rules = listOf(flagged, allow("OTP"))
        val d = RuleMatcher.planNotificationDecision(rules, "p", "OTP 123", null, false)
        assertFalse(d.isBlocked)
        assertTrue(d.shouldProxy)
    }

    @Test
    fun `proxy flag on stack rule alone never proxies`() {
        val stackWithProxy = stack("News").copy(advancedConfig = AdvancedRuleConfig(proxyEnabled = true))
        val d = RuleMatcher.planNotificationDecision(listOf(stackWithProxy), "p", "Other", null, false)
        assertFalse(d.shouldProxy)
    }

    @Test
    fun `proxy flag on disabled rule ignored`() {
        val rule = allowProxy("OTP").copy(isEnabled = false)
        val d = RuleMatcher.planNotificationDecision(listOf(rule), "p", "OTP 123", null, false)
        assertFalse(d.shouldProxy)
    }

    @Test
    fun `proxy flag from another package does not apply`() {
        val rule = BlockerRule(
            packageName = "other",
            ruleType = RuleType.ALLOWLIST,
            advancedConfig = AdvancedRuleConfig(proxyEnabled = true)
        )
        val d = RuleMatcher.planNotificationDecision(listOf(rule), "p", "OTP 123", null, false)
        assertFalse(d.shouldProxy)
    }
}
