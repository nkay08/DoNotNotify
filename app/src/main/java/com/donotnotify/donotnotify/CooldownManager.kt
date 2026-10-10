package com.donotnotify.donotnotify

/**
 * Pure, Android-free cooldown planning, JVM-testable like [RuleMatcher.planNotificationDecision].
 *
 * Cooldown is package-level: any enabled non-STACK rule for the package with
 * `advancedConfig.cooldownEnabled = true` opts the whole package in. When several such rules
 * exist with different durations, the smallest wins (most restrictive). STACK rules are
 * excluded — grouping already handles chatty apps, and a proxy/stack re-post must never be
 * suppressed by a flag the user put on an unrelated rule type.
 */
object CooldownManager {

    /** Max allowed window, also used by the UI clamp: 24 hours. */
    const val MAX_SECONDS = 24 * 60 * 60

    /**
     * The active cooldown window for [packageName] in seconds, or null when the package is
     * not opted in. Non-positive durations on opted-in rules are ignored.
     */
    fun cooldownSecondsFor(rules: List<BlockerRule>, packageName: String): Int? =
        rules
            .filter {
                it.packageName == packageName &&
                    it.isEnabled &&
                    it.ruleType != RuleType.STACK &&
                    it.advancedConfig?.cooldownEnabled == true
            }
            .mapNotNull { it.advancedConfig?.cooldownSeconds?.takeIf { s -> s > 0 } }
            .minOrNull()

    /**
     * True when the notification must be suppressed: a previous pass exists and the window
     * since it has not elapsed yet. `now - lastPassAt == window` passes (window has run out).
     */
    fun shouldSuppress(lastPassAt: Long?, cooldownSeconds: Int, now: Long): Boolean =
        lastPassAt != null && now - lastPassAt < cooldownSeconds * 1000L
}
