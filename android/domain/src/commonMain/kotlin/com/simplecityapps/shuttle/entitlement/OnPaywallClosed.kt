package com.simplecityapps.shuttle.entitlement

/** The paywall opened from [PaywallSource] has closed; the gate resumes an add it refused if the entitlement now allows it. */
fun interface OnPaywallClosed {
    operator fun invoke(source: PaywallSource)
}
