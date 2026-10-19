package com.simplecityapps.shuttle.ui.screens.settings.downloads

import androidx.compose.ui.test.junit4.createComposeRule
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Render tests for [DownloadsScreen]: the resolved copy and sizes, and each control reaching its event.
 * The decisions are covered by `DownloadsViewStateTest`, and where each event goes by `DownloadsEventsTest`.
 */
@RunWith(RobolectricTestRunner::class)
class DownloadsScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val robot = DownloadsRobot(composeTestRule)

    // codehealth-ignore tests-slow-kind-fast-subject: P2 wiring; the empty copy is a string resource and Remove all a top-bar action, both only in the rendered tree.
    @Test
    fun `shows the empty state with nothing downloaded`() {
        robot.setContent(emptyDownloads())

        robot.assertDisplayed("No downloads yet")
        robot.assertRemoveAllAvailable(false)
    }

    // codehealth-ignore tests-slow-kind-fast-subject: P2 wiring; the sizes come from Android's Formatter and the song count from a plural, both resolved only in the rendered tree.
    @Test
    fun `lists the downloaded albums with their song counts`() {
        robot.setContent(readyDownloads())

        robot.assertDisplayed("Night Drive")
        robot.assertDisplayed("Low Tide")
        robot.assertDisplayed("10 songs · 80 MB")
        robot.assertDisplayed("1 song · 8.0 MB")
    }

    // codehealth-ignore tests-slow-kind-fast-subject: P2 wiring; the size comes from Android's Formatter, which only resolves in the rendered tree.
    @Test
    fun `shows the storage downloads use`() {
        robot.setContent(readyDownloads(storageBytes = 88_000_000L))

        robot.assertDisplayed("88 MB used")
    }

    // codehealth-ignore tests-slow-kind-fast-subject: P2 wiring; the confirm dialog is a window only the rendered tree shows, and its confirm must reach onEvent.
    @Test
    fun `remove all asks first and then removes`() {
        robot.setContent(readyDownloads())

        robot.tapRemoveAll()
        robot.removeAllCount shouldBe 0
        robot.assertDisplayed("Remove all downloads?")
        robot.tapText("Remove all")

        robot.removeAllCount shouldBe 1
        robot.assertNotShown("Remove all downloads?")
    }

    // codehealth-ignore tests-slow-kind-fast-subject: P2 wiring; the dialog's Cancel button exists only in the rendered tree.
    @Test
    fun `cancelling remove all keeps the downloads`() {
        robot.setContent(readyDownloads())

        robot.tapRemoveAll()
        robot.tapText("Cancel")

        robot.removeAllCount shouldBe 0
        robot.assertDisplayed("Night Drive")
    }

    // codehealth-ignore tests-slow-kind-fast-subject: P2 wiring; checks a row's click reaches onEvent with its album.
    @Test
    fun `tapping an album opens it`() {
        val albums = listOf(downloadedAlbum(), downloadedAlbum(name = "Low Tide", songCount = 1))
        robot.setContent(readyDownloads(albums))

        robot.tapText("Low Tide")

        robot.openedAlbum shouldBe albums[1]
    }

    // codehealth-ignore tests-slow-kind-fast-subject: P2 wiring; the size comes from Android's Formatter and Remove all is a top-bar action, both only in the rendered tree.
    @Test
    fun `storage held without a complete album still shows the storage and Remove all, not the empty state`() {
        robot.setContent(DownloadsUiState(storageBytes = 5_000_000L, loading = false))

        robot.assertDisplayed("5.0 MB used")
        robot.assertRemoveAllAvailable(true)
        robot.assertNotShown("No downloads yet")
    }
}
