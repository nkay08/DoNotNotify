package com.donotnotify.donotnotify

import android.os.Parcelable
import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import java.util.UUID
import kotlinx.parcelize.Parcelize
@Keep
enum class MatchType {
    CONTAINS,
    REGEX
}

@Keep
enum class RuleType {
    @SerializedName(value = "DENYLIST", alternate = ["BLACKLIST"])
    DENYLIST,
    @SerializedName(value = "ALLOWLIST", alternate = ["WHITELIST"])
    ALLOWLIST,

    @SerializedName(value = "STACK", alternate = ["GROUP", "STACKED"])
    STACK
}

/**
 * Cooldown throttle scope. PACKAGE throttles every unblocked notification of the app;
 * RULE throttles only notifications that matched the cooldown rule. DENYLIST cooldowns are
 * always treated as PACKAGE by [CooldownManager] regardless of the stored value.
 */
@Keep
enum class CooldownScope {
    PACKAGE,
    RULE
}

@Keep
@Parcelize
data class AdvancedRuleConfig(
    val isTimeLimitEnabled: Boolean = false,
    val startTimeHour: Int = 9,
    val startTimeMinute: Int = 0,
    val endTimeHour: Int = 17,
    val endTimeMinute: Int = 0,
    /**
     * Package-level proxy flag (any enabled non-STACK rule for the package with this
     * on): notifications from that app which survive blocking are canceled and re-posted
     * by DoNotNotify itself, so watch-companion apps only ever see our notification.
     */
    val proxyEnabled: Boolean = false,
    /**
     * Package-level cooldown opt-in (any enabled non-STACK rule for the package with this
     * on): only the first unblocked notification passes; further notifications from that
     * app are blocked until [cooldownSeconds] have elapsed since the last pass.
     */
    val cooldownEnabled: Boolean = false,
    /** Cooldown window length. Seconds is the single stored unit (UI converts minutes). */
    val cooldownSeconds: Int = 300,
    /** PACKAGE = throttle the whole app; RULE = throttle only notifications matching the rule. */
    val cooldownScope: CooldownScope = CooldownScope.RULE
) : Parcelable

@Keep
@Parcelize
data class BlockerRule(
    val appName: String? = null,
    val packageName: String? = null,
    val titleFilter: String? = null,
    val titleMatchType: MatchType = MatchType.CONTAINS,
    val textFilter: String? = null,
    val textMatchType: MatchType = MatchType.CONTAINS,
    val hitCount: Int = 0,
    val ruleType: RuleType = RuleType.DENYLIST,
    val isEnabled: Boolean = true,
    val advancedConfig: AdvancedRuleConfig? = null,
    /**
     * Stable, device-local identity. Rules created through the Kotlin constructor get a
     * UUID from this default; rules loaded by Gson get **null** (Gson allocates via Unsafe
     * and never runs the constructor), so every load path must pass through
     * [RuleIds.normalizeIds]. Never exported — see the export ExclusionStrategy.
     */
    val id: String = UUID.randomUUID().toString(),
    /** Optional user-supplied label. Used as the notification channel name for STACK rules. */
    val name: String? = null
) : Parcelable