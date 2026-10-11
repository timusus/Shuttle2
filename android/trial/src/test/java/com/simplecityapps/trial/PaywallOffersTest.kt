package com.simplecityapps.trial

import com.android.billingclient.api.ProductDetails
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    private fun subscription(productId: String): ProductDetails = mockk {
        every { this@mockk.productId } returns productId
        every { oneTimePurchaseOfferDetails } returns null
        every { subscriptionOfferDetails } returns emptyList()
    }

    @Test
    fun `offers only the lifetime unlock`() {
        val offer = listOf(
            subscription(ProductIds.PRO_SUBSCRIPTION),
            oneTime(ProductIds.PRO_LIFETIME, "$9.99"),
            oneTime(ProductIds.LEGACY_LIFETIME_LOW, "$4.99")
        ).toPaywallOffer()

        assertEquals(PaywallOffer(ProductIds.PRO_LIFETIME, "$9.99"), offer)
    }

    @Test
    fun `never offers the annual subscription or a legacy product, even when Play returns only those`() {
        val offer = listOf(
            subscription(ProductIds.PRO_SUBSCRIPTION),
            oneTime(ProductIds.LEGACY_LIFETIME, "$3.49"),
            oneTime(ProductIds.LEGACY_LIFETIME_LOW, "$7.99"),
            subscription(ProductIds.LEGACY_SUBSCRIPTION_MONTHLY),
            subscription(ProductIds.LEGACY_SUBSCRIPTION_YEARLY),
            subscription(ProductIds.LEGACY_SUBSCRIPTION_YEARLY_LOW)
        ).toPaywallOffer()

        assertNull(offer)
    }

    @Test
    fun `offers nothing when Play returns no products`() {
        assertNull(emptyList<ProductDetails>().toPaywallOffer())
    }
}
