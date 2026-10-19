package com.simplecityapps.shuttle.ui.screens.paywall

import io.kotest.matchers.shouldBe
import org.junit.Test

class PaywallEventsTest {
    private val reached = mutableListOf<String>()

    private val callbacks = PaywallCallbacks(
        onClose = { reached += "close" },
        onPurchase = { reached += "purchase" },
        onRestore = { reached += "restore" },
        onRedeemCode = { reached += "redeem" },
        onRetry = { reached += "retry" },
        onManageSubscription = { reached += "manage" },
        onStartTrial = { reached += "trial" },
        onOpenPrivacyPolicy = { reached += "privacy" }
    )

    @Test
    fun `each event reaches its own handler`() {
        listOf(
            PaywallEvent.Close,
            PaywallEvent.Purchase,
            PaywallEvent.Restore,
            PaywallEvent.RedeemCode,
            PaywallEvent.Retry,
            PaywallEvent.ManageSubscription,
            PaywallEvent.StartTrial,
            PaywallEvent.OpenPrivacyPolicy
        ).forEach(callbacks::onEvent)

        reached shouldBe listOf("close", "purchase", "restore", "redeem", "retry", "manage", "trial", "privacy")
    }

    @Test
    fun `a free user's main button starts the trial and the second buys`() {
        paywallViewState(PaywallScenarios.free).purchaseBar!!.buttons.forEach { callbacks.onEvent(it.tap.event()) }

        reached shouldBe listOf("trial", "purchase")
    }

    @Test
    fun `after the trial the only button buys`() {
        paywallViewState(PaywallScenarios.trialEnded).purchaseBar!!.buttons.forEach { callbacks.onEvent(it.tap.event()) }

        reached shouldBe listOf("purchase")
    }
}
