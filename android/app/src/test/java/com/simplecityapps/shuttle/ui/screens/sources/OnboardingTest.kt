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

// Tall enough for Sources' whole lazy list to compose, so every "Add folder" row is there to tap
@Config(qualifiers = "w411dp-h2400dp")
@RunWith(RobolectricTestRunner::class)
class OnboardingTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val robot = OnboardingRobot(composeTestRule)

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
    fun `sources adds folders, rescans and opens servers`() {
        robot.setSources(SourcesUiState(thisDevice = true, servers = ServerTypes.map { ServerSource(it, connected = it == MediaProviderType.Plex) }))

        robot.clickText("Add folder", index = 2)
        robot.lastAddFolder shouldBe FolderKind.Extra

        robot.clickText("Scan now")
        robot.rescanClicks shouldBe 1

        robot.assertTextDisplayed("Connected")
        robot.clickText("Plex")
        robot.lastServer shouldBe ServerSource(MediaProviderType.Plex, connected = true)
    }

    @Test
    fun `turning this device off asks first`() {
        robot.setSources(SourcesUiState(thisDevice = true))

        robot.clickText("This device")

        robot.lastThisDevice shouldBe null
        robot.lastDialog shouldBe SourcesDialog.TurnOffThisDevice
    }

    @Test
    fun `the Android provider says folder choices don't apply`() {
        robot.setSources(SourcesUiState(thisDevice = true, usesAndroidProvider = true))

        robot.assertTextNotDisplayed("Music stored on this phone")
    }

    @Test
    fun `a failed scan shows its error and retries on tap`() {
        robot.setSources(SourcesUiState(thisDevice = true, scanError = "Couldn't reach the server"))

        robot.assertTextDisplayed("Scan failed: Couldn't reach the server. Tap to try again")
        robot.clickText("Scan now")

        robot.rescanClicks shouldBe 1
    }

    @Test
    fun `a folder whose access was revoked is flagged and offers to fix it`() {
        val folder = SourceFolder(uri = "content://tree/Music", path = "/storage/emulated/0/Music", name = "Music", hasAccess = false)
        robot.setSources(SourcesUiState(thisDevice = true, folders = FolderLists(includes = listOf(folder))))

        robot.assertTextDisplayed("Access removed. Tap to fix")
        robot.clickText("Music")

        robot.lastDialog shouldBe SourcesDialog.RevokedFolder(FolderKind.Include, folder)
    }
}
