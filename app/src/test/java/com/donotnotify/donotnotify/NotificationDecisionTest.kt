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

    private fun allowProxySilent(t: String) =
        BlockerRule(
            packageName = "p",
            titleFilter = t,
            ruleType = RuleType.ALLOWLIST,
            advancedConfig = AdvancedRuleConfig(proxyEnabled = true, proxySilent = true)
        )

    private fun allowDismiss(t: String, seconds: Int = 60) =
        BlockerRule(
            packageName = "p",
            titleFilter = t,
            ruleType = RuleType.ALLOWLIST,
            advancedConfig = AdvancedRuleConfig(dismissEnabled = true, dismissSeconds = seconds)
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

    // ---- proxy silent -----------------------------------------------------------

    @Test
    fun `proxy silent flag applies when proxying`() {
        val d = RuleMatcher.planNotificationDecision(listOf(allowProxySilent("OTP")), "p", "OTP 123", null, false)
        assertTrue(d.shouldProxy)
        assertTrue(d.proxySilent)
    }

    @Test
    fun `proxy silent without silent flag is false`() {
        val d = RuleMatcher.planNotificationDecision(listOf(allowProxy("OTP")), "p", "OTP 123", null, false)
        assertTrue(d.shouldProxy)
        assertFalse(d.proxySilent)
    }

    @Test
    fun `proxy silent flag without proxy has no effect`() {
        val rule = BlockerRule(
            packageName = "p",
            ruleType = RuleType.ALLOWLIST,
            advancedConfig = AdvancedRuleConfig(proxySilent = true)
        )
        val d = RuleMatcher.planNotificationDecision(listOf(rule), "p", "OTP 123", null, false)
        assertFalse(d.shouldProxy)
        assertFalse(d.proxySilent)
    }

    @Test
    fun `proxy silent never set when blocked or stacked`() {
        val blocked = RuleMatcher.planNotificationDecision(
            listOf(allowProxySilent("OTP"), deny("OTP")), "p", "OTP 123", null, false
        )
        assertTrue(blocked.isBlocked)
        assertFalse(blocked.proxySilent)

        val stacked = RuleMatcher.planNotificationDecision(
            listOf(allowProxySilent("News"), stack("News")), "p", "News x", null, false
        )
        assertTrue(stacked.shouldStack)
        assertFalse(stacked.proxySilent)
    }

    @Test
    fun `proxy silent from another package does not apply`() {
        val rule = BlockerRule(
            packageName = "other",
            ruleType = RuleType.ALLOWLIST,
            advancedConfig = AdvancedRuleConfig(proxyEnabled = true, proxySilent = true)
        )
        val d = RuleMatcher.planNotificationDecision(listOf(rule), "p", "OTP 123", null, false)
        assertFalse(d.shouldProxy)
        assertFalse(d.proxySilent)
    }

    // ---- auto-dismiss ------------------------------------------------------------

    @Test
    fun `matched dismiss rule yields dismiss seconds`() {
        val d = RuleMatcher.planNotificationDecision(listOf(allowDismiss("OTP", 90)), "p", "OTP 123", null, false)
        assertEquals(90, d.dismissSeconds)
    }

    @Test
    fun `dismiss flag off - no dismiss`() {
        val d = RuleMatcher.planNotificationDecision(listOf(allow("OTP")), "p", "OTP 123", null, false)
        assertNull(d.dismissSeconds)
    }

    @Test
    fun `earliest dismiss wins across matched rules`() {
        val rules = listOf(allowDismiss("OTP", 300), allowDismiss("OTP", 15))
        val d = RuleMatcher.planNotificationDecision(rules, "p", "OTP 123", null, false)
        assertEquals(15, d.dismissSeconds)
    }

    @Test
    fun `non-matching dismiss rule does not apply`() {
        val d = RuleMatcher.planNotificationDecision(listOf(allowDismiss("OTP")), "p", "Other", null, false)
        assertNull(d.dismissSeconds)
    }

    @Test
    fun `blocked notification never dismisses by timer`() {
        val rules = listOf(allowDismiss("OTP"), deny("OTP"))
        val d = RuleMatcher.planNotificationDecision(rules, "p", "OTP 123", null, false)
        assertTrue(d.isBlocked)
        assertNull(d.dismissSeconds)
    }

    @Test
    fun `stacked notification never dismisses by timer`() {
        val rules = listOf(allowDismiss("News"), stack("News"))
        val d = RuleMatcher.planNotificationDecision(rules, "p", "News x", null, false)
        assertTrue(d.shouldStack)
        assertNull(d.dismissSeconds)
    }

    @Test
    fun `dismiss applies to proxied and ongoing notifications`() {
        val proxied = RuleMatcher.planNotificationDecision(
            listOf(allowDismiss("OTP", 60).copy(advancedConfig = AdvancedRuleConfig(
                dismissEnabled = true, dismissSeconds = 60, proxyEnabled = true))),
            "p", "OTP 123", null, false
        )
        assertTrue(proxied.shouldProxy)
        assertEquals(60, proxied.dismissSeconds)

        val ongoing = RuleMatcher.planNotificationDecision(listOf(allowDismiss("OTP", 60)), "p", "OTP 123", null, true)
        assertEquals(60, ongoing.dismissSeconds)
    }

    @Test
    fun `dismiss from another package does not apply`() {
        val rule = BlockerRule(
            packageName = "other",
            ruleType = RuleType.ALLOWLIST,
            advancedConfig = AdvancedRuleConfig(dismissEnabled = true, dismissSeconds = 30)
        )
        val d = RuleMatcher.planNotificationDecision(listOf(rule), "p", "OTP 123", null, false)
        assertNull(d.dismissSeconds)
    }
}
