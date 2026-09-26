package com.simplecityapps.trial

import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** What the user may do with remote servers (Jellyfin, Emby, Plex). Local playback is never gated. */
sealed interface Entitlement {
    /**
     * Play hasn't answered yet, nothing is cached and the trial hasn't started, so a purchaser can't be told apart
     * from a new user. Resolves once Play answers; the trial never starts meanwhile.
     */
    data object Unknown : Entitlement

    /**
     * No Pro and no running trial.
     *
     * @param trialUsed true once the server trial has started, so it can't be offered again.
     */
    data class Free(val trialUsed: Boolean) : Entitlement

    /** The server trial is running until [endsAt]. */
    data class Trial(val endsAt: Instant) : Entitlement {
        /** Whole days left, rounded up, so the first day of a 14-day trial reads 14. */
        fun daysRemaining(now: Instant = Clock.System.now()): Int = ((endsAt - now).inWholeHours.coerceAtLeast(0) + 23).toInt() / 24
    }

    data class Pro(val source: ProSource) : Entitlement

    companion object {
        val TRIAL_LENGTH = 14.days
    }
}

/** What granted Pro. */
enum class ProSource {
    Lifetime,
    Subscription,
    LegacyLifetime,
    LegacySubscription,

    /** Debug builds are always Pro. */
    Debug
}

/**
 * A debug-build-only override of the resolved [Entitlement], for exercising paywall UI on the emulator
 * without a real purchase or trial. Set through [EntitlementRepository.setDebugOverride], driven by
 * `DebugEntitlementReceiver` (`android/app/src/debug`).
 */
enum class DebugEntitlementOverride {
    /** No override: debug builds resolve their normal always-Pro entitlement. */
    None,
    Free,
    Trial,
    Pro
}

/** The [Entitlement] this override stands in for, or null for [DebugEntitlementOverride.None]. */
internal fun DebugEntitlementOverride.toEntitlement(now: Instant): Entitlement? = when (this) {
    DebugEntitlementOverride.None -> null
    DebugEntitlementOverride.Free -> Entitlement.Free(trialUsed = false)
    DebugEntitlementOverride.Trial -> Entitlement.Trial(now + Entitlement.TRIAL_LENGTH)
    DebugEntitlementOverride.Pro -> Entitlement.Pro(ProSource.Debug)
}
