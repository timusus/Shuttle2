package com.simplecityapps.shuttle.ui.screens.library

import androidx.compose.ui.test.junit4.createComposeRule
import com.simplecityapps.createAlbum
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AlbumArtistDetailScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val robot = LibraryDetailRobot(composeTestRule)

    @Test
    fun `shows the artist's album and song counts, then Albums and Songs sections`() {
        robot.setAlbumArtist(readyAlbumArtistDetail())

        robot.assertTextDisplayed("Radiohead")
        robot.assertTextDisplayed("1 album · 3 songs")
        robot.assertTextDisplayed("Albums")
        robot.assertTextDisplayed("OK Computer")
        robot.scrollTo("Songs")
        robot.assertTextDisplayed("Songs")
    }

    @Test
    fun `tapping an album reports it`() {
        val state = readyAlbumArtistDetail()
        robot.setAlbumArtist(state)

        robot.clickText("OK Computer")

        robot.lastAlbumClicked shouldBe state.albums[0]
    }

    @Test
    fun `an unfolded album lists its songs, which play within the album`() {
        val album = createAlbum(name = "OK Computer", albumArtist = "Radiohead", songCount = 3)
        val songs = okComputerSongs()
        robot.setAlbumArtist(readyAlbumArtistDetail(albums = listOf(album), songs = songs, expandedAlbums = setOfNotNull(album.groupKey)))

        robot.clickText("Paranoid Android")

        robot.lastPlayed shouldBe (songs to 1)
    }

    @Test
    fun `Play plays every song and the overflow opens the artist's actions`() {
        val state = readyAlbumArtistDetail()
        robot.setAlbumArtist(state)

        robot.clickPlay()
        robot.lastPlayed shouldBe (state.songs to 0)

        robot.clickOverflow()
        robot.lastMore shouldBe state.albumArtist
    }

    @Test
    fun `while loading there is no Play button`() {
        robot.setAlbumArtist(loadingAlbumArtistDetail)

        robot.assertTextNotDisplayed("Play")
    }
}
