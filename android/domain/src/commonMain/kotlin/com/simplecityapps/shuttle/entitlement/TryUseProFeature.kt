package com.simplecityapps.shuttle.entitlement

/**
 * Whether the user may use [ProFeature] now, starting the trial on its first use where it starts without asking
 * ([ServerAccessGate.tryUse]). A store that hasn't answered yet lets it through, so a purchaser is never blocked. A
 * refusal has already asked for the paywall, so the caller just doesn't go ahead.
 */
fun interface TryUseProFeature {
    suspend operator fun invoke(feature: ProFeature): Boolean
}
