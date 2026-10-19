package com.simplecityapps.shuttle.ui.screens.paywall

import io.kotest.matchers.shouldBe
import org.junit.Test

class PaywallViewStateTest {
    @Test
    fun `a free user is offered the trial first with the lifetime price and an enabled buy`() {
        val state = paywallViewState(PaywallScenarios.free)

        state.status shouldBe PaywallViewState.StatusMessage.TrialAvailable
        state.explainsTrialEnd shouldBe true
        state.purchaseBar shouldBe PaywallViewState.PurchaseBar(PaywallPrimaryAction.StartTrial, purchaseEnabled = true)
        state.offer shouldBe PaywallViewState.Offer.Price("$9.99")
        state.showPricesError shouldBe false
        state.showAccountActions shouldBe true
    }

    @Test
    fun `the trial shows the days left and offers Pro`() {
        val state = paywallViewState(PaywallScenarios.trial)

        state.status shouldBe PaywallViewState.StatusMessage.Trial(daysLeft = 9)
        state.explainsTrialEnd shouldBe true
        state.purchaseBar shouldBe PaywallViewState.PurchaseBar(PaywallPrimaryAction.Purchase, purchaseEnabled = true)
    }

    @Test
    fun `after the trial the only action buys and nothing explains the trial end`() {
        val state = paywallViewState(PaywallScenarios.trialEnded)

        state.status shouldBe PaywallViewState.StatusMessage.TrialEnded
        state.explainsTrialEnd shouldBe false
        state.purchaseBar shouldBe PaywallViewState.PurchaseBar(PaywallPrimaryAction.Purchase, purchaseEnabled = true)
    }

    @Test
    fun `before Play answers it says checking and does not offer the trial`() {
        val state = paywallViewState(PaywallScenarios.checking)

        state.status shouldBe PaywallViewState.StatusMessage.Checking
        state.explainsTrialEnd shouldBe false
    }

    @Test
    fun `while the price loads there is a placeholder and buying is disabled`() {
        val state = paywallViewState(PaywallScenarios.loading)

        state.offer shouldBe PaywallViewState.Offer.Loading
        state.purchaseBar shouldBe PaywallViewState.PurchaseBar(PaywallPrimaryAction.StartTrial, purchaseEnabled = false)
        state.showPricesError shouldBe false
    }

    @Test
    fun `unavailable prices show the error and disable buying`() {
        val state = paywallViewState(PaywallScenarios.pricesUnavailable)

        state.offer shouldBe PaywallViewState.Offer.Unavailable
        state.purchaseBar shouldBe PaywallViewState.PurchaseBar(PaywallPrimaryAction.Purchase, purchaseEnabled = false)
        state.showPricesError shouldBe true
    }

    @Test
    fun `a purchase waiting on payment says so and cannot be repeated`() {
        val state = paywallViewState(PaywallScenarios.pending)

        state.status shouldBe PaywallViewState.StatusMessage.PurchasePending
        state.explainsTrialEnd shouldBe false
        state.purchaseBar?.purchaseEnabled shouldBe false
        state.restoreEnabled shouldBe true
    }

    @Test
    fun `restore is disabled while restoring`() {
        paywallViewState(PaywallScenarios.trialEnded.copy(restoring = true)).restoreEnabled shouldBe false
    }

    @Test
    fun `a lifetime Pro user sees their status and nothing to buy or manage`() {
        val state = paywallViewState(PaywallScenarios.pro)

        state.status shouldBe PaywallViewState.StatusMessage.Pro
        state.purchaseBar shouldBe null
        state.offer shouldBe null
        state.showAccountActions shouldBe false
        state.showManageSubscription shouldBe false
    }

    @Test
    fun `a subscriber can manage their subscription`() {
        paywallViewState(PaywallScenarios.subscriber).showManageSubscription shouldBe true
    }

    @Test
    fun `an earlier purchase is thanked as legacy Pro with nothing to manage`() {
        val state = paywallViewState(PaywallScenarios.legacyPro)

        state.status shouldBe PaywallViewState.StatusMessage.ProLegacy
        state.purchaseBar shouldBe null
        state.showManageSubscription shouldBe false
    }
}
