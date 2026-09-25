package com.simplecityapps.trial

import com.android.billingclient.api.ProductDetails

/** How a [PaywallOffer] is paid for. */
enum class PaywallPlan {
    Lifetime,
    Annual
}

/**
 * One thing the paywall can sell: a one-time product, or one base plan of a subscription.
 *
 * @param offerToken the base plan's offer token, which Play requires to buy a subscription; null for a one-time product.
 */
data class PaywallOffer(
    val productId: String,
    val plan: PaywallPlan,
    val formattedPrice: String,
    val offerToken: String?
) {
    val isSubscription: Boolean get() = plan != PaywallPlan.Lifetime
}

/**
 * The offers for [this] product details: the S2 Pro products if Play returned any, otherwise the legacy ones still
 * on sale. One offer per one-time product and per annual subscription base plan, lifetime first.
 */
internal fun List<ProductDetails>.toPaywallOffers(): List<PaywallOffer> {
    val pro = filter { it.productId == ProductIds.PRO_SUBSCRIPTION || it.productId == ProductIds.PRO_LIFETIME }
    return pro.ifEmpty { filter { it.productId in ProductIds.legacyOffered } }
        .flatMap { details ->
            details.oneTimePurchaseOfferDetails?.let { oneTime ->
                listOf(PaywallOffer(details.productId, PaywallPlan.Lifetime, oneTime.formattedPrice, offerToken = null))
            } ?: details.subscriptionOfferDetails.orEmpty()
                // Base plans only; developer-defined offers (offerId != null) aren't sold here.
                .filter { it.offerId == null }
                .mapNotNull { offer ->
                    val phase = offer.pricingPhases.pricingPhaseList.lastOrNull() ?: return@mapNotNull null
                    if (phase.billingPeriod != "P1Y") return@mapNotNull null
                    PaywallOffer(details.productId, PaywallPlan.Annual, phase.formattedPrice, offer.offerToken)
                }
        }
        .sortedBy { it.plan }
}
