package com.simplecityapps.shuttle.entitlement

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** Pro last seen from the store, kept so a Pro user isn't locked out while the store is slow or unreachable. */
data class CachedPro(
    val source: ProSource,
    val seenAt: Instant
)

/** How long [CachedPro] stands in for the store when the store hasn't answered. */
val CACHED_PRO_VALIDITY = 7.days

/**
 * Resolves the user's [Entitlement]: Pro, else a running trial, else Free, else (before the store has answered and
 * with no trial) Unknown.
 *
 * @param storeAnswered whether the store has reported the user's purchases yet.
 * @param ownedPro the best Pro the store reports the user owns, or null if none (or it hasn't answered).
 * @param cachedPro the last Pro seen from the store; used only while it hasn't answered. Without it, a user who never
 *   had the trial is [Entitlement.Unknown] until the store answers.
 * @param trialStartedAt when the server trial started, or null if it never has.
 * @param trialLength how long the trial runs from [trialStartedAt]; Android's by default, iOS passes its own.
 */
fun resolveEntitlement(
    storeAnswered: Boolean,
    ownedPro: ProSource?,
    cachedPro: CachedPro?,
    trialStartedAt: Instant?,
    now: Instant,
    isDebug: Boolean = false,
    trialLength: Duration = Entitlement.TRIAL_LENGTH
): Entitlement {
    if (isDebug) return Entitlement.Pro(ProSource.Debug)

    val proSource = if (storeAnswered) {
        ownedPro
    } else {
        cachedPro?.takeIf { now - it.seenAt < CACHED_PRO_VALIDITY }?.source
    }
    if (proSource != null) return Entitlement.Pro(proSource)

    if (trialStartedAt != null) {
        val endsAt = trialStartedAt + trialLength
        return if (now < endsAt) Entitlement.Trial(endsAt) else Entitlement.Free(trialUsed = true)
    }
    return if (storeAnswered) Entitlement.Free(trialUsed = false) else Entitlement.Unknown
}
