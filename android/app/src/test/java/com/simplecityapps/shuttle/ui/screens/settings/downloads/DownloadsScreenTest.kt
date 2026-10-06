package com.simplecityapps.shuttle.ui.screens.settings.downloads

import androidx.compose.ui.test.junit4.createComposeRule
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Characterisation tests for [DownloadsScreen]. */
@RunWith(RobolectricTestRunner::class)
class DownloadsScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val robot = DownloadsRobot(composeTestRule)

    @Test
    fun `shows the empty state with nothing downloaded`() {
        robot.setContent(emptyDownloads())

        robot.assertDisplayed("No downloads yet")
        robot.assertRemoveAllAvailable(false)
    }

    @Test
    fun `lists the downloaded albums with their song counts`() {
        robot.setContent(readyDownloads())

        robot.assertDisplayed("Night Drive")
        robot.assertDisplayed("Low Tide")
        robot.assertDisplayed("10 songs · 80 MB")
        robot.assertDisplayed("1 song · 8.0 MB")
    }

    @Test
    fun `shows the storage downloads use`() {
        robot.setContent(readyDownloads(storageBytes = 88_000_000L))

        robot.assertDisplayed("88 MB used")
    }

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

    @Test
    fun `cancelling remove all keeps the downloads`() {
        robot.setContent(readyDownloads())

        robot.tapRemoveAll()
        robot.tapText("Cancel")

        robot.removeAllCount shouldBe 0
        robot.assertDisplayed("Night Drive")
    }

    @Test
    fun `tapping an album opens it`() {
        val albums = listOf(downloadedAlbum(), downloadedAlbum(name = "Low Tide", songCount = 1))
        robot.setContent(readyDownloads(albums))

        robot.tapText("Low Tide")

        robot.openedAlbum shouldBe albums[1]
    }

    @Test
    fun `storage held without a complete album still shows the storage and Remove all, not the empty state`() {
        robot.setContent(DownloadsUiState(storageBytes = 5_000_000L, loading = false))

        robot.assertDisplayed("5.0 MB used")
        robot.assertRemoveAllAvailable(true)
        robot.assertNotShown("No downloads yet")
    }
}
