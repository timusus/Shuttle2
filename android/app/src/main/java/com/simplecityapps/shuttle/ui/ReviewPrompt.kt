package com.simplecityapps.shuttle.ui

import com.simplecityapps.shuttle.entitlement.Entitlement
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import dev.zacsweers.metro.Inject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * When to ask for a Play review: a week after S2 Pro was first seen, then at most every 30 days
 * (redesign-inventory.md §8).
 */
class ReviewPrompt(
    private val preferenceManager: GeneralPreferenceManager,
    private val now: () -> Instant
) {
    @Inject
    constructor(preferenceManager: GeneralPreferenceManager) : this(preferenceManager, { Clock.System.now() })

    /** Records the first time the user is seen with Pro as the purchase date. */
    fun onEntitlement(entitlement: Entitlement) {
        if (entitlement is Entitlement.Pro && preferenceManager.appPurchasedDate == null) {
            preferenceManager.appPurchasedDate = now()
        }
    }

    /** True if a review should be asked for now, recording that it was. */
    fun takeIfDue(): Boolean {
        val now = now()
        val purchased = preferenceManager.appPurchasedDate ?: return false
        if (purchased >= now - 7.days) return false
        val lastAsked = preferenceManager.lastViewedRatingFlow
        if (lastAsked != null && lastAsked >= now - 30.days) return false
        preferenceManager.lastViewedRatingFlow = now
        return true
    }
}
