package com.simplecityapps.shuttle.ui.screens.library.folders

import androidx.compose.ui.test.junit4.createComposeRule
import com.simplecityapps.createPlaylist
import com.simplecityapps.createSong
import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FolderListTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val robot = FolderListRobot(composeTestRule)

    // region View state rendering

    @Test
    fun `loading state shows loading indicator`() {
        robot.setContent(loadingFolderList)
        robot.assertTextDisplayed("Loading…")
    }

    @Test
    fun `scanning state shows scan progress message`() {
        robot.setContent(scanningFolderList(Progress(10, 100)))
        robot.assertTextDisplayed("Scanning your library")
    }

    @Test
    fun `empty state shows empty message`() {
        robot.setContent(emptyFolderList())
        robot.assertTextDisplayed("No folders")
    }

    @Test
    fun `ready state shows folder names and song counts`() {
        robot.setContent(
            readyFolderList(
                folders = listOf(
                    createFolder("primary", "Music", songCount = 245),
                    createFolder("primary", "Podcasts", songCount = 1),
                )
            )
        )

        robot.assertTextDisplayed("Music")
        robot.assertTextDisplayed("245 songs")
        robot.assertTextDisplayed("Podcasts")
        robot.assertTextDisplayed("1 song")
    }

    @Test
    fun `storage volumes have friendly names`() {
        robot.setContent(
            readyFolderList(
                folders = listOf(
                    createFolder("primary"),
                    createFolder("1234-ABCD"),
                    createFolder("<other>"),
                )
            )
        )

        robot.assertTextDisplayed("Internal storage")
        robot.assertTextDisplayed("1234-ABCD")
        robot.assertTextDisplayed("Other locations")
    }

    @Test
    fun `folder shows its subfolders and songs`() {
        robot.setContent(
            readyFolderList(
                currentFolder = createFolder("primary", "Music"),
                folders = listOf(createFolder("primary", "Music", "Juniper Static")),
                songs = listOf(createSong(name = "Loose Track")),
            )
        )

        robot.assertTextDisplayed("Juniper Static")
        robot.assertTextDisplayed("Loose Track")
    }

    @Test
    fun `top level has no way up`() {
        robot.setContent(readyFolderList())
        robot.assertCannotNavigateUp()
    }

    @Test
    fun `subfolder shows its path relative to the storage volume`() {
        robot.setContent(readyFolderList(currentFolder = createFolder("primary", "Music", "Juniper Static"), folders = emptyList()))

        robot.assertCanNavigateUp()
        robot.assertTextDisplayed("Music/Juniper Static")
    }

    // endregion

    // region Interactions

    @Test
    fun `clicking a folder opens it`() {
        val folder = createFolder("primary", "Music")
        robot.setContent(readyFolderList(folders = listOf(folder)))

        robot.clickText("Music")

        robot.lastOpenedFolder shouldBe folder
    }

    @Test
    fun `clicking up navigates to the parent folder`() {
        robot.setContent(readyFolderList(currentFolder = createFolder("primary", "Music")))

        robot.navigateUp()

        robot.navigatedUp shouldBe true
    }

    @Test
    fun `clicking a song plays it`() {
        val song = createSong(name = "Chlorophyll Loop")
        robot.setContent(readyFolderList(currentFolder = createFolder("primary", "Music"), folders = emptyList(), songs = listOf(song)))

        robot.clickText("Chlorophyll Loop")

        robot.lastClickedSong shouldBe song
    }

    // endregion

    // region Context menu

    @Test
    fun `context menu shows folder actions`() {
        robot.setItemContent(createFolder())
        robot.openContextMenu()

        robot.assertTextDisplayed("Play")
        robot.assertTextDisplayed("Shuffle")
        robot.assertTextDisplayed("Add to Queue")
        robot.assertTextDisplayed("Add to Playlist")
        robot.assertTextDisplayed("Play Next")
    }

    @Test
    fun `context menu invokes onPlayFolder`() {
        val folder = createFolder()
        robot.setItemContent(folder)
        robot.openContextMenu()
        robot.clickMenuItem("Play")
        robot.lastPlayedFolder shouldBe folder
    }

    @Test
    fun `context menu invokes onShuffleFolder`() {
        val folder = createFolder()
        robot.setItemContent(folder)
        robot.openContextMenu()
        robot.clickMenuItem("Shuffle")
        robot.lastShuffledFolder shouldBe folder
    }

    @Test
    fun `context menu invokes onAddToQueue`() {
        val folder = createFolder()
        robot.setItemContent(folder)
        robot.openContextMenu()
        robot.clickMenuItem("Add to Queue")
        robot.lastFolderAddedToQueue shouldBe folder
    }

    @Test
    fun `context menu invokes onPlayNext`() {
        val folder = createFolder()
        robot.setItemContent(folder)
        robot.openContextMenu()
        robot.clickMenuItem("Play Next")
        robot.lastFolderPlayedNext shouldBe folder
    }

    @Test
    fun `add to playlist passes the folder path`() {
        val folder = createFolder("primary", "Music")
        val playlist = createPlaylist(name = "Favourites")
        robot.setItemContent(folder, playlists = listOf(playlist))
        robot.openContextMenu()
        robot.clickMenuItem("Add to Playlist")
        robot.clickMenuItem("Favourites")

        val (selectedPlaylist, data) = robot.lastAddToPlaylist!!
        selectedPlaylist shouldBe playlist
        data.shouldBeInstanceOf<PlaylistData.Folders>()
        data.data shouldBe listOf(listOf("primary", "Music"))
    }

    @Test
    fun `new playlist from the menu requests the create dialog`() {
        val folder = createFolder()
        robot.setItemContent(folder)
        robot.openContextMenu()
        robot.clickMenuItem("Add to Playlist")
        robot.clickMenuItem("New Playlist")
        robot.lastCreatePlaylistDialog shouldBe folder
    }

    // endregion
}
