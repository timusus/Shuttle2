package com.simplecityapps.shuttle.entitlement

import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * What the user may do with remote servers (Jellyfin, Emby, Plex). Local playback is never gated.
 *
 * Each platform resolves it from its own store ([resolveEntitlement]): Play Billing on Android (`:android:trial`), and
 * StoreKit on iOS (`:shared`'s `StoreEntitlements`).
 */
sealed interface Entitlement {
    /**
     * The store hasn't answered yet, nothing is cached and the trial hasn't started, so a purchaser can't be told apart
     * from a new user. Resolves once the store answers; the trial never starts meanwhile.
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
 * A debug-build-only override of the resolved [Entitlement], for exercising paywall UI without a real purchase or
 * trial. Android sets it through `DebugEntitlementReceiver` (`android/app/src/debug`); iOS from Settings' debug section.
 */
enum class DebugEntitlementOverride {
    /** No override: debug builds resolve their normal always-Pro entitlement. */
    None,
    Free,
    Trial,
    Pro,

    /**
     * Resolve from the store as a release build does, rather than always Pro, so a debug build can exercise real
     * purchases (the Play test tracks, or StoreKit's local `S2.storekit` configuration on iOS).
     */
    Store
}

/** The [Entitlement] this override stands in for, or null where the store resolves it ([None], [Store]). */
fun DebugEntitlementOverride.toEntitlement(
    now: Instant,
    trialLength: Duration = Entitlement.TRIAL_LENGTH
): Entitlement? = when (this) {
    DebugEntitlementOverride.None, DebugEntitlementOverride.Store -> null
    DebugEntitlementOverride.Free -> Entitlement.Free(trialUsed = false)
    DebugEntitlementOverride.Trial -> Entitlement.Trial(now + trialLength)
    DebugEntitlementOverride.Pro -> Entitlement.Pro(ProSource.Debug)
}

/** Whether a build resolves as always-Pro: a debug build, unless the override asks for the store's answer. */
fun DebugEntitlementOverride.resolvesAsDebug(isDebug: Boolean): Boolean = isDebug && this != DebugEntitlementOverride.Store
