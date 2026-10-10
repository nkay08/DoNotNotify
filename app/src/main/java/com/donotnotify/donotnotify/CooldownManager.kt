package com.donotnotify.donotnotify

/**
 * Pure, Android-free cooldown planning, JVM-testable like [RuleMatcher.planNotificationDecision].
 *
 * Two scopes:
 *
 * - **PACKAGE** — any enabled non-STACK rule for the package with `cooldownEnabled = true` and
 *   PACKAGE scope (or any DENYLIST cooldown rule, which is always PACKAGE) opts the whole
 *   package in: every unblocked non-stacked notification consumes and is gated by the window.
 *   Smallest duration wins across such rules.
 * - **RULE** — the window belongs to one rule. Only notifications that *matched* an ALLOWLIST
 *   cooldown rule with RULE scope consume and are gated by its window; each matched rule gets
 *   an independent window, and a notification matching several of them is suppressed when any
 *   window is active. Non-matching notifications are untouched.
 *
 * Windows are recorded only when the notification actually passes, so a suppressed notification
 * never restarts a window.
 */
object CooldownManager {

    /** Max allowed window, also used by the UI clamp: 24 hours. */
    const val MAX_SECONDS = 24 * 60 * 60

    /** One active throttle window. [key] addresses its last-pass record in storage. */
    data class CooldownWindow(val key: String, val seconds: Int)

    private fun packageKey(packageName: String) = "package:$packageName"
    private fun ruleKey(ruleId: String) = "rule:$ruleId"

    /**
     * The active windows for a notification: one package window when the package is opted in
     * (PACKAGE-scope rules or any DENYLIST cooldown rule; smallest duration wins), plus one
     * window per *matched* RULE-scope ALLOWLIST cooldown rule. Empty when nothing applies.
     */
    fun windowsFor(
        rules: List<BlockerRule>,
        packageName: String,
        matchedRules: List<BlockerRule>
    ): List<CooldownWindow> {
        val windows = mutableListOf<CooldownWindow>()

        val packageSeconds = rules
            .filter {
                it.packageName == packageName &&
                    it.isEnabled &&
                    it.ruleType != RuleType.STACK &&
                    it.advancedConfig?.cooldownEnabled == true &&
                    (it.ruleType == RuleType.DENYLIST ||
                        it.advancedConfig?.cooldownScope == CooldownScope.PACKAGE)
            }
            .mapNotNull { it.advancedConfig?.cooldownSeconds?.takeIf { s -> s > 0 } }
            .minOrNull()
        packageSeconds?.let { windows.add(CooldownWindow(packageKey(packageName), it)) }

        matchedRules.forEach { rule ->
            val cfg = rule.advancedConfig ?: return@forEach
            // DENYLIST matches are blocked by the rules themselves, so RULE scope on a
            // DENYLIST rule could never throttle anything — ALLOWLIST only.
            if (rule.ruleType != RuleType.ALLOWLIST ||
                !cfg.cooldownEnabled ||
                cfg.cooldownScope != CooldownScope.RULE
            ) return@forEach
            cfg.cooldownSeconds.takeIf { it > 0 }?.let {
                windows.add(CooldownWindow(ruleKey(rule.id), it))
            }
        }

        return windows
    }

    /**
     * True when the notification must be suppressed: a previous pass exists and the window
     * since it has not elapsed yet. `now - lastPassAt == window` passes (window has run out).
     */
    fun shouldSuppress(lastPassAt: Long?, cooldownSeconds: Int, now: Long): Boolean =
        lastPassAt != null && now - lastPassAt < cooldownSeconds * 1000L
}
