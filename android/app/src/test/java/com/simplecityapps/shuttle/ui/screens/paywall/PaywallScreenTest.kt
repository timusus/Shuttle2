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

        robot.assertDisplayed("Add a Jellyfin, Emby or Plex server to start a free 14-day trial.")
        robot.assertDisplayed("What you get")
        robot.assertDisplayed("Stream from Jellyfin, Emby and Plex")
        robot.assertDisplayed("$9.99 once")
        robot.assertDisplayed("$3.99 / year")
        robot.assertDisplayed("Best value")
        robot.assertPlanSelected("Lifetime")
        robot.assertPlanNotSelected("Yearly")
        robot.assertEnabled("Get S2 Pro")
    }

    @Test
    fun `the trial shows the days left`() {
        robot.setContent(PaywallScenarios.trial)

        robot.assertDisplayed("9 days left in your free trial")
    }

    @Test
    fun `tapping a plan selects it, and the button buys it`() {
        robot.setContent(PaywallScenarios.free)

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
        robot.assertNotEnabled("Get S2 Pro")
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
}
