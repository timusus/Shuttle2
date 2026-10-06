package com.simplecityapps.trial

import com.android.billingclient.api.Purchase
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PurchaseEntitlementTest {
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
    fun `a completed purchase counts as owned`() {
        assertEquals(setOf("s2_iap_full_version"), listOf(purchase()).purchasedProductIds())
    }

    @Test
    fun `a pending purchase does not count as owned`() {
        assertTrue(listOf(purchase(state = Purchase.PurchaseState.PENDING)).purchasedProductIds().isEmpty())
    }

    @Test
    fun `a purchase in an unspecified state does not count as owned`() {
        assertTrue(listOf(purchase(state = Purchase.PurchaseState.UNSPECIFIED_STATE)).purchasedProductIds().isEmpty())
    }

    @Test
    fun `a pending purchase alongside a completed one owns only the completed one`() {
        val purchases = listOf(
            purchase(productId = "s2_subscription_full_version_yearly", state = Purchase.PurchaseState.PENDING),
            purchase()
        )
        assertEquals(setOf("s2_iap_full_version"), purchases.purchasedProductIds())
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

    @Test
    fun `only pending Pro purchases count as pending`() {
        val purchases = listOf(
            purchase(productId = "s2_pro_lifetime", state = Purchase.PurchaseState.PENDING),
            purchase(productId = "s2_subscription_full_version_yearly", state = Purchase.PurchaseState.PENDING),
            purchase(productId = "something_else", state = Purchase.PurchaseState.PENDING),
            purchase(productId = "s2_pro")
        )
        assertEquals(setOf("s2_pro_lifetime", "s2_subscription_full_version_yearly"), purchases.pendingProProductIds())
    }

    @Test
    fun `a restore that finds Pro restores it, even with another purchase pending`() {
        assertEquals(RestoreResult.Restored, restoreResult(owned = setOf("s2_iap_full_version"), pending = setOf("s2_pro")))
    }

    @Test
    fun `a restore that finds only a pending purchase says it is pending`() {
        assertEquals(RestoreResult.Pending, restoreResult(owned = emptySet(), pending = setOf("s2_pro_lifetime")))
    }

    @Test
    fun `a restore that finds nothing says so`() {
        assertEquals(RestoreResult.NothingToRestore, restoreResult(owned = setOf("something_else"), pending = emptySet()))
    }
}
