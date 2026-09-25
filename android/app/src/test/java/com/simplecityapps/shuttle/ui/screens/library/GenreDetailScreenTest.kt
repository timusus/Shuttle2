package com.simplecityapps.shuttle.ui.screens.library

import androidx.compose.ui.test.junit4.createComposeRule
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GenreDetailScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val robot = LibraryDetailRobot(composeTestRule)

    @Test
    fun `shows the genre, its song count, its albums and its songs`() {
        robot.setGenre(readyGenreDetail())

        robot.assertTextDisplayed("Electronic")
        robot.assertTextDisplayed("3 songs", substring = true)
        robot.assertTextDisplayed("Phase Garden")
        robot.scrollTo("Chlorophyll Loop")
        robot.assertTextDisplayed("Chlorophyll Loop")
    }

    @Test
    fun `tapping an album opens it and a song plays from its position`() {
        val state = readyGenreDetail()
        robot.setGenre(state)

        robot.clickText("Phase Garden")
        robot.lastAlbumClicked shouldBe state.albums[0]

        robot.clickText("Petal Arithmetic")
        robot.lastPlayed shouldBe (state.songs to 2)
    }

    @Test
    fun `Shuffle shuffles and the overflow opens the genre's actions`() {
        val state = readyGenreDetail()
        robot.setGenre(state)

        robot.clickShuffle()
        robot.shuffleClicked shouldBe true

        robot.clickOverflow()
        robot.lastMore shouldBe state.genre
    }

    @Test
    fun `a genre that is gone says it is not in the library`() {
        robot.setGenre(missingGenreDetail)

        robot.assertTextDisplayed("Not in your library")
    }
}
