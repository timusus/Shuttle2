package com.simplecityapps.trial

import com.android.billingclient.api.Purchase
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PurchaseEntitlementTest {
    private val paidProductIds = listOf("s2_iap_full_version", "s2_subscription_full_version_yearly")

    private fun purchase(
        productId: String = "s2_iap_full_version",
        state: Int = Purchase.PurchaseState.PURCHASED,
        acknowledged: Boolean = false,
        token: String = "token"
    ): Purchase = mockk {
        every { products } returns listOf(productId)
        every { purchaseState } returns state
        every { isAcknowledged } returns acknowledged
        every { purchaseToken } returns token
    }

    @Test
    fun `a completed purchase of a paid product grants the paid version`() {
        assertTrue(listOf(purchase()).grantPaidVersion(paidProductIds))
    }

    @Test
    fun `a pending purchase does not grant the paid version`() {
        assertFalse(listOf(purchase(state = Purchase.PurchaseState.PENDING)).grantPaidVersion(paidProductIds))
    }

    @Test
    fun `a purchase in an unspecified state does not grant the paid version`() {
        assertFalse(listOf(purchase(state = Purchase.PurchaseState.UNSPECIFIED_STATE)).grantPaidVersion(paidProductIds))
    }

    @Test
    fun `a completed purchase of an unknown product does not grant the paid version`() {
        assertFalse(listOf(purchase(productId = "something_else")).grantPaidVersion(paidProductIds))
    }

    @Test
    fun `a pending purchase alongside a completed one still grants the paid version`() {
        val purchases = listOf(
            purchase(productId = "s2_subscription_full_version_yearly", state = Purchase.PurchaseState.PENDING),
            purchase()
        )
        assertTrue(purchases.grantPaidVersion(paidProductIds))
    }

    @Test
    fun `only unacknowledged completed purchases need acknowledging`() {
        val purchases = listOf(
            purchase(token = "completed"),
            purchase(token = "pending", state = Purchase.PurchaseState.PENDING),
            purchase(token = "acknowledged", acknowledged = true)
        )
        assertEquals(listOf("completed"), purchases.needingAcknowledgement().map { it.purchaseToken })
    }
}
