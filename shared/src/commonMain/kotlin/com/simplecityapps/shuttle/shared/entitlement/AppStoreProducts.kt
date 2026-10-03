package com.simplecityapps.shuttle.shared.entitlement

import com.simplecityapps.shuttle.entitlement.ProSource

/**
 * The iOS app's App Store products, both non-consumable: the free 14-day server trial, and Shuttle Music Pro for life.
 * Swift's `StoreKitManager` loads and buys them; [StoreEntitlements] resolves what owning them grants.
 */
object AppStoreProducts {
    /**
     * Free (price tier 0). Buying it starts the server trial, which runs [com.simplecityapps.shuttle.entitlement.Entitlement.TRIAL_LENGTH]
     * from the transaction's purchase date. The App Store keeps the transaction, so the trial survives a reinstall and
     * is had once per Apple ID.
     */
    const val TRIAL = "com.simplecityapps.shuttle.pro.trial"

    /** Shuttle Music Pro, a one-time purchase. */
    const val LIFETIME = "com.simplecityapps.shuttle.pro.lifetime"

    val all: List<String> = listOf(TRIAL, LIFETIME)

    /** Where Pro comes from when [productId] is owned, or null if it doesn't grant Pro. */
    fun proSource(productId: String): ProSource? = when (productId) {
        LIFETIME -> ProSource.Lifetime
        else -> null
    }
}
