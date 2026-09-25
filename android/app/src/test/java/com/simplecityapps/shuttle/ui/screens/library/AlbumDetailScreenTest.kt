package com.simplecityapps.shuttle.ui.screens.library

import androidx.compose.ui.test.junit4.createComposeRule
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AlbumDetailScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val robot = LibraryDetailRobot(composeTestRule)

    @Test
    fun `shows the album, its artist, year and songs`() {
        robot.setAlbum(readyAlbumDetail())

        robot.assertTextDisplayed("OK Computer")
        robot.assertTextDisplayed("Radiohead · 1997 · 3 songs", substring = true)
        robot.assertTextDisplayed("Airbag")
        robot.assertTextDisplayed("Paranoid Android")
    }

    @Test
    fun `Play starts the album from the top and a song plays from its position`() {
        val songs = okComputerSongs()
        robot.setAlbum(readyAlbumDetail(songs = songs))

        robot.clickPlay()
        robot.lastPlayed shouldBe (songs to 0)

        robot.clickText("Paranoid Android")
        robot.lastPlayed shouldBe (songs to 1)
    }

    @Test
    fun `Shuffle shuffles the album`() {
        robot.setAlbum(readyAlbumDetail())

        robot.clickShuffle()

        robot.shuffleClicked shouldBe true
    }

    @Test
    fun `the overflow opens the album's actions and a row's opens the song's`() {
        val state = readyAlbumDetail()
        robot.setAlbum(state)

        robot.clickOverflow()
        robot.lastMore shouldBe state.album

        robot.clickRowMore(1)
        robot.lastMore shouldBe state.songs[1]
    }

    @Test
    fun `a two-disc album groups its songs under disc headers`() {
        robot.setAlbum(readyAlbumDetail(songs = okComputerSongs(disc = 1) + okComputerSongs(disc = 2)))

        robot.assertTextDisplayed("Disc 1")
        robot.scrollTo("Disc 2")
        robot.assertTextDisplayed("Disc 2")
    }

    @Test
    fun `a one-disc album has no disc header`() {
        robot.setAlbum(readyAlbumDetail())

        robot.assertTextNotDisplayed("Disc 1")
    }

    @Test
    fun `the playing song is marked`() {
        val songs = okComputerSongs()
        robot.setAlbum(readyAlbumDetail(songs = songs, currentSong = songs[0]))

        robot.assertNowPlayingShown()
    }

    @Test
    fun `while loading there is no Play button`() {
        robot.setAlbum(loadingAlbumDetail)

        robot.assertTextNotDisplayed("Play")
    }

    @Test
    fun `an album that is gone says it is not in the library and navigates up`() {
        robot.setAlbum(missingAlbumDetail)

        robot.assertTextDisplayed("Not in your library")
        robot.clickNavigateUp()

        robot.navigatedUp shouldBe true
    }
}
