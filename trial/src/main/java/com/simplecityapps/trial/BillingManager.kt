package com.simplecityapps.trial

import android.content.Context
import androidx.fragment.app.FragmentActivity
import com.android.billingclient.api.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Named

class BillingManager(
    context: Context,
    @Named("AppCoroutineScope") private val coroutineScope: CoroutineScope
) {

    interface Listener {
        fun onBillingClientAvailable()
    }

    private var retryDelay = 1000L

    private var listeners: MutableSet<Listener> = mutableSetOf()

    fun addListener(listener: Listener) {
        listeners.add(listener)
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    private val paidVersionSkus = listOf(
        "s2_subscription_full_version_monthly",
        "s2_subscription_full_version_yearly",
        "s2_iap_full_version"
    )

    val skuDetails: MutableStateFlow<Set<SkuDetails>> = MutableStateFlow(emptySet())

    val billingState: MutableStateFlow<BillingState> = MutableStateFlow(BillingState.Unknown)

    init {
        billingState
            .onEach { Timber.i("billingState changed to $it") }
            .launchIn(coroutineScope)
    }

    private val billingClientStateListener = object : BillingClientStateListener {
        override fun onBillingServiceDisconnected() {
            coroutineScope.launch {
                delay(retryDelay)
                start()
                retryDelay *= 2
            }
        }

        override fun onBillingSetupFinished(billingResult: BillingResult) {
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                listeners.forEach { it.onBillingClientAvailable() }
            } else {
                Timber.e("onBillingSetupFinished (code: ${billingResult.responseCode}, message: ${billingResult.debugMessage})")
            }
        }
    }

    private val purchasesUpdatedListener =
        PurchasesUpdatedListener { billingResult, purchases ->
            when (billingResult.responseCode) {
                BillingClient.BillingResponseCode.OK -> {
                    processPurchases(purchases.orEmpty())
                }
                BillingClient.BillingResponseCode.USER_CANCELED -> {
                    Timber.i("onPurchasesUpdated: User canceled the purchase")
                }
                BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                    Timber.i("onPurchasesUpdated: The user already owns this item")
                }
                BillingClient.BillingResponseCode.DEVELOPER_ERROR -> {
                    Timber.e("onPurchasesUpdated: Developer error means that Google Play does not recognize the configuration. If you are just getting started, make sure you have configured the application correctly in the Google Play Console. The SKU product ID must match and the APK you are using must be signed with release keys.")
                }
            }
        }

    private val billingClient = BillingClient.newBuilder(context)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases()
        .build()

    fun start() {
        billingClient.startConnection(billingClientStateListener)
    }

    fun launchPurchaseFlow(activity: FragmentActivity, skuDetails: SkuDetails) {
        billingClient.launchBillingFlow(activity, BillingFlowParams.newBuilder().setSkuDetails(skuDetails).build())
    }

    suspend fun querySkuDetails() {
        val inAppPurchaseDetails = SkuDetailsParams.newBuilder()
            .setSkusList(
                listOf(
                    "s2_iap_full_version"
                )
            )
            .setType(BillingClient.SkuType.INAPP)
            .build()

        val subscriptionDetails = SkuDetailsParams.newBuilder()
            .setSkusList(
                listOf(
                    "s2_subscription_full_version_monthly",
                    "s2_subscription_full_version_yearly"
                )
            )
            .setType(BillingClient.SkuType.SUBS)
            .build()

        listOf(inAppPurchaseDetails, subscriptionDetails).forEach { skuDetailsParams ->
            val skuDetailsResult = billingClient.querySkuDetails(skuDetailsParams)
            when (skuDetailsResult.billingResult.responseCode) {
                BillingClient.BillingResponseCode.OK -> {
                    skuDetails.value = (skuDetails.value + skuDetailsResult.skuDetailsList.orEmpty())
                        .sortedByDescending { skuDetails -> skuDetails.type } // Show subs first
                        .toSet()
                }
                BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
                BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
                BillingClient.BillingResponseCode.BILLING_UNAVAILABLE,
                BillingClient.BillingResponseCode.ITEM_UNAVAILABLE,
                BillingClient.BillingResponseCode.DEVELOPER_ERROR,
                BillingClient.BillingResponseCode.ERROR -> {
                    Timber.e("onSkuDetailsResponse: ${skuDetailsResult.billingResult.responseCode} ${skuDetailsResult.billingResult.debugMessage}")
                }
                BillingClient.BillingResponseCode.USER_CANCELED,
                BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED,
                BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED,
                BillingClient.BillingResponseCode.ITEM_NOT_OWNED -> {
                    // These response codes are not expected.
                    Timber.e("onSkuDetailsResponse: ${skuDetailsResult.billingResult.responseCode} ${skuDetailsResult.billingResult.debugMessage}")
                }
            }
        }
    }

    fun queryPurchases() {
        val purchaseResponseListener = PurchasesResponseListener { _, purchases ->
            processPurchases(purchases)
        }
        billingClient.queryPurchasesAsync(BillingClient.SkuType.INAPP, purchaseResponseListener)
        billingClient.queryPurchasesAsync(BillingClient.SkuType.SUBS, purchaseResponseListener)
    }

    private fun processPurchases(purchases: List<Purchase>) {
        Timber.i("processPurchases (purchases: ${purchases.size})")
        if (paidVersionSkus.intersect(purchases
                .flatMap { purchase -> purchase.skus })
                .isNotEmpty()
        ) {
            billingState.value = BillingState.Paid
        } else {
            billingState.value = BillingState.Unpaid
        }

        purchases
            .filterNot { purchase -> purchase.isAcknowledged }
            .forEach {
                acknowledgePurchase(it.purchaseToken)
            }
    }

    private fun acknowledgePurchase(purchaseToken: String) {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchaseToken)
            .build()
        billingClient.acknowledgePurchase(params) { billingResult ->
            val responseCode = billingResult.responseCode
            val debugMessage = billingResult.debugMessage
            Timber.d("acknowledgePurchase: $responseCode $debugMessage")
        }
    }
}