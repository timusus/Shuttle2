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
}
