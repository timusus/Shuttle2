package com.simplecityapps.trial

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.simplecityapps.shuttle.di.AppCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/** [Billing] backed by the Play Billing library. */
class PlayBilling(
    context: Context,
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
    private val analytics: MonetisationAnalytics
) : Billing {
    private var retryDelay = INITIAL_RETRY_DELAY

    private val _ownedProductIds = MutableStateFlow<Set<String>?>(null)

    override val ownedProductIds: StateFlow<Set<String>?> = _ownedProductIds.asStateFlow()

    private val _offers = MutableStateFlow<PaywallOffers>(PaywallOffers.Loading)

    /** The S2 Pro products once they exist in Play, otherwise the legacy ones still on sale. */
    override val offers: StateFlow<PaywallOffers> = _offers.asStateFlow()

    /** The details Play returned for each product on sale, which the purchase flow needs. */
    private var productDetails: Map<String, ProductDetails> = emptyMap()

    private val billingClientStateListener =
        object : BillingClientStateListener {
            override fun onBillingServiceDisconnected() {
                coroutineScope.launch {
                    delay(retryDelay)
                    retryDelay = (retryDelay * 2).coerceAtMost(MAX_RETRY_DELAY)
                    start()
                }
            }

            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    retryDelay = INITIAL_RETRY_DELAY
                    queryPurchases()
                    coroutineScope.launch { queryProductDetails() }
                } else {
                    Timber.e("onBillingSetupFinished (code: ${billingResult.responseCode}, message: ${billingResult.debugMessage})")
                    _offers.value = PaywallOffers.Unavailable
                }
            }
        }

    private val purchasesUpdatedListener =
        PurchasesUpdatedListener { billingResult, purchases ->
            when (billingResult.responseCode) {
                BillingClient.BillingResponseCode.OK -> {
                    Timber.v("onPurchasesUpdated: found ${purchases.orEmpty().size} purchases")
                    onPurchasesUpdated(purchases.orEmpty())
                }

                BillingClient.BillingResponseCode.USER_CANCELED -> {
                    Timber.v("onPurchasesUpdated: User canceled the purchase")
                }

                BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                    Timber.v("onPurchasesUpdated: The user already owns this item")
                    queryPurchases()
                }

                BillingClient.BillingResponseCode.DEVELOPER_ERROR -> {
                    Timber.e("onPurchasesUpdated: Developer error means that Google Play does not recognize the configuration. If you are just getting started, make sure you have configured the application correctly in the Google Play Console. The SKU product ID must match and the APK you are using must be signed with release keys.")
                }
            }
        }

    private val billingClient =
        BillingClient.newBuilder(context)
            .setListener(purchasesUpdatedListener)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .build()
            )
            .build()

    override fun start() {
        billingClient.startConnection(billingClientStateListener)
    }

    override fun refreshOffers() {
        _offers.value = PaywallOffers.Loading
        if (billingClient.isReady) {
            coroutineScope.launch { queryProductDetails() }
        } else {
            // Setup loads the offers once it connects, or reports them unavailable.
            start()
        }
    }

    override fun launchPurchaseFlow(
        activity: Activity,
        offer: PaywallOffer
    ): Boolean {
        val details = productDetails[offer.productId]
        if (!billingClient.isReady || details == null) {
            Timber.e("Failed to launch purchase flow: BillingClient not ready, or no details for ${offer.productId}")
            return false
        }

        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .apply { offer.offerToken?.let { setOfferToken(it) } }
            .build()

        val result = billingClient.launchBillingFlow(
            activity,
            BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(productDetailsParams))
                .build()
        )
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            Timber.e("launchBillingFlow failed (code: ${result.responseCode}, message: ${result.debugMessage})")
            return false
        }
        analytics.purchaseStarted(offer.productId)
        return true
    }

    private suspend fun queryProductDetails() {
        val offered = listOf(ProductIds.PRO_SUBSCRIPTION, ProductIds.PRO_LIFETIME) + ProductIds.legacyOffered
        val details = listOf(BillingClient.ProductType.SUBS, BillingClient.ProductType.INAPP).flatMap { productType ->
            val productList = offered
                .filter { productId -> (productId in ProductIds.subscriptions) == (productType == BillingClient.ProductType.SUBS) }
                .map { productId ->
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(productId)
                        .setProductType(productType)
                        .build()
                }
            val result = billingClient.queryProductDetails(QueryProductDetailsParams.newBuilder().setProductList(productList).build())
            if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                result.productDetailsList.orEmpty()
            } else {
                Timber.e("queryProductDetails: ${result.billingResult.responseCode} ${result.billingResult.debugMessage}")
                emptyList()
            }
        }
        productDetails = details.associateBy { it.productId }
        val offers = details.toPaywallOffers()
        _offers.value = if (offers.isEmpty()) PaywallOffers.Unavailable else PaywallOffers.Available(offers)
    }

    override fun queryPurchases() {
        coroutineScope.launch { queryOwnedProducts() }
    }

    override suspend fun restorePurchases(): RestoreResult {
        val owned = queryOwnedProducts() ?: return RestoreResult.Failed
        return if (owned.proSource() != null) RestoreResult.Restored else RestoreResult.NothingToRestore
    }

    /** Asks Play for every owned product and publishes them. Returns null if Play couldn't answer for every product type. */
    private suspend fun queryOwnedProducts(): Set<String>? {
        if (!billingClient.isReady) {
            // If the billing client isn't ready, querying it potentially crashes the billing service.
            // Setup calls this again once it's ready.
            return null
        }
        val productTypes = listOf(BillingClient.ProductType.INAPP, BillingClient.ProductType.SUBS)
        val pass = OwnedProductsQuery(productTypes.toSet())
        var outcome: OwnedProductsQuery.Outcome = OwnedProductsQuery.Outcome.Pending
        productTypes.forEach { productType ->
            val params = QueryPurchasesParams.newBuilder()
                .setProductType(productType)
                .build()
            val result = billingClient.queryPurchasesAsync(params)
            val purchased = if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                acknowledge(result.purchasesList)
                result.purchasesList.purchasedProductIds()
            } else {
                Timber.e("queryPurchasesAsync($productType): ${result.billingResult.responseCode} ${result.billingResult.debugMessage}")
                null
            }
            outcome = pass.onResult(productType, purchased)
        }
        return onQueryResult(outcome)
    }

    @Synchronized
    private fun onQueryResult(outcome: OwnedProductsQuery.Outcome): Set<String>? = when (outcome) {
        is OwnedProductsQuery.Outcome.Complete -> outcome.owned.also { _ownedProductIds.value = it }

        is OwnedProductsQuery.Outcome.Failed -> {
            Timber.e("queryPurchases: ${outcome.failedTypes} failed; keeping the last owned products (${_ownedProductIds.value})")
            null
        }

        OwnedProductsQuery.Outcome.Pending -> null
    }

    @Synchronized
    private fun onPurchasesUpdated(purchases: List<Purchase>) {
        val purchased = purchases.purchasedProductIds()
        purchased.forEach { analytics.purchaseCompleted(it) }
        if (purchased.isNotEmpty()) {
            _ownedProductIds.value = _ownedProductIds.value.orEmpty() + purchased
        }
        acknowledge(purchases)
    }

    private fun acknowledge(purchases: List<Purchase>) {
        purchases.needingAcknowledgement().forEach { purchase ->
            val params =
                AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
            billingClient.acknowledgePurchase(params) { billingResult ->
                Timber.d("acknowledgePurchase: ${billingResult.responseCode} ${billingResult.debugMessage}")
            }
        }
    }

    companion object {
        private const val INITIAL_RETRY_DELAY = 1000L
        private const val MAX_RETRY_DELAY = 60_000L
    }
}

