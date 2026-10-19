package com.simplecityapps.shuttle.ui.screens.paywall

import androidx.compose.ui.test.junit4.createComposeRule
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Render smoke test for [PaywallScreen]; the decisions are covered by `PaywallViewStateTest`. */
@RunWith(RobolectricTestRunner::class)
class PaywallScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val robot = PaywallRobot(composeTestRule)

    @Test
    fun `a free user sees the pitch, the benefits and the price, and the main button starts the trial`() {
        robot.setContent(PaywallScenarios.free)

        robot.assertDisplayed("Your server, offline and in the car")
        robot.assertDisplayed("What you get")
        robot.assertDisplayed("Edit the tags of many songs at once")
        robot.assertDisplayed("$9.99 once")

        robot.tapText("Start 14-day free trial")

        robot.trialStarts shouldBe 1
    }
}
