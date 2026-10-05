package com.simplecityapps.shuttle.entitlement

/**
 * Whether the user may use [ProFeature] now, starting the trial on its first use where it starts without asking. A
 * refusal has already asked for the paywall, so the caller just doesn't go ahead.
 */
fun interface TryUseProFeature {
    suspend operator fun invoke(feature: ProFeature): Boolean
}
