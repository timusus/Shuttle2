package com.simplecityapps.shuttle.entitlement

import kotlinx.coroutines.flow.SharedFlow

/** Where a refused action wants the paywall opened from. Dropped while nothing on screen collects it. */
fun interface ObservePaywallRequests {
    operator fun invoke(): SharedFlow<PaywallSource>
}
