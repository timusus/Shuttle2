package com.simplecityapps.shuttle.ui

import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.trial.Entitlement
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * When to ask for a Play review: a week after S2 Pro was first seen, then at most every 30 days
 * (redesign-inventory.md §8).
 */
class ReviewPrompt(
    private val preferenceManager: GeneralPreferenceManager,
    private val now: () -> Date
) {
    @Inject
    constructor(preferenceManager: GeneralPreferenceManager) : this(preferenceManager, ::Date)

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
        if (!purchased.before(now.minusDays(7))) return false
        val lastAsked = preferenceManager.lastViewedRatingFlow
        if (lastAsked != null && !lastAsked.before(now.minusDays(30))) return false
        preferenceManager.lastViewedRatingFlow = now
        return true
    }

    private fun Date.minusDays(days: Long) = Date(time - TimeUnit.DAYS.toMillis(days))
}
