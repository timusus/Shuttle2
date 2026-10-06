package com.simplecityapps.shuttle.shared.entitlement

import com.simplecityapps.shuttle.entitlement.ProSource
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/**
 * The iOS app's App Store products, both non-consumable: the free server trial, and Shuttle Music Pro for life.
 * Swift's `StoreKitManager` loads and buys them; [StoreEntitlements] resolves what owning them grants.
 */
object AppStoreProducts {
    /** How many days the iOS trial runs; the one place the length is defined (Swift's copy reads it too). */
    const val TRIAL_DAYS = 14

    val TRIAL_LENGTH: Duration = TRIAL_DAYS.days

    /**
     * Free (price tier 0). Buying it starts the server trial, which runs [TRIAL_LENGTH]
     * from the transaction's original purchase date. The App Store keeps the transaction, and a restore or reinstall
     * reports the same original date, so the trial is had once per Apple ID.
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
