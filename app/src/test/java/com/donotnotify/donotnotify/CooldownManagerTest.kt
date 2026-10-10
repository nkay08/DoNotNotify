package com.donotnotify.donotnotify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure planning for [CooldownManager]. */
class CooldownManagerTest {

    private fun cooldownRule(seconds: Int = 300, enabled: Boolean = true, type: RuleType = RuleType.ALLOWLIST) =
        BlockerRule(
            packageName = "p",
            ruleType = type,
            isEnabled = enabled,
            advancedConfig = AdvancedRuleConfig(cooldownEnabled = true, cooldownSeconds = seconds)
        )

    // ---- cooldownSecondsFor -----------------------------------------------------

    @Test
    fun `no cooldown rules - null`() {
        assertNull(CooldownManager.cooldownSecondsFor(emptyList(), "p"))
        assertNull(
            CooldownManager.cooldownSecondsFor(
                listOf(BlockerRule(packageName = "p", ruleType = RuleType.ALLOWLIST)),
                "p"
            )
        )
    }

    @Test
    fun `opted in by any enabled non-stack rule for the package`() {
        val rules = listOf(
            BlockerRule(packageName = "p", ruleType = RuleType.DENYLIST),
            cooldownRule(seconds = 120)
        )
        assertEquals(120, CooldownManager.cooldownSecondsFor(rules, "p"))
    }

    @Test
    fun `stack rule with cooldown flag is ignored`() {
        val rules = listOf(cooldownRule(seconds = 120, type = RuleType.STACK))
        assertNull(CooldownManager.cooldownSecondsFor(rules, "p"))
    }

    @Test
    fun `disabled cooldown rule ignored`() {
        assertNull(CooldownManager.cooldownSecondsFor(listOf(cooldownRule(enabled = false)), "p"))
    }

    @Test
    fun `other package ignored`() {
        val rules = listOf(cooldownRule(seconds = 120).copy(packageName = "other"))
        assertNull(CooldownManager.cooldownSecondsFor(rules, "p"))
    }

    @Test
    fun `smallest duration wins`() {
        val rules = listOf(
            cooldownRule(seconds = 300),
            cooldownRule(seconds = 15)
        )
        assertEquals(15, CooldownManager.cooldownSecondsFor(rules, "p"))
    }

    @Test
    fun `non-positive durations ignored`() {
        assertEquals(
            60,
            CooldownManager.cooldownSecondsFor(
                listOf(cooldownRule(seconds = 0), cooldownRule(seconds = -5), cooldownRule(seconds = 60)),
                "p"
            )
        )
        assertNull(CooldownManager.cooldownSecondsFor(listOf(cooldownRule(seconds = 0)), "p"))
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
