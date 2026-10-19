package com.simplecityapps.shuttle.ui.screens.paywall

import com.simplecityapps.shuttle.entitlement.ProSource

/** What the paywall shows for a [PaywallUiState]: which blocks appear, which buttons are enabled, and which message. */
data class PaywallViewState(
    val status: StatusMessage,
    val explainsTrialEnd: Boolean,
    /** Null for a Pro user, who has nothing to buy. */
    val purchaseBar: PurchaseBar?,
    /** Null for a Pro user. */
    val offer: Offer?,
    val showPricesError: Boolean,
    val showAccountActions: Boolean,
    val restoreEnabled: Boolean,
    val showManageSubscription: Boolean
) {
    sealed interface StatusMessage {
        data object Checking : StatusMessage

        data object TrialAvailable : StatusMessage

        data object TrialEnded : StatusMessage

        data class Trial(val daysLeft: Int) : StatusMessage

        data object PurchasePending : StatusMessage

        data object Pro : StatusMessage

        data object ProLegacy : StatusMessage
    }

    data class PurchaseBar(val action: PaywallPrimaryAction, val purchaseEnabled: Boolean)

    sealed interface Offer {
        data class Price(val formatted: String) : Offer

        data object Loading : Offer

        data object Unavailable : Offer
    }
}

fun paywallViewState(uiState: PaywallUiState): PaywallViewState {
    val status = uiState.status
    val isPro = status is PaywallStatus.Pro
    return PaywallViewState(
        status = when (status) {
            PaywallStatus.Checking -> PaywallViewState.StatusMessage.Checking
            PaywallStatus.TrialAvailable -> PaywallViewState.StatusMessage.TrialAvailable
            PaywallStatus.TrialEnded -> PaywallViewState.StatusMessage.TrialEnded
            is PaywallStatus.Trial -> PaywallViewState.StatusMessage.Trial(status.daysLeft)
            is PaywallStatus.PurchasePending -> PaywallViewState.StatusMessage.PurchasePending
            is PaywallStatus.Pro -> if (status.isLegacy) PaywallViewState.StatusMessage.ProLegacy else PaywallViewState.StatusMessage.Pro
        },
        explainsTrialEnd = uiState.explainsTrialEnd,
        purchaseBar = if (isPro) null else PaywallViewState.PurchaseBar(uiState.primaryAction, uiState.canPurchase),
        offer = if (isPro) {
            null
        } else {
            val price = uiState.offer?.formattedPrice
            when {
                price != null -> PaywallViewState.Offer.Price(price)
                uiState.offersLoading -> PaywallViewState.Offer.Loading
                else -> PaywallViewState.Offer.Unavailable
            }
        },
        showPricesError = !isPro && uiState.offersUnavailable,
        showAccountActions = !isPro,
        restoreEnabled = !uiState.restoring,
        showManageSubscription = status is PaywallStatus.Pro && (status.source == ProSource.Subscription || status.source == ProSource.LegacySubscription)
    )
}
