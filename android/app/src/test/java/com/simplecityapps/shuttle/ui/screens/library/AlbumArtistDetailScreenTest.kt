package com.simplecityapps.shuttle.ui.screens.library

import androidx.compose.ui.test.junit4.createComposeRule
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class AlbumArtistDetailScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val robot = LibraryDetailRobot(composeTestRule)

    @Test
    fun `shows the artist's album and song counts, then Albums and Songs sections`() {
        robot.setAlbumArtist(readyAlbumArtistDetail())

        robot.assertTextDisplayed("Juniper Static")
        robot.assertTextDisplayed("1 album · 3 songs")
        robot.assertTextDisplayed("Albums")
        robot.assertTextDisplayed("Phase Garden")
        robot.scrollTo("Songs")
        robot.assertTextDisplayed("Songs")
    }

    @Test
    fun `tapping an album reports it`() {
        val state = readyAlbumArtistDetail()
        robot.setAlbumArtist(state)

        robot.clickText("Phase Garden")

        robot.lastAlbumClicked shouldBe state.albums[0]
    }

    @Test
    fun `a folded album shows its songs only in the Songs section`() {
        robot.setAlbumArtist(readyAlbumArtistDetail())

        robot.assertTextShownTimes("Soft Machines at Dawn", 1)
    }

    @Test
    @Config(qualifiers = "w411dp-h2000dp") // tall enough that the Songs section below the unfolded album is composed
    fun `an unfolded album lists its songs, which play within the album`() {
        val songs = phaseGardenSongs()
        val album = albumOf(songs)
        robot.setAlbumArtist(readyAlbumArtistDetail(songs = songs, albums = listOf(album), expandedAlbums = setOfNotNull(album.groupKey)))

        robot.assertTextShownTimes("Soft Machines at Dawn", 2)
        robot.clickText("Soft Machines at Dawn")

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
    fun `Shuffle shuffles every song by the artist`() {
        robot.setAlbumArtist(readyAlbumArtistDetail())

        robot.clickShuffle()

        robot.shuffleClicked shouldBe true
    }

    @Test
    fun `while loading there is no Play button`() {
        robot.setAlbumArtist(loadingAlbumArtistDetail)

        robot.assertTextNotDisplayed("Play")
    }
}
