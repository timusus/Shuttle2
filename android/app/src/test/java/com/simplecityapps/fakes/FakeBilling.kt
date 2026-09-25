package com.simplecityapps.fakes

import android.app.Activity
import com.simplecityapps.trial.Billing
import com.simplecityapps.trial.PaywallOffer
import com.simplecityapps.trial.PaywallOffers
import com.simplecityapps.trial.PaywallPlan
import com.simplecityapps.trial.RestoreResult
import kotlinx.coroutines.flow.MutableStateFlow

/** Play Billing without Play: settable offers and restore result, and a record of what was asked of it. */
class FakeBilling(
    offers: PaywallOffers = PaywallOffers.Available(SAMPLE_OFFERS)
) : Billing {
    override val ownedProductIds = MutableStateFlow<Set<String>?>(emptySet())
    override val offers = MutableStateFlow(offers)

    var restoreResult = RestoreResult.NothingToRestore
    var launchSucceeds = true
    var offerRefreshes = 0
        private set
    val launchedOffers = mutableListOf<PaywallOffer>()

    override fun start() = Unit

    override fun queryPurchases() = Unit

    override fun refreshOffers() {
        offerRefreshes++
    }

    override suspend fun restorePurchases(): RestoreResult = restoreResult

    override fun launchPurchaseFlow(
        activity: Activity,
        offer: PaywallOffer
    ): Boolean {
        launchedOffers += offer
        return launchSucceeds
    }

    companion object {
        val LIFETIME = PaywallOffer("s2_pro_lifetime", PaywallPlan.Lifetime, "$9.99", offerToken = null)
        val ANNUAL = PaywallOffer("s2_pro", PaywallPlan.Annual, "$3.99", offerToken = "annual")
        val SAMPLE_OFFERS = listOf(LIFETIME, ANNUAL)
    }
}
