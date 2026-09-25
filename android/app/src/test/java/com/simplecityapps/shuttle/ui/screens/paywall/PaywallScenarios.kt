package com.simplecityapps.shuttle.ui.screens.paywall

import com.simplecityapps.fakes.FakeBilling
import com.simplecityapps.trial.PaywallOffers
import com.simplecityapps.trial.ProSource

/** Paywall UI states the characterisation and screenshot tests render. */
object PaywallScenarios {
    val free = PaywallUiState(status = PaywallStatus.TrialAvailable, offers = PaywallOffers.Available(FakeBilling.SAMPLE_OFFERS))

    val trialEnded = free.copy(status = PaywallStatus.TrialEnded)

    val trial = free.copy(status = PaywallStatus.Trial(daysLeft = 9))

    val pro = PaywallUiState(status = PaywallStatus.Pro(ProSource.Lifetime), offers = PaywallOffers.Available(FakeBilling.SAMPLE_OFFERS))

    val subscriber = pro.copy(status = PaywallStatus.Pro(ProSource.Subscription))

    val loading = free.copy(offers = PaywallOffers.Loading)

    val pricesUnavailable = trialEnded.copy(offers = PaywallOffers.Unavailable)
}
