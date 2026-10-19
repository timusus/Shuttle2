package com.simplecityapps.shuttle.ui.screens.paywall

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.entitlement.ProSource

/** What the paywall shows for a [PaywallUiState]: which blocks appear, which buttons are enabled, which copy and which tap does what. */
data class PaywallViewState(
    val status: Copy,
    val explainsTrialEnd: Boolean,
    /** Null for a Pro user, who has nothing to buy. */
    val purchaseBar: PurchaseBar?,
    /** Null for a Pro user. */
    val offer: Copy?,
    val showPricesError: Boolean,
    val showRestore: Boolean,
    val showRedeemCode: Boolean,
    val restoreEnabled: Boolean,
    val showManageSubscription: Boolean
) {
    /** Copy the screen resolves from `strings.xml`, so the choice of string is decided (and tested) here. */
    sealed interface Copy {
        data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : Copy

        data class Plural(@PluralsRes val id: Int, val count: Int) : Copy
    }

    data class PurchaseBar(val buttons: List<Button>)

    data class Button(
        @StringRes val label: Int,
        val tap: Tap,
        val enabled: Boolean,
        val emphasis: Emphasis
    )

    enum class Tap { StartTrial, Purchase }

    enum class Emphasis { Filled, Outlined }
}

fun paywallViewState(uiState: PaywallUiState): PaywallViewState {
    val status = uiState.status
    val isPro = status is PaywallStatus.Pro
    return PaywallViewState(
        status = when (status) {
            PaywallStatus.Checking -> PaywallViewState.Copy.Res(R.string.paywall_status_checking)
            PaywallStatus.TrialAvailable -> PaywallViewState.Copy.Res(R.string.paywall_status_trial_available)
            PaywallStatus.TrialEnded -> PaywallViewState.Copy.Res(R.string.paywall_status_trial_ended)
            is PaywallStatus.Trial -> PaywallViewState.Copy.Plural(R.plurals.paywall_status_trial, status.daysLeft)
            is PaywallStatus.PurchasePending -> PaywallViewState.Copy.Res(R.string.paywall_status_purchase_pending)
            is PaywallStatus.Pro -> PaywallViewState.Copy.Res(if (status.isLegacy) R.string.paywall_status_pro_legacy else R.string.paywall_status_pro)
        },
        explainsTrialEnd = uiState.explainsTrialEnd,
        purchaseBar = if (isPro) null else purchaseBar(uiState),
        offer = if (isPro) {
            null
        } else {
            val price = uiState.offer?.formattedPrice
            when {
                price != null -> PaywallViewState.Copy.Res(R.string.paywall_price_once, listOf(price))
                uiState.offersLoading -> PaywallViewState.Copy.Res(R.string.paywall_price_loading)
                else -> PaywallViewState.Copy.Res(R.string.paywall_price_unavailable)
            }
        },
        showPricesError = !isPro && uiState.offersUnavailable,
        showRestore = !isPro,
        showRedeemCode = !isPro,
        restoreEnabled = !uiState.restoring,
        showManageSubscription = status is PaywallStatus.Pro && (status.source == ProSource.Subscription || status.source == ProSource.LegacySubscription)
    )
}

private fun purchaseBar(uiState: PaywallUiState): PaywallViewState.PurchaseBar = when (uiState.primaryAction) {
    PaywallPrimaryAction.StartTrial -> PaywallViewState.PurchaseBar(
        listOf(
            PaywallViewState.Button(R.string.paywall_start_trial, PaywallViewState.Tap.StartTrial, enabled = true, PaywallViewState.Emphasis.Filled),
            PaywallViewState.Button(R.string.paywall_buy_now, PaywallViewState.Tap.Purchase, uiState.canPurchase, PaywallViewState.Emphasis.Outlined)
        )
    )

    PaywallPrimaryAction.Purchase -> PaywallViewState.PurchaseBar(
        listOf(PaywallViewState.Button(R.string.paywall_purchase, PaywallViewState.Tap.Purchase, uiState.canPurchase, PaywallViewState.Emphasis.Filled))
    )
}
