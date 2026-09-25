package com.simplecityapps.shuttle.ui.screens.library

import androidx.compose.ui.test.junit4.createComposeRule
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SmartPlaylistDetailScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val robot = LibraryDetailRobot(composeTestRule)

    @Test
    fun `shows the smart playlist's name and songs`() {
        robot.setSmartPlaylist(readySmartPlaylistDetail())

        robot.assertTextDisplayed("Recently Added")
        robot.assertTextDisplayed("3 songs", substring = true)
        robot.assertTextDisplayed("Chlorophyll Loop")
    }

    @Test
    fun `a song plays from its position and Play from the top`() {
        val state = readySmartPlaylistDetail()
        robot.setSmartPlaylist(state)

        robot.clickText("Soft Machines at Dawn")
        robot.lastPlayed shouldBe (state.songs to 1)

        robot.clickPlay()
        robot.lastPlayed shouldBe (state.songs to 0)
    }

    @Test
    fun `the overflow opens the smart playlist's actions and a row's opens the song's`() {
        val state = readySmartPlaylistDetail()
        robot.setSmartPlaylist(state)

        robot.clickOverflow()
        robot.lastMore shouldBe state.smartPlaylist

        robot.clickRowMore(0)
        robot.lastMore shouldBe state.songs[0]
    }

    @Test
    fun `a smart playlist that no longer resolves says so`() {
        robot.setSmartPlaylist(missingSmartPlaylistDetail)

        robot.assertTextDisplayed("Not in your library")
    }
}
