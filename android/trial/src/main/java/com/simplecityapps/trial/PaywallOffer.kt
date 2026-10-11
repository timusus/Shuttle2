package com.simplecityapps.trial

import com.android.billingclient.api.ProductDetails

/** The one thing the paywall sells: the lifetime unlock, at Play's [formattedPrice] for the user's region. */
data class PaywallOffer(
    val productId: String,
    val formattedPrice: String
)

/** The offer for [this] product details, from the [ProductIds.offered] product only; null if Play didn't return it. */
internal fun List<ProductDetails>.toPaywallOffer(): PaywallOffer? = firstOrNull { it.productId in ProductIds.offered }
    ?.let { details -> details.oneTimePurchaseOfferDetails?.let { PaywallOffer(details.productId, it.formattedPrice) } }
