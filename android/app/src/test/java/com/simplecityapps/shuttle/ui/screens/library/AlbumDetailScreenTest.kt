package com.simplecityapps.shuttle.ui.screens.library

import androidx.compose.ui.test.junit4.createComposeRule
import com.simplecityapps.createAlbum
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

        robot.assertTextDisplayed("Phase Garden")
        robot.assertTextDisplayed("Juniper Static · 2021 · 3 songs", substring = true)
        robot.assertTextDisplayed("Chlorophyll Loop")
        robot.assertTextDisplayed("Soft Machines at Dawn")
    }

    @Test
    fun `Play starts the album from the top and a song plays from its position`() {
        val songs = phaseGardenSongs()
        robot.setAlbum(readyAlbumDetail(songs = songs))

        robot.clickPlay()
        robot.lastPlayed shouldBe (songs to 0)

        robot.clickText("Soft Machines at Dawn")
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
        robot.setAlbum(readyAlbumDetail(songs = phaseGardenSongs(disc = 1) + phaseGardenSongs(disc = 2)))

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
        val songs = phaseGardenSongs()
        robot.setAlbum(readyAlbumDetail(songs = songs, currentSong = songs[0]))

        robot.assertNowPlayingShown()
    }

    @Test
    fun `More by lists the artist's other albums, a tap opens one and a long press opens its actions`() {
        val other = createAlbum(name = "Verdigris", albumArtist = "Juniper Static", year = 2019)
        robot.setAlbum(readyAlbumDetail(moreByArtist = listOf(other)))

        robot.scrollTo("More by Juniper Static")
        robot.assertTextDisplayed("More by Juniper Static")
        robot.clickText("Verdigris")
        robot.lastAlbumClicked shouldBe other

        robot.longClickText("Verdigris")
        robot.lastMore shouldBe other
    }

    @Test
    fun `without other albums by the artist there is no More by shelf`() {
        robot.setAlbum(readyAlbumDetail())

        robot.assertTextNotDisplayed("More by", substring = true)
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
