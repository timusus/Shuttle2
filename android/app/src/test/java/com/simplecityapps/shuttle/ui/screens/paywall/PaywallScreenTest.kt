package com.simplecityapps.shuttle.ui.screens.paywall

import androidx.compose.ui.test.junit4.createComposeRule
import com.simplecityapps.trial.PaywallPlan
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Characterisation tests for the S2 Pro paywall. */
@RunWith(RobolectricTestRunner::class)
class PaywallScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val robot = PaywallRobot(composeTestRule)

    @Test
    fun `a free user sees the trial offer, the benefits and every plan with its price`() {
        robot.setContent(PaywallScenarios.free)

        robot.assertDisplayed(TRIAL_AVAILABLE)
        robot.assertDisplayed(TRIAL_TERMS)
        robot.assertDisplayed("What you get")
        robot.assertDisplayed("Stream from Jellyfin, Emby and Plex")
        robot.assertDisplayed("$9.99 once")
        robot.assertDisplayed("$3.99 / year")
        robot.assertDisplayed("Renews yearly · cancel anytime in Google Play")
        robot.assertDisplayed("Best value")
        robot.assertPlanSelected("Lifetime")
        robot.assertPlanNotSelected("Yearly")
    }

    @Test
    fun `a free user's main button starts the trial, and buying is the second button`() {
        robot.setContent(PaywallScenarios.free)

        robot.assertNotShown("Get S2 Pro")
        robot.tapText("Start free trial")
        robot.tapText("Buy now")

        robot.trialStarts shouldBe 1
        robot.purchases shouldBe 1
    }

    @Test
    fun `the trial shows the days left, what stops after it, and offers Pro`() {
        robot.setContent(PaywallScenarios.trial)

        robot.assertDisplayed("9 days left in your free trial")
        robot.assertDisplayed(TRIAL_TERMS)
        robot.assertEnabled("Get S2 Pro")
        robot.assertNotShown("Start free trial")
        robot.assertNotShown("Buy now")
    }

    @Test
    fun `the privacy policy is linked from the footer`() {
        robot.setContent(PaywallScenarios.pro)

        robot.tapText("Privacy policy")

        robot.privacyPolicyTaps shouldBe 1
    }

    @Test
    fun `before Play answers, the paywall says it is checking rather than offering the trial`() {
        robot.setContent(PaywallScenarios.checking)

        robot.assertDisplayed("Checking your purchases with Google Play…")
        robot.assertNotShown(TRIAL_AVAILABLE)
        robot.assertNotShown(TRIAL_TERMS)
    }

    @Test
    fun `tapping a plan selects it, and the button buys it`() {
        robot.setContent(PaywallScenarios.trialEnded)

        robot.tapPlan("Yearly")
        robot.tapText("Get S2 Pro")

        robot.selectedPlans shouldBe listOf(PaywallPlan.Annual)
        robot.purchases shouldBe 1
    }

    @Test
    fun `restore and back reach the caller`() {
        robot.setContent(PaywallScenarios.trialEnded)

        robot.tapText("Restore purchases")
        robot.tapBack()

        robot.restores shouldBe 1
        robot.closed shouldBe true
    }

    @Test
    fun `while prices load the plans show placeholders and nothing can be bought`() {
        robot.setContent(PaywallScenarios.loading)

        robot.assertDisplayed("Lifetime")
        robot.assertShownOnEveryPlan("Loading price…")
        robot.assertEnabled("Start free trial")
        robot.assertNotEnabled("Buy now")
        robot.assertNotShown("Couldn't load prices")
    }

    @Test
    fun `unavailable prices offer a retry`() {
        robot.setContent(PaywallScenarios.pricesUnavailable)

        robot.assertDisplayed("Your free trial has ended. Upgrade to keep streaming from your servers.")
        robot.assertShownOnEveryPlan("Price unavailable")
        robot.assertNotEnabled("Get S2 Pro")
        robot.tapText("Retry")

        robot.retries shouldBe 1
    }

    @Test
    fun `a Pro user sees their status and no plans`() {
        robot.setContent(PaywallScenarios.pro)

        robot.assertDisplayed("You have S2 Pro. Thank you for supporting S2.")
        robot.assertNotShown("Get S2 Pro")
        robot.assertNotShown("Manage subscription")
    }

    @Test
    fun `a subscriber can manage their subscription`() {
        robot.setContent(PaywallScenarios.subscriber)

        robot.tapText("Manage subscription")

        robot.manageTaps shouldBe 1
    }

    private companion object {
        const val TRIAL_AVAILABLE = "Try streaming from your server free for 14 days. The trial starts the first time you play a server song."
        const val TRIAL_TERMS = "After the trial, server songs won't play until you upgrade. Downloaded songs and music on this phone keep playing."
    }
}
