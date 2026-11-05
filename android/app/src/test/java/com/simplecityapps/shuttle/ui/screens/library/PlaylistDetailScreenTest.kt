package com.simplecityapps.shuttle.ui.screens.library

import androidx.compose.ui.test.junit4.createComposeRule
import com.simplecityapps.createSong
import com.simplecityapps.shuttle.designsystem.component.SongOfflineState
import com.simplecityapps.shuttle.sorting.SongListSort
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
        robot.assertTextDisplayed("3 songs", substring = true)
        robot.assertTextDisplayed("Chlorophyll Loop")
    }

    @Test
    fun `in its custom order each row has a drag handle and there is no sort hint`() {
        robot.setPlaylist(readyPlaylistDetail())

        robot.assertReorderHandlesShown()
        robot.assertTextNotDisplayed("Choose Custom to reorder", substring = true)
    }

    @Test
    fun `in its custom order a downloaded song's row shows it is available offline`() {
        val entries = playlistEntries()
        robot.setPlaylist(readyPlaylistDetail(songs = entries), offline = mapOf(entries[0].song.path to SongOfflineState.Offline))

        robot.assertContentDescriptionShown("Available offline")
    }

    @Test
    fun `sorted another way there are no handles and a hint says how to reorder`() {
        robot.setPlaylist(readyPlaylistDetail(sort = SongListSort.SongTitle))

        robot.assertNoReorderHandles()
        robot.assertTextDisplayed("Sorted by Song title. Choose Custom to reorder.")
    }

    @Test
    fun `grouped by album the songs fold under their albums with no handles`() {
        val songs = twoAlbumEntries()
        val collapsed = readyPlaylistDetail(songs = songs, sort = SongListSort.AlbumTitle)
        robot.setPlaylist(collapsed.copy(expandedAlbums = setOfNotNull(collapsed.sections[0].album?.groupKey)))

        robot.assertTextDisplayed("Albums")
        robot.assertTextAbove("Amber Lines", "Night Bus")
        robot.assertTextAbove("Night Bus", "Phase Garden")
        robot.assertTextNotDisplayed("Chlorophyll Loop")
        robot.assertNoReorderHandles()
        robot.assertTextDisplayed("Choose Custom to reorder", substring = true)
    }

    @Test
    fun `grouped a song plays from its place in the grouped order and an album header folds`() {
        val collapsed = readyPlaylistDetail(songs = twoAlbumEntries(), sort = SongListSort.AlbumTitle)
        val state = collapsed.copy(expandedAlbums = collapsed.sections.mapNotNull { it.album?.groupKey }.toSet())
        robot.setPlaylist(state)

        robot.clickText("Chlorophyll Loop")
        robot.lastPlayed?.first?.map { it.name } shouldBe listOf("Night Bus", "Chlorophyll Loop")
        robot.lastPlayed?.second shouldBe 1

        robot.clickText("Phase Garden")
        robot.lastAlbumToggled?.name shouldBe "Phase Garden"
    }

    @Test
    fun `grouped Collapse all folds every album`() {
        val collapsed = readyPlaylistDetail(songs = twoAlbumEntries(), sort = SongListSort.AlbumTitle)
        robot.setPlaylist(collapsed.copy(expandedAlbums = collapsed.sections.mapNotNull { it.album?.groupKey }.toSet()))

        robot.clickContentDescription("Collapse all")

        robot.collapsedAll shouldBe true
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

        robot.clickContentDescription("Add to queue")
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
        val state = readyPlaylistDetail(selectedIds = setOf(10L), sort = SongListSort.SongTitle)
        robot.setPlaylist(state)

        robot.clickText("Soft Machines at Dawn")

        robot.lastToggled?.song?.name shouldBe "Soft Machines at Dawn"
        robot.lastPlayed shouldBe null
    }

    @Test
    fun `the songs header's sort menu picks a sort and the top bar exports`() {
        robot.setPlaylist(readyPlaylistDetail())

        robot.openPlaylistSortMenu()
        robot.clickMenuItem("Album title")
        robot.lastPlaylistSort shouldBe SongListSort.AlbumTitle

        robot.clickContentDescription("Export as m3u")
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

    /** Chlorophyll Loop on Phase Garden, then Night Bus on Amber Lines, in the playlist's own order. */
    private fun twoAlbumEntries() = playlistEntries(
        listOf(
            createSong(id = 1, name = "Chlorophyll Loop", albumArtist = "Juniper Static", album = "Phase Garden", track = 1),
            createSong(id = 2, name = "Night Bus", albumArtist = "Juniper Static", album = "Amber Lines", track = 1),
        ),
    )
}
