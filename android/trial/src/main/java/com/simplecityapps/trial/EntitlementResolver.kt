package com.simplecityapps.trial

import com.simplecityapps.shuttle.entitlement.CachedPro
import com.simplecityapps.shuttle.entitlement.Entitlement
import com.simplecityapps.shuttle.entitlement.ProSource
import kotlin.time.Instant

/**
 * Resolves the user's [Entitlement] from Play's answer, by the rule both platforms share
 * ([com.simplecityapps.shuttle.entitlement.resolveEntitlement]).
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
): Entitlement = com.simplecityapps.shuttle.entitlement.resolveEntitlement(
    storeAnswered = ownedProductIds != null,
    ownedPro = ownedProductIds?.proSource(),
    cachedPro = cachedPro,
    trialStartedAt = trialStartedAt,
    now = now,
    isDebug = isDebug
)

/** The best Pro source among [this] product IDs: the current products before the legacy ones, lifetime before subscription. */
internal fun Set<String>.proSource(): ProSource? = mapNotNull { ProductIds.proSource(it) }.minOrNull()
