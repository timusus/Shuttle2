package com.simplecityapps.trial

import android.content.Context
import com.android.billingclient.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BillingManager(private val context: Context) {

    private val purchasesUpdatedListener =
        PurchasesUpdatedListener { billingResult, purchases ->
            // To be implemented in a later section.
        }

    private var billingClient = BillingClient.newBuilder(context)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases()
        .build()

    fun start() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingServiceDisconnected() {
                TODO("Not yet implemented")
            }

            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    // The BillingClient is setup successfully
                }
            }

        })
    }

    suspend fun querySkuDetails() {
        val params1 = SkuDetailsParams.newBuilder()
        params1.setSkusList(listOf("premium_one_time")).setType(BillingClient.SkuType.INAPP)

        val params2 = SkuDetailsParams.newBuilder()
        params2.setSkusList(listOf("premium_sub_1", "premium_sub_2")).setType(BillingClient.SkuType.SUBS)


        // leverage querySkuDetails Kotlin extension function
        val skuDetailsResult = withContext(Dispatchers.IO) {
            billingClient.querySkuDetails(params1.build())
        }

        if (skuDetailsResult.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {

        } else {
            // Todo: Handle failure
        }


        // Process the result.
    }
}