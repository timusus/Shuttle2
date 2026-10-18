package com.simplecityapps.shuttle.shared

import com.simplecityapps.shuttle.analytics.MonetisationAnalytics
import com.simplecityapps.shuttle.entitlement.ObservePaywallRequests
import com.simplecityapps.shuttle.shared.entitlement.CarPlayAccess
import com.simplecityapps.shuttle.shared.entitlement.StoreEntitlements
import com.simplecityapps.shuttle.shared.telemetry.IosTelemetryStartup

/** Shuttle Music Pro's entitlement and paywall, and the telemetry around them; part of [IosAppGraph]. */
interface IosStoreAccessors {
    /** The user's entitlement, fed from StoreKit by Swift's `StoreKitManager`. */
    val storeEntitlements: StoreEntitlements

    /** A gated action's paywall requests, which Swift's `PaywallPresenter` answers. */
    val observePaywallRequests: ObservePaywallRequests

    /** Whether CarPlay is locked behind Shuttle Music Pro, which `CarPlaySceneDelegate` follows. */
    val carPlayAccess: CarPlayAccess

    /** Crash reporting, analytics and their consent; Swift starts it first of all, at launch. */
    val telemetryStartup: IosTelemetryStartup

    /** The paywall and purchase events Swift's `PaywallPresenter` and `StoreKitManager` record. */
    val monetisationAnalytics: MonetisationAnalytics
}
