package com.simplecityapps.shuttle.ui.screens.sources

import androidx.compose.ui.test.junit4.createComposeRule
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.ui.screens.library.LibraryAvailability
import com.simplecityapps.shuttle.ui.screens.library.ScanProgress
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Tall enough for Sources' and Folder rules' whole lazy lists to compose, so every "Add folder" button is there to tap
@Config(qualifiers = "w411dp-h2400dp")
@RunWith(RobolectricTestRunner::class)
class OnboardingTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val robot = OnboardingRobot(composeTestRule)

    private val revokedFolder = SourceFolder(uri = "content://tree/Music", path = "/storage/emulated/0/Music", name = "Music", hasAccess = false)

    @Test
    fun `first run asks for access and offers a server`() {
        robot.setEmptyState(LibraryAvailability.Empty(MusicAccess.NotRequested))

        robot.clickText("Allow access to music")
        robot.clickText("Connect a server")

        robot.allowAccessClicks shouldBe 1
        robot.connectServerClicks shouldBe 1
    }

    @Test
    fun `a permanent refusal links to the app's settings`() {
        robot.setEmptyState(LibraryAvailability.Empty(MusicAccess.PermanentlyDenied))

        robot.assertTextNotDisplayed("Allow access to music")
        robot.clickText("Open app settings")

        robot.openAppSettingsClicks shouldBe 1
    }

    @Test
    fun `a scan shows its progress`() {
        robot.setEmptyState(LibraryAvailability.Empty(MusicAccess.Granted, ScanProgress("Artist • Song", 0.5f)))

        robot.assertTextDisplayed("Scanning your music")
        robot.assertTextDisplayed("Artist • Song")
        robot.assertTextNotDisplayed("Allow access to music")
    }

    @Test
    fun `a scan that found nothing offers to scan again`() {
        robot.setEmptyState(LibraryAvailability.Empty(MusicAccess.Granted))

        robot.assertTextDisplayed("No music found")
        robot.clickText("Scan again")

        robot.scanClicks shouldBe 1
    }

    @Test
    fun `sources turns this device on`() {
        robot.setSources(SourcesUiState(thisDevice = false))
        robot.clickText("This device")
        robot.lastThisDevice shouldBe true
    }

    @Test
    fun `this device shows its songs and when the library last updated`() {
        robot.setSources(SourcesScenarios.configured)

        robot.assertTextDisplayed("1,234 songs · Updated 2 hours ago")
        robot.assertScanNowEnabled(true)
        robot.clickText("Scan now")

        robot.rescanClicks shouldBe 1
    }

    @Test
    fun `a scan shows its progress on this device's card`() {
        robot.setSources(SourcesScenarios.scanning)

        robot.assertTextDisplayed("Scanning… 340 of 1,234 songs")
        robot.assertProgressShown()
        robot.assertScanNowEnabled(false)
    }

    @Test
    fun `folder rules open their own screen`() {
        robot.setSources(SourcesScenarios.configured)

        robot.assertTextNotDisplayed("Leave out these folders")
        robot.clickText("Folder rules")

        robot.folderRulesClicks shouldBe 1
    }

    @Test
    fun `connected servers show their status and open their options - add a server picks a type`() {
        robot.setSources(SourcesScenarios.serverUnreachable)

        robot.assertTextDisplayed("Connected · 1,842 songs · Updated 2 hours ago")
        robot.assertTextDisplayed("Can't reach")
        robot.assertTextNotDisplayed("Emby")
        robot.clickText("Plex")
        robot.lastServer shouldBe SourcesScenarios.serverUnreachable.servers.first { it.type == MediaProviderType.Plex }

        robot.clickText("Add a server")
        robot.addServerClicks shouldBe 1
    }

    @Test
    fun `turning this device off asks first`() {
        robot.setSources(SourcesUiState(thisDevice = true))

        robot.clickText("This device")

        robot.lastThisDevice shouldBe null
        robot.lastDialog shouldBe SourcesDialog.TurnOffThisDevice
    }

    @Test
    fun `the Android provider says folder rules don't apply`() {
        robot.setSources(SourcesUiState(thisDevice = true, usesAndroidProvider = true))

        robot.assertTextDisplayed("Android's media library finds this device's music, so folder rules don't apply")
    }

    @Test
    fun `a failed scan shows its error and scans again`() {
        robot.setSources(SourcesUiState(thisDevice = true, deviceStatus = SourceStatus.Failed("Couldn't read storage")))

        robot.assertTextDisplayed("Last scan failed: Couldn't read storage")
        robot.clickText("Scan now")

        robot.rescanClicks shouldBe 1
    }

    @Test
    fun `folder rules add folders in plain words`() {
        robot.setFolderRules(SourcesScenarios.configured.folders)

        robot.assertTextDisplayed("Only include these folders")
        robot.assertTextDisplayed("Every folder is included")
        robot.assertTextDisplayed("Leave out these folders")
        robot.clickText("Add folder", index = 2)

        robot.lastAddFolder shouldBe FolderKind.Extra
    }

    @Test
    fun `sources flags a folder whose access was revoked`() {
        robot.setSources(SourcesUiState(thisDevice = true, folders = FolderLists(includes = listOf(revokedFolder))))

        robot.assertTextDisplayed("A folder needs access again")
    }

    @Test
    fun `a folder whose access was revoked is flagged and offers to fix it`() {
        val folder = revokedFolder
        robot.setFolderRules(FolderLists(includes = listOf(folder)))
        robot.assertTextDisplayed("Access removed. Tap to fix")
        robot.clickText("Music")

        robot.lastFolderDialog shouldBe FolderRulesDialog.RevokedFolder(FolderKind.Include, folder)
    }
}
