package com.simplecityapps.shuttle.ui.screens.paywall

/** What the paywall asks of its host. */
sealed interface PaywallEvent {
    data object Close : PaywallEvent

    data object Purchase : PaywallEvent

    data object StartTrial : PaywallEvent

    data object Restore : PaywallEvent

    data object RedeemCode : PaywallEvent

    data object Retry : PaywallEvent

    data object ManageSubscription : PaywallEvent

    data object OpenPrivacyPolicy : PaywallEvent
}

class PaywallCallbacks(
    val onClose: () -> Unit,
    val onPurchase: () -> Unit,
    val onRestore: () -> Unit,
    val onRedeemCode: () -> Unit,
    val onRetry: () -> Unit,
    val onManageSubscription: () -> Unit,
    val onStartTrial: () -> Unit,
    val onOpenPrivacyPolicy: () -> Unit
) {
    fun onEvent(event: PaywallEvent) = when (event) {
        PaywallEvent.Close -> onClose()
        PaywallEvent.Purchase -> onPurchase()
        PaywallEvent.StartTrial -> onStartTrial()
        PaywallEvent.Restore -> onRestore()
        PaywallEvent.RedeemCode -> onRedeemCode()
        PaywallEvent.Retry -> onRetry()
        PaywallEvent.ManageSubscription -> onManageSubscription()
        PaywallEvent.OpenPrivacyPolicy -> onOpenPrivacyPolicy()
    }
}

fun PaywallViewState.Tap.event(): PaywallEvent = when (this) {
    PaywallViewState.Tap.StartTrial -> PaywallEvent.StartTrial
    PaywallViewState.Tap.Purchase -> PaywallEvent.Purchase
}
