package com.simplecityapps.trial

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.ProductDetails

/**
 * One thing the paywall can sell: a one-time product, or one base plan of a subscription.
 *
 * @param offerToken the base plan's offer token, which Play requires to buy a subscription; null for a one-time product.
 */
data class PaywallOffer(
    val productDetails: ProductDetails,
    val offerToken: String?,
    val formattedPrice: String,
    /** ISO 8601 billing period of a subscription base plan (e.g. `P1M`, `P1Y`); null for a one-time product. */
    val billingPeriod: String?
) {
    val productId: String get() = productDetails.productId

    val isSubscription: Boolean get() = productDetails.productType == BillingClient.ProductType.SUBS
}

/**
 * The offers for [this] product details: the S2 Pro products if Play returned any, otherwise the legacy ones.
 * Subscriptions come first, one offer per base plan, annual before monthly.
 */
internal fun List<ProductDetails>.toPaywallOffers(): List<PaywallOffer> {
    val pro = filter { it.productId == ProductIds.PRO_SUBSCRIPTION || it.productId == ProductIds.PRO_LIFETIME }
    return pro.ifEmpty { filter { it.productId in ProductIds.legacyOffered } }
        .sortedByDescending { it.productType } // "subs" before "inapp"
        .flatMap { details ->
            details.oneTimePurchaseOfferDetails?.let { oneTime ->
                listOf(PaywallOffer(details, offerToken = null, formattedPrice = oneTime.formattedPrice, billingPeriod = null))
            } ?: details.subscriptionOfferDetails.orEmpty()
                // Base plans only; developer-defined offers (offerId != null) aren't sold here.
                .filter { it.offerId == null }
                .sortedByDescending { it.basePlanId == ProductIds.PRO_BASE_PLAN_ANNUAL }
                .mapNotNull { offer ->
                    val phase = offer.pricingPhases.pricingPhaseList.lastOrNull() ?: return@mapNotNull null
                    PaywallOffer(details, offer.offerToken, phase.formattedPrice, phase.billingPeriod)
                }
        }
}
