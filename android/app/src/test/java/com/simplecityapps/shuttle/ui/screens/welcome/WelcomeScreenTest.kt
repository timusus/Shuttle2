package com.simplecityapps.shuttle.ui.screens.welcome

import androidx.compose.ui.test.junit4.createComposeRule
import com.simplecityapps.shuttle.ui.screens.welcome.WelcomeRobot.Companion.SERVER_PICKER
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WelcomeScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val robot = WelcomeRobot(composeTestRule)

    @Test
    fun `the welcome offers this device or a server and says analytics are on`() {
        robot.setContent()

        robot.assertTextDisplayed("Welcome to Shuttle Music")
        robot.assertTextDisplayed("Music on this device")
        robot.assertTextDisplayed("Connect a server")
        robot.assertTextDisplayed("Crash reports and anonymous analytics are on, to help improve the app.")
        robot.assertTextDisplayed("Privacy settings")
    }

    @Test
    fun `music on this device asks for the music permission`() {
        robot.setContent()

        robot.clickText("Music on this device")

        robot.musicAccessRequests shouldBe 1
        robot.assertTextNotDisplayed(SERVER_PICKER)
    }

    @Test
    fun `connect a server opens the server picker without asking for the music permission`() {
        robot.setContent()

        robot.clickText("Connect a server")

        robot.assertTextDisplayed(SERVER_PICKER)
        robot.musicAccessRequests shouldBe 0
    }

    @Test
    fun `closing the server picker returns to the welcome`() {
        robot.setContent()
        robot.clickText("Connect a server")

        robot.clickText(SERVER_PICKER)

        robot.assertTextNotDisplayed(SERVER_PICKER)
        robot.assertTextDisplayed("Music on this device")
    }

    @Test
    fun `privacy settings opens privacy`() {
        robot.setContent()

        robot.clickText("Privacy settings")

        robot.privacyOpened shouldBe 1
    }
}
