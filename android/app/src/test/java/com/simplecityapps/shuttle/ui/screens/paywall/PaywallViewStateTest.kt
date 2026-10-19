package com.simplecityapps.shuttle.ui.screens.paywall

import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.screens.paywall.PaywallViewState.Button
import com.simplecityapps.shuttle.ui.screens.paywall.PaywallViewState.Copy
import com.simplecityapps.shuttle.ui.screens.paywall.PaywallViewState.Emphasis
import com.simplecityapps.shuttle.ui.screens.paywall.PaywallViewState.Tap
import io.kotest.matchers.shouldBe
import org.junit.Test

class PaywallViewStateTest {
    private fun startTrial(buyEnabled: Boolean) = listOf(
        Button(R.string.paywall_start_trial, Tap.StartTrial, enabled = true, Emphasis.Filled),
        Button(R.string.paywall_buy_now, Tap.Purchase, buyEnabled, Emphasis.Outlined)
    )

    private fun purchase(enabled: Boolean) = listOf(Button(R.string.paywall_purchase, Tap.Purchase, enabled, Emphasis.Filled))

    @Test
    fun `a free user is offered the trial first with the lifetime price and an enabled buy`() {
        val state = paywallViewState(PaywallScenarios.free)

        state.status shouldBe Copy.Res(R.string.paywall_status_trial_available)
        state.explainsTrialEnd shouldBe true
        state.purchaseBar?.buttons shouldBe startTrial(buyEnabled = true)
        state.offer shouldBe Copy.Res(R.string.paywall_price_once, listOf("$9.99"))
        state.showPricesError shouldBe false
        state.showRestore shouldBe true
        state.showRedeemCode shouldBe true
    }

    @Test
    fun `the trial shows the days left and offers Pro`() {
        val state = paywallViewState(PaywallScenarios.trial)

        state.status shouldBe Copy.Plural(R.plurals.paywall_status_trial, count = 9)
        state.explainsTrialEnd shouldBe true
        state.purchaseBar?.buttons shouldBe purchase(enabled = true)
    }

    @Test
    fun `after the trial the only action buys and nothing explains the trial end`() {
        val state = paywallViewState(PaywallScenarios.trialEnded)

        state.status shouldBe Copy.Res(R.string.paywall_status_trial_ended)
        state.explainsTrialEnd shouldBe false
        state.purchaseBar?.buttons shouldBe purchase(enabled = true)
    }

    @Test
    fun `before Play answers it says checking and does not offer the trial`() {
        val state = paywallViewState(PaywallScenarios.checking)

        state.status shouldBe Copy.Res(R.string.paywall_status_checking)
        state.explainsTrialEnd shouldBe false
    }

    @Test
    fun `while the price loads there is a placeholder and buying is disabled`() {
        val state = paywallViewState(PaywallScenarios.loading)

        state.offer shouldBe Copy.Res(R.string.paywall_price_loading)
        state.purchaseBar?.buttons shouldBe startTrial(buyEnabled = false)
        state.showPricesError shouldBe false
    }

    @Test
    fun `unavailable prices show the error and disable buying`() {
        val state = paywallViewState(PaywallScenarios.pricesUnavailable)

        state.offer shouldBe Copy.Res(R.string.paywall_price_unavailable)
        state.purchaseBar?.buttons shouldBe purchase(enabled = false)
        state.showPricesError shouldBe true
    }

    @Test
    fun `a purchase waiting on payment says so and cannot be repeated`() {
        val state = paywallViewState(PaywallScenarios.pending)

        state.status shouldBe Copy.Res(R.string.paywall_status_purchase_pending)
        state.explainsTrialEnd shouldBe false
        state.purchaseBar?.buttons?.filter { it.tap == Tap.Purchase }?.map { it.enabled }?.distinct() shouldBe listOf(false)
        state.restoreEnabled shouldBe true
    }

    @Test
    fun `restore is disabled while restoring`() {
        paywallViewState(PaywallScenarios.trialEnded.copy(restoring = true)).restoreEnabled shouldBe false
    }

    @Test
    fun `a lifetime Pro user sees their status and nothing to buy redeem restore or manage`() {
        val state = paywallViewState(PaywallScenarios.pro)

        state.status shouldBe Copy.Res(R.string.paywall_status_pro)
        state.purchaseBar shouldBe null
        state.offer shouldBe null
        state.showRedeemCode shouldBe false
        state.showRestore shouldBe false
        state.showPricesError shouldBe false
        state.showManageSubscription shouldBe false
    }

    @Test
    fun `a subscriber can manage their subscription`() {
        paywallViewState(PaywallScenarios.subscriber).showManageSubscription shouldBe true
    }

    @Test
    fun `an earlier purchase is thanked as legacy Pro with nothing to manage`() {
        val state = paywallViewState(PaywallScenarios.legacyPro)

        state.status shouldBe Copy.Res(R.string.paywall_status_pro_legacy)
        state.purchaseBar shouldBe null
        state.showManageSubscription shouldBe false
    }
}
