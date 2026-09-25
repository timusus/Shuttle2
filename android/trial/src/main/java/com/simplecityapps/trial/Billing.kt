package com.simplecityapps.trial

import android.app.Activity
import kotlinx.coroutines.flow.StateFlow

/** Play Billing as the rest of the app sees it: owned products, the products on sale, restore and purchase. */
interface Billing {
    /** Product IDs with a completed purchase, or null until Play has answered. */
    val ownedProductIds: StateFlow<Set<String>?>

    /** What the paywall can sell. */
    val offers: StateFlow<PaywallOffers>

    /** Connects to Play, then loads owned products and the products on sale. */
    fun start()

    /** Refreshes owned products. Call whenever the app comes to the foreground. */
    fun queryPurchases()

    /** Loads the products on sale again, after they failed to load. */
    fun refreshOffers()

    /** Asks Play for the user's purchases now and reports whether any of them grants Pro. */
    suspend fun restorePurchases(): RestoreResult

    /** Opens Play's purchase sheet for [offer]. Returns false if it couldn't be opened. */
    fun launchPurchaseFlow(
        activity: Activity,
        offer: PaywallOffer
    ): Boolean
}

/** The products on sale, as Play last reported them. */
sealed interface PaywallOffers {
    data object Loading : PaywallOffers

    /** Play is unreachable, or returned no products. */
    data object Unavailable : PaywallOffers

    data class Available(val offers: List<PaywallOffer>) : PaywallOffers
}

enum class RestoreResult {
    /** A purchase that grants Pro was found. */
    Restored,

    /** Play answered, and none of the user's purchases grants Pro. */
    NothingToRestore,

    /** Play couldn't be reached. */
    Failed
}
