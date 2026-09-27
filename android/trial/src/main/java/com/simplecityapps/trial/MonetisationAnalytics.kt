package com.simplecityapps.trial

import com.simplecityapps.shuttle.analytics.Analytics
import com.simplecityapps.shuttle.entitlement.PaywallSource
import com.simplecityapps.shuttle.model.MediaProviderType
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/** The paywall and server-use analytics events. */
@SingleIn(AppScope::class)
class MonetisationAnalytics
@Inject
constructor(
    private val analytics: Analytics
) {
    fun paywallShown(source: PaywallSource) = analytics.capture("paywall_shown", mapOf("source" to source.value))

    fun purchaseStarted(productId: String) = analytics.capture("purchase_started", mapOf("product" to productId))

    fun purchaseCompleted(productId: String) = analytics.capture("purchase_completed", mapOf("product" to productId))

    fun trialStarted() = analytics.capture("trial_started")

    fun serverConnected(type: MediaProviderType) = analytics.capture("server_connected", mapOf("type" to type.name.lowercase()))
}
