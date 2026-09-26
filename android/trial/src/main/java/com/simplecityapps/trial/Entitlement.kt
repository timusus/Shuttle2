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
