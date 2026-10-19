package com.simplecityapps.shuttle.ui.screens.settings

import androidx.compose.ui.test.junit4.createComposeRule
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsDestination
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** What the Settings root shows is decided by `SettingsViewStateTest`; this renders it and follows a tap. */
@RunWith(RobolectricTestRunner::class)
class SettingsScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val robot = SettingsRobot(composeTestRule)

    @Test
    fun `the root lists every destination and opens the one tapped`() {
        robot.setRootContent()

        listOf("Appearance", "Playback & sound", "Sources", "Library", "Privacy", "About").forEach(robot::assertDisplayed)
        robot.tapText("Playback & sound")

        robot.openedDestinations shouldBe listOf(SettingsDestination.PlaybackAndSound)
    }
}
