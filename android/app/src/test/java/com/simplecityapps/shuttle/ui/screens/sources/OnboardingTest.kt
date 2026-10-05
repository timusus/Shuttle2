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

// Tall enough for Sources' and This device's whole lazy lists to compose, so every "Add folder" button is there to tap
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
    fun `sources shows servers first and opens this device from one row`() {
        robot.setSources(SourcesScenarios.configured)

        robot.assertTextDisplayed("Servers")
        robot.assertTextDisplayed("1,234 songs · Updated 2 hours ago")
        robot.assertTextNotDisplayed("Scan now")
        robot.assertTextNotDisplayed("Folder rules")
        robot.clickText("This device")

        robot.thisDeviceClicks shouldBe 1
    }

    @Test
    fun `an off device row says it's off`() {
        robot.setSources(SourcesUiState(thisDevice = false))

        robot.assertTextDisplayed("Off. Turn on to use music stored on this phone")
    }

    @Test
    fun `a scan shows its progress on the this device row`() {
        robot.setSources(SourcesScenarios.scanning)

        robot.assertTextDisplayed("Scanning… 340 of 1,234 songs")
        robot.assertProgressShown()
    }

    @Test
    fun `sources flags a folder whose access was revoked`() {
        robot.setSources(SourcesUiState(thisDevice = true, folders = FolderLists(includes = listOf(revokedFolder))))

        robot.assertTextDisplayed("A folder needs access again")
    }

    @Test
    fun `connected servers show their account and status and open their page - add a server picks a type`() {
        robot.setSources(SourcesScenarios.serverUnreachable)

        robot.assertTextDisplayed("tim@jellyfin.local · 1,842 songs · Updated 2 hours ago")
        robot.assertTextDisplayed("tim@plex.local · Can't reach")
        robot.assertTextNotDisplayed("Emby")
        robot.clickText("Plex")
        robot.lastServer shouldBe SourcesScenarios.serverUnreachable.servers.first { it.type == MediaProviderType.Plex }

        robot.clickText("Add a server")
        robot.addServerClicks shouldBe 1
    }

    @Test
    fun `a server's listing shortfall shows on its row`() {
        robot.setSources(SourcesScenarios.serverShortfall)

        robot.assertTextDisplayed("tim@jellyfin.local · 1,842 songs · Updated 2 hours ago · 3 items the server counts but doesn't return")
        robot.assertTextDisplayed("tim@plex.local · 1,842 songs · Updated 2 hours ago")
    }

    @Test
    fun `a server's page shows its status and account - syncs, signs in again and removes`() {
        robot.setServerDetail(SourcesScenarios.configured.servers.first { it.connected })

        robot.assertTextDisplayed("Connected · 1,842 songs · Updated 2 hours ago")
        robot.assertTextDisplayed("tim@jellyfin.local")
        robot.clickText("Sync now")
        robot.clickText("tim@jellyfin.local")
        robot.clickText("Remove")

        robot.syncClicks shouldBe 1
        robot.signInClicks shouldBe 1
        robot.removeClicks shouldBe 1
    }

    @Test
    fun `a server's page closes when the server disconnects while it's open`() {
        val server = SourcesScenarios.configured.servers.first { it.connected }
        robot.setServerDetail(server)

        robot.updateServerDetail(server.copy(connected = false))

        robot.navigateUpClicks shouldBe 1
    }

    @Test
    fun `a server's page waits for the sources to load rather than closing`() {
        val server = SourcesScenarios.configured.servers.first { it.connected }
        robot.setServerDetail(server.copy(connected = false))

        robot.updateServerDetail(server)

        robot.navigateUpClicks shouldBe 0
        robot.assertTextDisplayed("tim@jellyfin.local")
    }

    @Test
    fun `a syncing server's page can't start another sync`() {
        robot.setServerDetail(SourcesScenarios.scanning.servers.first { it.connected })

        robot.assertTextDisplayed("Syncing… 600 of 1,842 songs")
        robot.assertSyncEnabled(false)
    }

    @Test
    fun `this device shows its songs and when the library last updated`() {
        robot.setThisDevice(SourcesScenarios.configured)

        robot.assertTextDisplayed("1,234 songs · Updated 2 hours ago")
        robot.assertScanNowEnabled(true)
        robot.clickText("Scan now")

        robot.rescanClicks shouldBe 1
    }

    @Test
    fun `a scan shows its progress on this device's page`() {
        robot.setThisDevice(SourcesScenarios.scanning)

        robot.assertTextDisplayed("Scanning… 340 of 1,234 songs")
        robot.assertProgressShown()
        robot.assertScanNowEnabled(false)
    }

    @Test
    fun `this device's page turns it on`() {
        robot.setThisDevice(SourcesUiState(thisDevice = false))

        robot.clickTag("sources-this-device")

        robot.lastThisDevice shouldBe true
    }

    @Test
    fun `turning this device off asks first`() {
        robot.setThisDevice(SourcesUiState(thisDevice = true))

        robot.clickTag("sources-this-device")

        robot.lastThisDevice shouldBe null
        robot.lastDialog shouldBe ThisDeviceDialog.TurnOff
    }

    @Test
    fun `files this device couldn't read show with a retry`() {
        robot.setThisDevice(SourcesScenarios.configured.copy(deviceSkippedFiles = 2))

        robot.assertTextDisplayed("2 files couldn't be read and were left out")
        robot.clickText("Try again")

        robot.retrySkippedClicks shouldBe 1
    }

    @Test
    fun `no files left unread shows no retry`() {
        robot.setThisDevice(SourcesScenarios.configured)

        robot.assertTextNotDisplayed("Try again")
    }

    @Test
    fun `the Android provider says folder rules don't apply`() {
        robot.setThisDevice(SourcesUiState(thisDevice = true, usesAndroidProvider = true))

        robot.assertTextDisplayed("Android's media library finds this device's music, so folder rules don't apply")
        robot.assertTextNotDisplayed("Exclude folders")
    }

    @Test
    fun `a failed scan shows its error and scans again`() {
        robot.setThisDevice(SourcesUiState(thisDevice = true, deviceStatus = SourceStatus.Failed("Couldn't read storage")))

        robot.assertTextDisplayed("Last scan failed: Couldn't read storage")
        robot.clickText("Scan now")

        robot.rescanClicks shouldBe 1
    }

    @Test
    fun `this device's folder rules add folders in plain words`() {
        robot.setThisDevice(SourcesScenarios.configured)

        robot.assertTextDisplayed("Scan only these folders")
        robot.assertTextDisplayed("Every folder is scanned")
        robot.assertTextDisplayed("Exclude folders")
        robot.assertTextDisplayed("Extra folders")
        robot.clickText("Add folder", index = 2)

        robot.lastAddFolder shouldBe FolderKind.Extra
    }

    @Test
    fun `a folder whose access was revoked is flagged and offers to fix it`() {
        val folder = revokedFolder
        robot.setThisDevice(SourcesUiState(thisDevice = true, folders = FolderLists(includes = listOf(folder))))
        robot.assertTextDisplayed("Access removed. Tap to fix")
        robot.clickText("Music")

        robot.lastDialog shouldBe ThisDeviceDialog.RevokedFolder(FolderKind.Include, folder)
    }
}
