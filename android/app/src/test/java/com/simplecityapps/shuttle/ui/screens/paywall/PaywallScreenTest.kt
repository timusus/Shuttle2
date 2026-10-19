package com.simplecityapps.shuttle.ui.screens.paywall

import androidx.compose.ui.test.junit4.createComposeRule
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Render tests for [PaywallScreen]: the resolved copy, rendered enabled state and each control reaching its event.
 * The decisions behind them are covered by `PaywallViewStateTest`, and where each event goes by `PaywallEventsTest`.
 */
@RunWith(RobolectricTestRunner::class)
class PaywallScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val robot = PaywallRobot(composeTestRule)

    // codehealth-ignore tests-slow-kind-fast-subject: P2 wiring; the trial copy, benefits and price are string resources only the rendered tree resolves.
    @Test
    fun `a free user sees the trial offer, the benefits and the lifetime price`() {
        robot.setContent(PaywallScenarios.free)

        robot.assertDisplayed(TRIAL_AVAILABLE)
        robot.assertDisplayed(TRIAL_TERMS)
        robot.assertDisplayed("What you get")
        robot.assertDisplayed("Download songs from your server to play offline")
        robot.assertDisplayed("Your whole library in Android Auto")
        robot.assertDisplayed("Jellyfin, Emby, Plex and Navidrome in one app")
        robot.assertDisplayed("Edit the tags of many songs at once")
        robot.assertDisplayed("ReplayGain volume levelling by track or album")
        robot.assertDisplayed("$9.99 once")
        robot.assertNotShown("Yearly")
        robot.assertNotShown("$3.99 / year")
    }

    // codehealth-ignore tests-slow-kind-fast-subject: P2 wiring; checks the purchase bar's buttons render their labels and reach onEvent.
    @Test
    fun `a free user's main button starts the trial, and buying is the second button`() {
        robot.setContent(PaywallScenarios.free)

        robot.assertNotShown("Get Shuttle Music Pro")
        robot.tapText("Start 14-day free trial")
        robot.tapText("Buy now")

        robot.trialStarts shouldBe 1
        robot.purchases shouldBe 1
    }

    // codehealth-ignore tests-slow-kind-fast-subject: P2 wiring; the days-left plural and the enabled state are resolved only in the rendered tree.
    @Test
    fun `the trial shows the days left, what stops after it, and offers Pro`() {
        robot.setContent(PaywallScenarios.trial)

        robot.assertDisplayed("9 days left in your free trial")
        robot.assertDisplayed(TRIAL_TERMS)
        robot.assertEnabled("Get Shuttle Music Pro")
        robot.assertNotShown("Start 14-day free trial")
        robot.assertNotShown("Buy now")
    }

    // codehealth-ignore tests-slow-kind-fast-subject: P2 wiring; checks the footer link's click reaches onEvent.
    @Test
    fun `the privacy policy is linked from the footer`() {
        robot.setContent(PaywallScenarios.pro)

        robot.tapText("Privacy policy")

        robot.privacyPolicyTaps shouldBe 1
    }

    // codehealth-ignore tests-slow-kind-fast-subject: P2 wiring; the checking copy is a string resource only the rendered tree resolves.
    @Test
    fun `before Play answers, the paywall says it is checking rather than offering the trial`() {
        robot.setContent(PaywallScenarios.checking)

        robot.assertDisplayed("Checking your purchases with Google Play…")
        robot.assertNotShown(TRIAL_AVAILABLE)
        robot.assertNotShown(TRIAL_TERMS)
    }

    // codehealth-ignore tests-slow-kind-fast-subject: P2 wiring; checks the single purchase button renders and reaches onEvent.
    @Test
    fun `after the trial the only button buys, and there is no second trial`() {
        robot.setContent(PaywallScenarios.trialEnded)

        robot.assertNotShown("Start 14-day free trial")
        robot.tapText("Get Shuttle Music Pro")

        robot.purchases shouldBe 1
    }

    // codehealth-ignore tests-slow-kind-fast-subject: P2 wiring; checks the Redeem button's click reaches onEvent.
    @Test
    fun `a user without Pro can redeem a promo code`() {
        robot.setContent(PaywallScenarios.trialEnded)

        robot.tapText("Redeem a code")

        robot.redeemTaps shouldBe 1
    }

    // codehealth-ignore tests-slow-kind-fast-subject: P2 wiring; checks the hidden flag removes the button from the rendered tree.
    @Test
    fun `a Pro user is not offered a promo code`() {
        robot.setContent(PaywallScenarios.pro)

        robot.assertNotShown("Redeem a code")
    }

    // codehealth-ignore tests-slow-kind-fast-subject: P2 wiring; Back belongs to the top bar, only in the rendered tree, and Restore's click must reach onEvent.
    @Test
    fun `restore and back reach the caller`() {
        robot.setContent(PaywallScenarios.trialEnded)

        robot.tapText("Restore purchases")
        robot.tapBack()

        robot.restores shouldBe 1
        robot.closed shouldBe true
    }

    // codehealth-ignore tests-slow-kind-fast-subject: P2 wiring; the placeholder copy and the enabled states are resolved only in the rendered tree.
    @Test
    fun `while the price loads it shows a placeholder and nothing can be bought`() {
        robot.setContent(PaywallScenarios.loading)

        robot.assertDisplayed("Lifetime")
        robot.assertDisplayed("Loading price…")
        robot.assertEnabled("Start 14-day free trial")
        robot.assertNotEnabled("Buy now")
        robot.assertNotShown("Couldn't load prices")
    }

    // codehealth-ignore tests-slow-kind-fast-subject: P2 wiring; the error state's Retry is a design-system action only the rendered tree exposes.
    @Test
    fun `unavailable prices offer a retry`() {
        robot.setContent(PaywallScenarios.pricesUnavailable)

        robot.assertDisplayed("Your free trial has ended. Upgrade to keep using Shuttle Music Pro.")
        robot.assertDisplayed("Price unavailable")
        robot.assertNotEnabled("Get Shuttle Music Pro")
        robot.tapText("Retry")

        robot.retries shouldBe 1
    }

    // codehealth-ignore tests-slow-kind-fast-subject: P2 wiring; the Pro status copy is a string resource only the rendered tree resolves.
    @Test
    fun `a Pro user sees their status and no plans`() {
        robot.setContent(PaywallScenarios.pro)

        robot.assertDisplayed("You have Shuttle Music Pro. Thank you for supporting Shuttle Music.")
        robot.assertNotShown("Get Shuttle Music Pro")
        robot.assertNotShown("Manage subscription")
    }

    // codehealth-ignore tests-slow-kind-fast-subject: P2 wiring; the pitch is fixed string resources only the rendered tree resolves.
    @Test
    fun `the pitch says what is free and what Pro costs before anything else`() {
        robot.setContent(PaywallScenarios.free)

        robot.assertDisplayed("Your server, offline and in the car")
        robot.assertDisplayed("Free for music on this phone. Pro is a one-time unlock, with a 14-day free trial.")
    }

    // codehealth-ignore tests-slow-kind-fast-subject: P2 wiring; the pending copy and enabled state are rendered, and Restore's click must reach onEvent.
    @Test
    fun `a purchase waiting on payment says so, and another can't be bought meanwhile`() {
        robot.setContent(PaywallScenarios.pending)

        robot.assertDisplayed("Your purchase is waiting for payment. Shuttle Music Pro unlocks as soon as Google Play confirms it.")
        robot.assertNotEnabled("Get Shuttle Music Pro")
        robot.assertNotShown(TRIAL_AVAILABLE)
        robot.tapText("Restore purchases")

        robot.restores shouldBe 1
    }

    // codehealth-ignore tests-slow-kind-fast-subject: P2 wiring; the legacy thanks is a string resource only the rendered tree resolves.
    @Test
    fun `an earlier purchase is thanked as already owning Pro`() {
        robot.setContent(PaywallScenarios.legacyPro)

        robot.assertDisplayed("You already own Shuttle Music Pro through your earlier purchase. Thank you for supporting Shuttle Music.")
        robot.assertNotShown("Get Shuttle Music Pro")
        robot.assertNotShown("Manage subscription")
    }

    // codehealth-ignore tests-slow-kind-fast-subject: P2 wiring; checks the Manage button's click reaches onEvent.
    @Test
    fun `a subscriber can manage their subscription`() {
        robot.setContent(PaywallScenarios.subscriber)

        robot.tapText("Manage subscription")

        robot.manageTaps shouldBe 1
    }

    private companion object {
        const val TRIAL_AVAILABLE = "Try Shuttle Music Pro free for 14 days. Nothing is charged, and local playback stays free."
        const val TRIAL_TERMS = "After the trial, server songs, Android Auto, batch tag editing and turning on ReplayGain ask you to upgrade. Downloaded songs, music on this phone and your saved settings keep working."
    }
}
