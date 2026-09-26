package com.simplecityapps.trial

import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** Pro last seen from Play, kept so a Pro user isn't locked out while Play is slow or unreachable. */
data class CachedPro(
    val source: ProSource,
    val seenAt: Instant
)

/** How long [CachedPro] stands in for Play when Play hasn't answered. */
internal val CACHED_PRO_VALIDITY = 7.days

/**
 * Resolves the user's [Entitlement].
 *
 * @param ownedProductIds product IDs with a completed (PURCHASED) purchase, or null while Play hasn't answered yet.
 * @param cachedPro the last Pro seen from Play; used only while [ownedProductIds] is null.
 *   Without it, a user who never had the trial is [Entitlement.Unknown] until Play answers.
 * @param trialStartedAt when the server trial started, or null if it never has.
 */
internal fun resolveEntitlement(
    ownedProductIds: Set<String>?,
    cachedPro: CachedPro?,
    trialStartedAt: Instant?,
    now: Instant,
    isDebug: Boolean = false
): Entitlement {
    if (isDebug) return Entitlement.Pro(ProSource.Debug)

    val proSource = if (ownedProductIds != null) {
        ownedProductIds.proSource()
    } else {
        cachedPro?.takeIf { now - it.seenAt < CACHED_PRO_VALIDITY }?.source
    }
    if (proSource != null) return Entitlement.Pro(proSource)

    if (trialStartedAt != null) {
        val endsAt = trialStartedAt + Entitlement.TRIAL_LENGTH
        return if (now < endsAt) Entitlement.Trial(endsAt) else Entitlement.Free(trialUsed = true)
    }
    return if (ownedProductIds == null) Entitlement.Unknown else Entitlement.Free(trialUsed = false)
}

/** The best Pro source among [this] product IDs: the current products before the legacy ones, lifetime before subscription. */
internal fun Set<String>.proSource(): ProSource? = mapNotNull { ProductIds.proSource(it) }.minOrNull()
