package com.simplecityapps.shuttle.ui.screens.settings.downloads

import androidx.compose.ui.test.junit4.createComposeRule
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Render smoke test for [DownloadsScreen]; the decisions are covered by `DownloadsViewStateTest`. */
@RunWith(RobolectricTestRunner::class)
class DownloadsScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val robot = DownloadsRobot(composeTestRule)

    @Test
    fun `renders the albums with formatted sizes and tapping one opens it`() {
        val albums = listOf(downloadedAlbum(), downloadedAlbum(name = "Low Tide", songCount = 1, bytes = 8_000_000L))
        robot.setContent(readyDownloads(albums))

        robot.assertDisplayed("88 MB used")
        robot.assertDisplayed("Night Drive")
        robot.assertDisplayed("10 songs · 80 MB")
        robot.assertDisplayed("1 song · 8.0 MB")

        robot.tapText("Low Tide")

        robot.openedAlbum shouldBe albums[1]
    }
}
