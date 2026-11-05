package com.simplecityapps.shuttle.ui.screens.library

import androidx.compose.ui.test.junit4.createComposeRule
import com.simplecityapps.createSmartPlaylist
import com.simplecityapps.createSong
import com.simplecityapps.shuttle.model.SmartPlaylistId
import com.simplecityapps.shuttle.sorting.SongListSort
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
        robot.assertTextDisplayed("3 songs", substring = true)
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
    fun `flat the songs header names the playlist's own order and its menu picks a sort`() {
        robot.setSmartPlaylist(readySmartPlaylistDetail(smartPlaylist = createSmartPlaylist(SmartPlaylistId.Favourites)))

        robot.assertTextDisplayed("Songs")
        robot.assertTextDisplayed("Recent")
        robot.openPlaylistSortMenu()
        robot.assertTextDisplayed("Recently favourited")
        robot.clickMenuItem("Album title")

        robot.lastPlaylistSort shouldBe SongListSort.AlbumTitle
    }

    @Test
    fun `grouped the songs fold under album headers and play in the grouped order`() {
        val songs = listOf(
            createSong(id = 1, name = "Chlorophyll Loop", albumArtist = "Juniper Static", album = "Phase Garden", track = 1),
            createSong(id = 2, name = "Night Bus", albumArtist = "Juniper Static", album = "Amber Lines", track = 1),
        )
        val collapsed = readySmartPlaylistDetail(songs = songs, sort = SongListSort.AlbumTitle)
        robot.setSmartPlaylist(collapsed.copy(expandedAlbums = setOfNotNull(collapsed.sections[0].album?.groupKey)))

        robot.assertTextDisplayed("Albums")
        robot.assertTextAbove("Amber Lines", "Phase Garden")
        robot.assertTextNotDisplayed("Chlorophyll Loop")
        robot.clickText("Night Bus")

        robot.lastPlayed?.first?.map { it.name } shouldBe listOf("Night Bus", "Chlorophyll Loop")
        robot.lastPlayed?.second shouldBe 0
    }

    @Test
    fun `a smart playlist that no longer resolves says so`() {
        robot.setSmartPlaylist(missingSmartPlaylistDetail)

        robot.assertTextDisplayed("Not in your library")
    }
}
