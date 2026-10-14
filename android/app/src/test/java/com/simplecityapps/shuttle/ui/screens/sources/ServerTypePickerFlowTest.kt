package com.simplecityapps.shuttle.ui.screens.sources

import androidx.compose.ui.test.junit4.createComposeRule
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ServerTypePickerFlowTest {
    @get:Rule
    val rule = createComposeRule()

    private val robot = ServerTypePickerFlowRobot(rule)

    @Test
    fun `choosing Emby opens its sign-in instead of closing the flow`() {
        robot.setContent()

        robot.clickText("Emby")

        robot.assertTextDisplayed("Signing in to Emby")
        robot.dismissed shouldBe 0
    }

    @Test
    fun `choosing Plex opens its sign-in`() {
        robot.setContent()

        robot.clickText("Plex")

        robot.assertTextDisplayed("Signing in to Plex")
    }
}
