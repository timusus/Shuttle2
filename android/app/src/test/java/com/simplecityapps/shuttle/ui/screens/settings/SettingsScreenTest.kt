package com.simplecityapps.shuttle.ui.screens.settings

import androidx.compose.ui.test.junit4.createComposeRule
import com.simplecityapps.shuttle.settings.AppearanceSettings
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsAction
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsDestination
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsLink
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Render tests for the Settings screens' wiring: each control reaching its event. What they show is decided by
 * `SettingsViewStateTest` and `SettingsDialogViewStateTest`, and where each event goes by `SettingsEventsTest`.
 */
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

    @Test
    fun `the root opens Shuttle Music Pro`() {
        robot.setRootContent()

        robot.tapText("Shuttle Music Pro")

        robot.openedPro shouldBe true
    }

    @Test
    fun `back leaves settings`() {
        robot.setRootContent()

        robot.tapBack()

        robot.navigatedUp shouldBe true
    }

    @Test
    fun `tapping a switch asks for the opposite value`() {
        robot.setDestinationContent(SettingsDestination.Appearance, SettingsScenarios.darkPureBlack)

        robot.tapText("Pure black")

        robot.switchChanges shouldBe listOf(AppearanceSettings.PureBlack.key to false)
    }

    @Test
    fun `tapping the theme row opens its choice dialog`() {
        robot.setDestinationContent(SettingsDestination.Appearance, SettingsScenarios.darkPureBlack)

        robot.tapText("Theme")

        robot.assertDialogDisplayed("Light")
    }

    @Test
    fun `picking a theme selects it and closes the dialog`() {
        robot.setDestinationContent(SettingsDestination.Appearance, SettingsScenarios.darkPureBlack)
        robot.tapText("Theme")

        robot.tapDialogText("Light")

        robot.choiceSelections shouldBe listOf(AppearanceSettings.Theme.key to 1)
        robot.assertNotShown("Light")
    }

    @Test
    fun `clearing artwork asks first then runs`() {
        robot.setDestinationContent(SettingsDestination.Library)

        robot.tapText("Clear artwork")
        robot.actions shouldBe emptyList()
        robot.assertDialogDisplayed("This will permanently remove all cached artwork")
        robot.tapDialogText("Clear")

        robot.actions shouldBe listOf(SettingsAction.ClearArtworkCache)
    }

    @Test
    fun `sharing logs is enabled with debug logging on`() {
        robot.setDestinationContent(SettingsDestination.About, SettingsScenarios.fileLoggingOn)
        robot.assertEnabled("Share debug logs")
        robot.tapText("Share debug logs")

        robot.actions shouldBe listOf(SettingsAction.ShareDebugLogs)
    }

    @Test
    fun `about shows the version and opens the changelog`() {
        robot.setDestinationContent(SettingsDestination.About, versionName = "2026.09.25")

        robot.assertDisplayed("2026.09.25")
        robot.tapText("View changelog")

        robot.openedLinks shouldBe listOf(SettingsLink.WhatsNew)
    }

    @Test
    fun `about opens the licences`() {
        robot.setDestinationContent(SettingsDestination.About)

        robot.tapText("View licenses")

        robot.openedLinks shouldBe listOf(SettingsLink.Licences)
    }
}
