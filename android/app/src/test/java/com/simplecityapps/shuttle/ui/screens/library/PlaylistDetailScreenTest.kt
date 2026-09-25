package com.simplecityapps.shuttle.ui.screens.library

import androidx.compose.ui.test.junit4.createComposeRule
import com.simplecityapps.createPlaylist
import com.simplecityapps.shuttle.sorting.PlaylistSongSortOrder
import com.simplecityapps.shuttle.ui.actions.MediaActionType
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaylistDetailScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val robot = LibraryDetailRobot(composeTestRule)

    @Test
    fun `shows the playlist, its song count and its songs`() {
        robot.setPlaylist(readyPlaylistDetail())

        robot.assertTextDisplayed("Road trip")
        robot.assertTextDisplayed("3 songs", substring = true)
        robot.assertTextDisplayed("Chlorophyll Loop")
    }

    @Test
    fun `in its custom order each row has a drag handle and there is no sort hint`() {
        robot.setPlaylist(readyPlaylistDetail())

        robot.assertReorderHandlesShown()
        robot.assertTextNotDisplayed("Choose Custom to reorder", substring = true)
    }

    @Test
    fun `sorted another way there are no handles and a hint says how to reorder`() {
        robot.setPlaylist(readyPlaylistDetail(playlist = createPlaylist(id = 7, name = "Road trip", sortOrder = PlaylistSongSortOrder.SongName)))

        robot.assertNoReorderHandles()
        robot.assertTextDisplayed("Sorted by Song Name. Choose Custom to reorder.")
    }

    @Test
    fun `a song plays from its position`() {
        val state = readyPlaylistDetail()
        robot.setPlaylist(state)

        robot.clickText("Soft Machines at Dawn")

        robot.lastPlayed shouldBe (state.songs.map { it.song } to 1)
    }

    @Test
    fun `a long press toggles a song's selection`() {
        val state = readyPlaylistDetail()
        robot.setPlaylist(state)

        robot.longClickText("Chlorophyll Loop")

        robot.lastToggled shouldBe state.songs[0]
    }

    @Test
    fun `a selection shows the toolbar, whose Remove and actions report`() {
        val state = readyPlaylistDetail(selectedIds = setOf(10L, 11L))
        robot.setPlaylist(state)

        robot.assertTextDisplayed("2 selected")
        robot.clickContentDescription("Remove from playlist")
        robot.removedSelected shouldBe true

        robot.clickContentDescription("Add to Queue")
        robot.lastSelectionAction shouldBe MediaActionType.AddToQueue
    }

    @Test
    fun `back while selecting clears the selection rather than leaving the playlist`() {
        robot.setPlaylist(readyPlaylistDetail(selectedIds = setOf(10L)))

        robot.pressBack()

        robot.selectionCleared shouldBe true
        robot.navigatedUp shouldBe false
    }

    @Test
    fun `without a selection back is left to the back stack`() {
        robot.setPlaylist(readyPlaylistDetail())

        robot.backIsHandled() shouldBe false
    }

    @Test
    fun `while selecting a tap toggles rather than plays`() {
        val state = readyPlaylistDetail(playlist = createPlaylist(id = 7, name = "Road trip", sortOrder = PlaylistSongSortOrder.SongName), selectedIds = setOf(10L))
        robot.setPlaylist(state)

        robot.clickText("Soft Machines at Dawn")

        robot.lastToggled shouldBe state.songs[1]
        robot.lastPlayed shouldBe null
    }

    @Test
    fun `the sort menu picks a sort, flips Descending and exports`() {
        robot.setPlaylist(readyPlaylistDetail())

        robot.openSortMenu()
        robot.clickMenuItem("Song Name")
        robot.lastSortOrder shouldBe PlaylistSongSortOrder.SongName

        robot.openSortMenu()
        robot.clickMenuItem("Descending")
        robot.lastDescending shouldBe true

        robot.openSortMenu()
        robot.clickMenuItem("Export as m3u", substring = true)
        robot.exported shouldBe true
    }

    @Test
    fun `the overflow opens the playlist's actions`() {
        val state = readyPlaylistDetail()
        robot.setPlaylist(state)

        robot.clickOverflow()

        robot.lastMore shouldBe state.playlist
    }

    @Test
    fun `while loading there is no Play button`() {
        robot.setPlaylist(loadingPlaylistDetail)

        robot.assertTextNotDisplayed("Play")
    }
}
