package com.simplecityapps.shuttle.ui.screens.settings

import androidx.compose.ui.test.hasText
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

/** Characterisation tests for the Settings root and destination screens, rendered from the catalog. */
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
    fun `the root opens S2 Pro`() {
        robot.setRootContent()

        robot.tapText("S2 Pro")

        robot.openedPro shouldBe true
    }

    @Test
    fun `back leaves settings`() {
        robot.setRootContent()

        robot.tapBack()

        robot.navigatedUp shouldBe true
    }

    @Test
    fun `appearance shows the stored values`() {
        robot.setDestinationContent(SettingsDestination.Appearance, SettingsScenarios.darkPureBlack)

        robot.assertDisplayed("Dark")
        robot.assertSwitchOn("Pure black")
        robot.assertSwitchOff("Show Home on launch")
    }

    @Test
    fun `tapping a switch asks for the opposite value`() {
        robot.setDestinationContent(SettingsDestination.Appearance, SettingsScenarios.darkPureBlack)

        robot.tapText("Pure black")

        robot.switchChanges shouldBe listOf(AppearanceSettings.PureBlack.key to false)
    }

    @Test
    fun `a choice opens a dialog and reports the option picked`() {
        robot.setDestinationContent(SettingsDestination.Appearance)

        robot.tapText("Theme")
        robot.assertDialogDisplayed("Light")
        robot.tapDialogText("Light")

        robot.choiceSelections shouldBe listOf(AppearanceSettings.Theme.key to 1)
        robot.assertNotShown("Light")
    }

    @Test
    fun `rows newer than the device are left out`() {
        robot.setDestinationContent(SettingsDestination.Appearance, sdkInt = 30)

        robot.assertNotShown("Dynamic colour")
    }

    @Test
    fun `a confirmed action runs only after confirming`() {
        robot.setDestinationContent(SettingsDestination.Library)

        robot.tapText("Clear artwork")
        robot.actions shouldBe emptyList()
        robot.assertDialogDisplayed("This will permanently remove all cached artwork")
        robot.tapDialogText("Clear")

        robot.actions shouldBe listOf(SettingsAction.ClearArtworkCache)
    }

    @Test
    fun `an unconfirmed action runs straight away`() {
        robot.setDestinationContent(SettingsDestination.Library)

        robot.tapText("Rescan")

        robot.actions shouldBe listOf(SettingsAction.Rescan)
    }

    @Test
    fun `the last scan date follows the rescan frequency`() {
        robot.setDestinationContent(SettingsDestination.Library, SettingsScenarios.scannedWeekly)

        robot.scrollTo("Rescan frequency")
        composeTestRule.onNode(hasText("Weekly", substring = true) and hasText("Last scan", substring = true)).assertExists()
    }

    @Test
    fun `copying logs is disabled until debug logging is on`() {
        robot.setDestinationContent(SettingsDestination.About, SettingsScenarios.fileLoggingOff)
        robot.assertNotEnabled("Copy debug logs")
    }

    @Test
    fun `copying logs is enabled with debug logging on`() {
        robot.setDestinationContent(SettingsDestination.About, SettingsScenarios.fileLoggingOn)
        robot.assertEnabled("Copy debug logs")
        robot.tapText("Copy debug logs")

        robot.actions shouldBe listOf(SettingsAction.CopyDebugLogs)
    }

    @Test
    fun `about shows the version and opens the changelog`() {
        robot.setDestinationContent(SettingsDestination.About, versionName = "2026.09.25")

        robot.assertDisplayed("2026.09.25")
        robot.tapText("View changelog")

        robot.openedLinks shouldBe listOf(SettingsLink.WhatsNew)
    }

    @Test
    fun `the media provider link stays hidden until the sources redesign`() {
        robot.setDestinationContent(SettingsDestination.Sources)

        robot.assertNotShown("Media providers")
        robot.assertDisplayed("Report playback to server")
    }

    @Test
    fun `playback opens the equalizer`() {
        robot.setDestinationContent(SettingsDestination.PlaybackAndSound)

        robot.tapText("Equalizer")

        robot.openedLinks shouldBe listOf(SettingsLink.Equalizer)
    }
}
