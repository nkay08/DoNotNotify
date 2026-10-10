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
     * Phone-silent proxying (any enabled non-STACK proxy rule for the package with this
     * on): re-posted notifications are posted with setSilent(true), so the phone neither
     * sounds nor vibrates while watch-companion apps still forward the content.
     */
    val proxySilent: Boolean = false
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
