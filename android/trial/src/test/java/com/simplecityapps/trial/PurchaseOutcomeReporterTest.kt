package com.simplecityapps.trial

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.Purchase
import com.simplecityapps.shuttle.analytics.MonetisationAnalytics
import com.simplecityapps.shuttle.analytics.PurchaseFailureReason
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PurchaseOutcomeReporterTest {
    private val analytics = mockk<MonetisationAnalytics>(relaxed = true)
    private val reporter = PurchaseOutcomeReporter(analytics)

    private fun purchase(
        productId: String,
        state: Int
    ): Purchase = mockk {
        every { products } returns listOf(productId)
        every { purchaseState } returns state
    }

    @Test
    fun `backing out of the sheet is a cancel`() {
        reporter.onPurchasesUpdated(BillingClient.BillingResponseCode.USER_CANCELED, emptyList(), ProductIds.PRO_LIFETIME)
        verify(exactly = 1) { analytics.purchaseFailed(ProductIds.PRO_LIFETIME, PurchaseFailureReason.Cancelled) }
    }

    @Test
    fun `a refusal from Play is an error`() {
        reporter.onPurchasesUpdated(BillingClient.BillingResponseCode.ERROR, emptyList(), ProductIds.PRO_SUBSCRIPTION)
        verify(exactly = 1) { analytics.purchaseFailed(ProductIds.PRO_SUBSCRIPTION, PurchaseFailureReason.Failed) }
    }

    @Test
    fun `a cancel or error with no launched product has nothing to report against`() {
        reporter.onPurchasesUpdated(BillingClient.BillingResponseCode.USER_CANCELED, emptyList(), null)
        reporter.onPurchasesUpdated(BillingClient.BillingResponseCode.ERROR, emptyList(), null)
        verify(exactly = 0) { analytics.purchaseFailed(any(), any()) }
    }

    @Test
    fun `a purchase waiting on payment is pending`() {
        reporter.onPurchasesUpdated(
            BillingClient.BillingResponseCode.OK,
            listOf(purchase(ProductIds.PRO_LIFETIME, Purchase.PurchaseState.PENDING)),
            ProductIds.PRO_LIFETIME
        )
        verify(exactly = 1) { analytics.purchaseFailed(ProductIds.PRO_LIFETIME, PurchaseFailureReason.Pending) }
    }

    @Test
    fun `a completed purchase and an already owned product are not failures`() {
        reporter.onPurchasesUpdated(
            BillingClient.BillingResponseCode.OK,
            listOf(purchase(ProductIds.PRO_LIFETIME, Purchase.PurchaseState.PURCHASED)),
            ProductIds.PRO_LIFETIME
        )
        reporter.onPurchasesUpdated(BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED, emptyList(), ProductIds.PRO_LIFETIME)
        verify(exactly = 0) { analytics.purchaseFailed(any(), any()) }
    }

    @Test
    fun `a restore reports the best Pro product owned`() {
        assertEquals(ProductIds.PRO_SUBSCRIPTION, setOf("unrelated", ProductIds.PRO_SUBSCRIPTION).restoredProductId())
        assertNull(setOf("unrelated").restoredProductId())
    }
}
