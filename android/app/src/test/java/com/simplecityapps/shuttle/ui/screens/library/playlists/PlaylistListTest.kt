package com.simplecityapps.shuttle.ui.screens.library.playlists

import androidx.compose.ui.test.junit4.createComposeRule
import com.simplecityapps.createPlaylist
import com.simplecityapps.createSmartPlaylist
import com.simplecityapps.mediaprovider.Progress
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaylistListTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val robot = PlaylistListRobot(composeTestRule)

    // region View state rendering

    @Test
    fun `loading state shows loading indicator`() {
        robot.setContent(loadingPlaylistList)
        robot.assertTextDisplayed("Loading…")
    }

    @Test
    fun `scanning state shows scan progress message`() {
        robot.setContent(scanningPlaylistList(Progress(10, 100)))
        robot.assertTextDisplayed("Scanning your library")
    }

    @Test
    fun `scanning state with null progress shows scan message`() {
        robot.setContent(scanningPlaylistList())
        robot.assertTextDisplayed("Scanning your library")
    }

    @Test
    fun `empty state shows empty message`() {
        robot.setContent(emptyPlaylistList())
        robot.assertTextDisplayed("No playlists")
    }

    @Test
    fun `ready state shows playlist name`() {
        robot.setContent(readyPlaylistList(playlists = listOf(createPlaylist(name = "Favorites"))))
        robot.assertTextDisplayed("Favorites")
    }

    @Test
    fun `ready state shows song count for single song`() {
        robot.setContent(readyPlaylistList(playlists = listOf(createPlaylist(name = "Solo", songCount = 1))))
        robot.assertTextDisplayed("1 song")
    }

    @Test
    fun `ready state shows song count for multiple songs`() {
        robot.setContent(readyPlaylistList(playlists = listOf(createPlaylist(name = "Mix", songCount = 42))))
        robot.assertTextDisplayed("42 songs")
    }

    @Test
    fun `ready state shows empty songs message for playlist with zero songs`() {
        robot.setContent(readyPlaylistList(playlists = listOf(createPlaylist(name = "Empty", songCount = 0))))
        robot.assertTextDisplayed("No songs")
    }

    @Test
    fun `ready state shows multiple playlists`() {
        robot.setContent(
            readyPlaylistList(
                playlists = listOf(
                    createPlaylist(name = "Rock Hits"),
                    createPlaylist(name = "Chill Vibes"),
                    createPlaylist(name = "Workout"),
                )
            )
        )
        robot.assertTextDisplayed("Rock Hits")
        robot.assertTextDisplayed("Chill Vibes")
        robot.assertTextDisplayed("Workout")
    }

    @Test
    fun `ready state shows smart playlist name`() {
        robot.setContent(
            readyPlaylistList(
                playlists = emptyList(),
                smartPlaylists = listOf(createSmartPlaylist()),
            )
        )
        robot.assertTextDisplayed("Recently Added")
    }

    @Test
    fun `ready state shows section headers when both types present`() {
        robot.setContent(
            readyPlaylistList(
                playlists = listOf(createPlaylist(name = "My Mix")),
                smartPlaylists = listOf(createSmartPlaylist()),
            )
        )
        robot.assertTextDisplayed("Smart Playlists")
        robot.assertTextDisplayed("Playlists")
    }

    @Test
    fun `ready state hides Playlists header when no regular playlists`() {
        robot.setContent(
            readyPlaylistList(
                playlists = emptyList(),
                smartPlaylists = listOf(createSmartPlaylist()),
            )
        )
        robot.assertTextDisplayed("Smart Playlists")
        robot.assertTextNotDisplayed("Playlists")
    }

    @Test
    fun `ready state hides Smart Playlists header when no smart playlists`() {
        robot.setContent(
            readyPlaylistList(
                playlists = listOf(createPlaylist(name = "My Mix")),
                smartPlaylists = emptyList(),
            )
        )
        robot.assertTextNotDisplayed("Smart Playlists")
    }

    // endregion

    // region Callbacks

    @Test
    fun `clicking playlist invokes onPlaylistClick`() {
        val playlist = createPlaylist(name = "Favorites")
        robot.setContent(readyPlaylistList(playlists = listOf(playlist)))
        robot.clickText("Favorites")
        robot.lastClickedPlaylist shouldBe playlist
    }

    @Test
    fun `clicking smart playlist invokes onSmartPlaylistClick`() {
        val smartPlaylist = createSmartPlaylist()
        robot.setContent(
            readyPlaylistList(
                playlists = emptyList(),
                smartPlaylists = listOf(smartPlaylist),
            )
        )
        robot.clickText("Recently Added")
        robot.lastClickedSmartPlaylist shouldBe smartPlaylist
    }

    // endregion

    // region Context menu (rendered via PlaylistListItem — see PlaylistListRobot doc)

    @Test
    fun `context menu shows standard items`() {
        robot.setItemContent(playlist = createPlaylist())
        robot.openContextMenu()

        robot.assertTextDisplayed("Play")
        robot.assertTextDisplayed("Add to Queue")
        robot.assertTextDisplayed("Play Next")
        robot.assertTextDisplayed("Delete")
        robot.assertTextDisplayed("Clear")
        robot.assertTextDisplayed("Rename")
    }

    @Test
    fun `context menu invokes onPlay`() {
        val playlist = createPlaylist(name = "Rock")
        robot.setItemContent(playlist = playlist)
        robot.openContextMenu()
        robot.clickMenuItem("Play")
        robot.lastPlayedPlaylist shouldBe playlist
    }

    @Test
    fun `context menu invokes onAddToQueue`() {
        val playlist = createPlaylist(name = "Jazz")
        robot.setItemContent(playlist = playlist)
        robot.openContextMenu()
        robot.clickMenuItem("Add to Queue")
        robot.lastAddedToQueue shouldBe playlist
    }

    @Test
    fun `context menu invokes onPlayNext`() {
        val playlist = createPlaylist(name = "Blues")
        robot.setItemContent(playlist = playlist)
        robot.openContextMenu()
        robot.clickMenuItem("Play Next")
        robot.lastPlayNext shouldBe playlist
    }

    @Test
    fun `context menu invokes onDelete`() {
        val playlist = createPlaylist(name = "Old Stuff")
        robot.setItemContent(playlist = playlist)
        robot.openContextMenu()
        robot.clickMenuItem("Delete")
        robot.lastDeletedPlaylist shouldBe playlist
    }

    @Test
    fun `context menu invokes onClear`() {
        val playlist = createPlaylist(name = "Cleanup")
        robot.setItemContent(playlist = playlist)
        robot.openContextMenu()
        robot.clickMenuItem("Clear")
        robot.lastClearedPlaylist shouldBe playlist
    }

    @Test
    fun `context menu invokes onRename`() {
        val playlist = createPlaylist(name = "Rename Me")
        robot.setItemContent(playlist = playlist)
        robot.openContextMenu()
        robot.clickMenuItem("Rename")
        robot.lastRenamedPlaylist shouldBe playlist
    }

    // endregion
}
