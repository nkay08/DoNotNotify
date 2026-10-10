package com.donotnotify.donotnotify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure planning for [CooldownManager]. */
class CooldownManagerTest {

    private fun allowCooldown(
        seconds: Int = 300,
        enabled: Boolean = true,
        scope: CooldownScope = CooldownScope.RULE
    ) = BlockerRule(
        packageName = "p",
        ruleType = RuleType.ALLOWLIST,
        isEnabled = enabled,
        advancedConfig = AdvancedRuleConfig(
            cooldownEnabled = true,
            cooldownSeconds = seconds,
            cooldownScope = scope
        )
    )

    private fun denyCooldown(seconds: Int = 300, enabled: Boolean = true) = BlockerRule(
        packageName = "p",
        ruleType = RuleType.DENYLIST,
        isEnabled = enabled,
        advancedConfig = AdvancedRuleConfig(cooldownEnabled = true, cooldownSeconds = seconds)
    )

    // ---- windowsFor: package scope ----------------------------------------------

    @Test
    fun `no cooldown rules - no windows`() {
        assertTrue(CooldownManager.windowsFor(emptyList(), "p", emptyList()).isEmpty())
        assertTrue(
            CooldownManager.windowsFor(
                listOf(BlockerRule(packageName = "p", ruleType = RuleType.ALLOWLIST)),
                "p",
                emptyList()
            ).isEmpty()
        )
    }

    @Test
    fun `package-scope rule opts the whole package in`() {
        val rules = listOf(allowCooldown(scope = CooldownScope.PACKAGE, seconds = 120))
        val windows = CooldownManager.windowsFor(rules, "p", emptyList())
        assertEquals(listOf(CooldownManager.CooldownWindow("package:p", 120)), windows)
    }

    @Test
    fun `package scope - smallest duration wins`() {
        val rules = listOf(
            allowCooldown(scope = CooldownScope.PACKAGE, seconds = 300),
            allowCooldown(scope = CooldownScope.PACKAGE, seconds = 15)
        )
        assertEquals(
            listOf(CooldownManager.CooldownWindow("package:p", 15)),
            CooldownManager.windowsFor(rules, "p", emptyList())
        )
    }

    @Test
    fun `denylist cooldown is always package scope`() {
        val rules = listOf(denyCooldown(seconds = 45))
        assertEquals(
            listOf(CooldownManager.CooldownWindow("package:p", 45)),
            CooldownManager.windowsFor(rules, "p", emptyList())
        )
    }

    @Test
    fun `stack rule with cooldown flag is ignored`() {
        val stackWithCooldown = BlockerRule(
            packageName = "p",
            ruleType = RuleType.STACK,
            advancedConfig = AdvancedRuleConfig(cooldownEnabled = true, cooldownSeconds = 120)
        )
        assertTrue(CooldownManager.windowsFor(listOf(stackWithCooldown), "p", emptyList()).isEmpty())
    }

    @Test
    fun `disabled cooldown rule ignored`() {
        assertTrue(
            CooldownManager.windowsFor(listOf(allowCooldown(enabled = false)), "p", emptyList()).isEmpty()
        )
    }

    @Test
    fun `other package ignored`() {
        val rules = listOf(allowCooldown(scope = CooldownScope.PACKAGE).copy(packageName = "other"))
        assertTrue(CooldownManager.windowsFor(rules, "p", emptyList()).isEmpty())
    }

    @Test
    fun `non-positive durations ignored`() {
        val rules = listOf(
            allowCooldown(scope = CooldownScope.PACKAGE, seconds = 0),
            allowCooldown(scope = CooldownScope.PACKAGE, seconds = 60)
        )
        assertEquals(
            listOf(CooldownManager.CooldownWindow("package:p", 60)),
            CooldownManager.windowsFor(rules, "p", emptyList())
        )
        assertTrue(
            CooldownManager.windowsFor(
                listOf(allowCooldown(scope = CooldownScope.PACKAGE, seconds = 0)),
                "p",
                emptyList()
            ).isEmpty()
        )
    }

    // ---- windowsFor: rule scope --------------------------------------------------

    @Test
    fun `rule-scope window only for matched rules`() {
        val rule = allowCooldown(seconds = 90)
        assertEquals(
            listOf(CooldownManager.CooldownWindow("rule:${rule.id}", 90)),
            CooldownManager.windowsFor(listOf(rule), "p", listOf(rule))
        )
        // Same rule, notification did not match it: no window.
        assertTrue(CooldownManager.windowsFor(listOf(rule), "p", emptyList()).isEmpty())
    }

    @Test
    fun `unmatched rule-scope rule does not suppress anything`() {
        val rule = allowCooldown(seconds = 90)
        val otherMatched = BlockerRule(packageName = "p", ruleType = RuleType.ALLOWLIST)
        assertTrue(CooldownManager.windowsFor(listOf(rule), "p", listOf(otherMatched)).isEmpty())
    }

    @Test
    fun `multiple matched rule-scope rules - independent windows`() {
        val a = allowCooldown(seconds = 30)
        val b = allowCooldown(seconds = 120)
        val windows = CooldownManager.windowsFor(listOf(a, b), "p", listOf(a, b))
        assertEquals(
            listOf(
                CooldownManager.CooldownWindow("rule:${a.id}", 30),
                CooldownManager.CooldownWindow("rule:${b.id}", 120)
            ),
            windows
        )
    }

    @Test
    fun `package and matched rule windows coexist`() {
        val packageRule = allowCooldown(scope = CooldownScope.PACKAGE, seconds = 60)
        val matchedRule = allowCooldown(seconds = 15)
        val windows = CooldownManager.windowsFor(
            listOf(packageRule, matchedRule),
            "p",
            listOf(matchedRule)
        )
        assertEquals(
            listOf(
                CooldownManager.CooldownWindow("package:p", 60),
                CooldownManager.CooldownWindow("rule:${matchedRule.id}", 15)
            ),
            windows
        )
    }

    @Test
    fun `matched denylist rule never yields a rule window`() {
        val rule = denyCooldown(seconds = 30)
        // A matched DENYLIST rule means the notification is blocked by the rules; the caller
        // guards this, but windowsFor must not produce a rule window even if misused.
        assertTrue(CooldownManager.windowsFor(listOf(rule), "p", listOf(rule)).all { it.key.startsWith("package:") })
    }

    // ---- shouldSuppress ---------------------------------------------------------

    @Test
    fun `no previous pass - never suppressed`() {
        assertFalse(CooldownManager.shouldSuppress(null, 60, 1_000_000L))
    }

    @Test
    fun `inside window - suppressed`() {
        assertTrue(CooldownManager.shouldSuppress(1_000L, 60, 30_000L))
    }

    @Test
    fun `window edge passes`() {
        // Exactly 60s after the pass the window has run out.
        assertFalse(CooldownManager.shouldSuppress(1_000L, 60, 61_000L))
    }

    @Test
    fun `outside window - passes`() {
        assertFalse(CooldownManager.shouldSuppress(1_000L, 60, 70_000L))
    }
}
