package com.simplecityapps.trial

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.Purchase
import com.simplecityapps.shuttle.analytics.MonetisationAnalytics
import com.simplecityapps.shuttle.analytics.PurchaseFailureReason

/** Reports the purchases that didn't complete, mapping Play's response codes to the reasons iOS reports. */
internal class PurchaseOutcomeReporter(
    private val analytics: MonetisationAnalytics
) {
    /**
     * Reports what a purchase update means for [launchedProductId], the product whose sheet was open. A user who
     * backs out is a cancel; a purchase waiting on payment is pending; any other refusal is an error. A product
     * the user already owns is not a failure: the owned products are refreshed instead.
     */
    fun onPurchasesUpdated(
        responseCode: Int,
        purchases: List<Purchase>,
        launchedProductId: String?
    ) {
        when (responseCode) {
            BillingClient.BillingResponseCode.OK ->
                purchases
                    .filter { it.purchaseState == Purchase.PurchaseState.PENDING }
                    .flatMap { it.products }
                    .forEach { analytics.purchaseFailed(it, PurchaseFailureReason.Pending) }

            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> Unit

            BillingClient.BillingResponseCode.USER_CANCELED ->
                launchedProductId?.let { analytics.purchaseFailed(it, PurchaseFailureReason.Cancelled) }

            else -> launchedProductId?.let { analytics.purchaseFailed(it, PurchaseFailureReason.Failed) }
        }
    }
}

/** The owned Pro product a restore reports: the one granting the best [com.simplecityapps.shuttle.entitlement.ProSource]. */
internal fun Set<String>.restoredProductId(): String? = filter { ProductIds.proSource(it) != null }.minByOrNull { ProductIds.proSource(it)!! }
