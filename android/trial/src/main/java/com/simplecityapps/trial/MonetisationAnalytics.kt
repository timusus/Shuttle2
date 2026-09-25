package com.simplecityapps.trial

import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.remote_config.AnalyticsManager
import javax.inject.Inject
import javax.inject.Singleton

/** The paywall and server-use analytics events. */
@Singleton
class MonetisationAnalytics
@Inject
constructor(
    private val analyticsManager: AnalyticsManager
) {
    fun paywallShown(source: PaywallSource) = analyticsManager.logEvent("paywall_shown", mapOf("source" to source.value))

    fun purchaseStarted(productId: String) = analyticsManager.logEvent("purchase_started", mapOf("product" to productId))

    fun purchaseCompleted(productId: String) = analyticsManager.logEvent("purchase_completed", mapOf("product" to productId))

    fun trialStarted() = analyticsManager.logEvent("trial_started")

    fun serverConnected(type: MediaProviderType) = analyticsManager.logEvent("server_connected", mapOf("type" to type.name.lowercase()))
}

/** Where the paywall was opened from. */
enum class PaywallSource(val value: String) {
    LibraryTrialChip("library_trial_chip"),
    QueueTrialChip("queue_trial_chip"),
    Settings("settings"),
    AddServer("add_server"),
    ServerPlayback("server_playback"),
    ServerDownload("server_download")
}
