package com.simplecityapps.shuttle.entitlement

/**
 * The paywall opened from [PaywallSource] has closed; the gate resumes an add it refused if the entitlement now allows
 * it, waiting briefly for a purchase that Play hasn't acknowledged yet.
 */
fun interface OnPaywallClosed {
    suspend operator fun invoke(source: PaywallSource)
}