/**
 * One pass of owned-product queries, one query per product type. Once every type has answered it yields the owned
 * products, or, if any query failed, a failure: a partial set could drop a product the user owns and revoke Pro.
 */
internal class OwnedProductsQuery(private val productTypes: Set<String>) {
    sealed interface Outcome {
        data object Pending : Outcome

        data class Complete(val owned: Set<String>) : Outcome

        data class Failed(val failedTypes: Set<String>) : Outcome
    }

    private val results = mutableMapOf<String, Set<String>?>()

    /** Records [productType]'s purchased products, or null if its query failed. */
    @Synchronized
    fun onResult(
        productType: String,
        purchased: Set<String>?
    ): Outcome {
        results[productType] = purchased
        if (!results.keys.containsAll(productTypes)) return Outcome.Pending
        val failed = results.filterValues { it == null }.keys
        return if (failed.isEmpty()) Outcome.Complete(results.values.flatMap { it.orEmpty() }.toSet()) else Outcome.Failed(failed)
    }
}

/**
 * Products with a completed purchase. A PENDING purchase (e.g. a cash payment that hasn't been made yet) grants
 * nothing; Play calls the purchases listener again once it completes.
 */
internal fun List<Purchase>.purchasedProductIds(): Set<String> = filter { purchase -> purchase.purchaseState == Purchase.PurchaseState.PURCHASED }
    .flatMap { purchase -> purchase.products }
    .toSet()

/**
 * Completed purchases that haven't been acknowledged yet. Only PURCHASED purchases can be acknowledged;
 * Play refunds any that stay unacknowledged for three days.
 */
internal fun List<Purchase>.needingAcknowledgement(): List<Purchase> = filter { purchase ->
    purchase.purchaseState == Purchase.PurchaseState.PURCHASED && !purchase.isAcknowledged
}
