package com.simplecityapps.trial

import com.android.billingclient.api.ProductDetails
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class PaywallOffersTest {
    private fun oneTime(
        productId: String,
        price: String
    ): ProductDetails = mockk {
        every { this@mockk.productId } returns productId
        every { oneTimePurchaseOfferDetails } returns mockk { every { formattedPrice } returns price }
        every { subscriptionOfferDetails } returns null
    }

    private fun basePlan(
        period: String,
        price: String,
        offerId: String? = null
    ): ProductDetails.SubscriptionOfferDetails = mockk {
        every { this@mockk.offerId } returns offerId
        every { offerToken } returns "token-$period-$offerId"
        every { pricingPhases.pricingPhaseList } returns listOf(
            mockk {
                every { billingPeriod } returns period
                every { formattedPrice } returns price
            }
        )
    }

    private fun subscription(
        productId: String,
        vararg plans: ProductDetails.SubscriptionOfferDetails
    ): ProductDetails = mockk {
        every { this@mockk.productId } returns productId
        every { oneTimePurchaseOfferDetails } returns null
        every { subscriptionOfferDetails } returns plans.toList()
    }

    @Test
    fun `offers the S2 Pro products lifetime first, one offer per base plan`() {
        val offers = listOf(
            subscription(ProductIds.PRO_SUBSCRIPTION, basePlan("P1M", "$1.49"), basePlan("P1Y", "$5.99"), basePlan("P1Y", "$2.99", offerId = "intro")),
            oneTime(ProductIds.PRO_LIFETIME, "$14.99"),
            oneTime(ProductIds.LEGACY_LIFETIME_LOW, "$4.99")
        ).toPaywallOffers()

        assertEquals(
            listOf(
                PaywallOffer(ProductIds.PRO_LIFETIME, PaywallPlan.Lifetime, "$14.99", offerToken = null),
                PaywallOffer(ProductIds.PRO_SUBSCRIPTION, PaywallPlan.Annual, "$5.99", "token-P1Y-null"),
                PaywallOffer(ProductIds.PRO_SUBSCRIPTION, PaywallPlan.Monthly, "$1.49", "token-P1M-null")
            ),
            offers
        )
    }

    @Test
    fun `falls back to the legacy products until the S2 Pro products exist in Play`() {
        val offers = listOf(
            oneTime(ProductIds.LEGACY_LIFETIME_LOW, "$4.99"),
            subscription(ProductIds.LEGACY_SUBSCRIPTION_YEARLY_LOW, basePlan("P1Y", "$2.99"))
        ).toPaywallOffers()

        assertEquals(listOf(PaywallPlan.Lifetime, PaywallPlan.Annual), offers.map { it.plan })
        assertEquals(listOf(ProductIds.LEGACY_LIFETIME_LOW, ProductIds.LEGACY_SUBSCRIPTION_YEARLY_LOW), offers.map { it.productId })
    }

    @Test
    fun `offers nothing when Play returns no products`() {
        assertEquals(emptyList<PaywallOffer>(), emptyList<ProductDetails>().toPaywallOffers())
    }
}
