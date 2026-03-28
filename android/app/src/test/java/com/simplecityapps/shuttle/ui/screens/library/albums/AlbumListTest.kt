package com.simplecityapps.shuttle.ui.screens.library.albums

import androidx.compose.ui.test.junit4.createComposeRule
import com.simplecityapps.createAlbum
import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.ui.screens.library.ViewMode
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AlbumListTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val robot = AlbumListRobot(composeTestRule)

    // region View state rendering

    @Test
    fun `loading state shows loading indicator`() {
        robot.setContent(loadingAlbumList)
        robot.assertTextDisplayed("Loading…")
    }

    @Test
    fun `scanning state shows scan progress message`() {
        robot.setContent(scanningAlbumList(Progress(50, 200)))
        robot.assertTextDisplayed("Scanning your library")
    }

    @Test
    fun `scanning state with null progress shows scan message`() {
        robot.setContent(scanningAlbumList())
        robot.assertTextDisplayed("Scanning your library")
    }

    @Test
    fun `ready state with empty albums shows empty message`() {
        robot.setContent(emptyAlbumList())
        robot.assertTextDisplayed("No albums")
    }

    @Test
    fun `ready state shows album name and artist`() {
        robot.setContent(readyAlbumList(albums = listOf(createAlbum(name = "Dark Side of the Moon", albumArtist = "Pink Floyd"))))
        robot.assertTextDisplayed("Dark Side of the Moon")
        robot.assertSubtextDisplayed("Pink Floyd")
    }

    @Test
    fun `ready state shows song count`() {
        robot.setContent(
            readyAlbumList(albums = listOf(createAlbum(name = "Abbey Road", songCount = 17)))
        )
        robot.assertSubtextDisplayed("17 songs")
    }

    @Test
    fun `ready state shows multiple albums`() {
        robot.setContent(
            readyAlbumList(
                albums = listOf(
                    createAlbum(name = "Album A"),
                    createAlbum(name = "Album B"),
                    createAlbum(name = "Album C"),
                )
            )
        )
        robot.assertTextDisplayed("Album A")
        robot.assertTextDisplayed("Album B")
        robot.assertTextDisplayed("Album C")
    }

    @Test
    fun `grid mode renders grid layout`() {
        robot.setContent(
            readyAlbumList(
                albums = listOf(createAlbum(name = "Grid Album")),
                viewMode = ViewMode.Grid,
            )
        )
        robot.assertGridLayout()
        robot.assertTextDisplayed("Grid Album")
    }

    @Test
    fun `list mode renders list layout`() {
        robot.setContent(
            readyAlbumList(
                albums = listOf(createAlbum(name = "List Album")),
                viewMode = ViewMode.List,
            )
        )
        robot.assertListLayout()
        robot.assertTextDisplayed("List Album")
    }

    @Test
    fun `selected album shows selection mark`() {
        val album = createAlbum(name = "Selected Album")
        robot.setItemContent(album = album, isSelected = true)
        robot.assertSelectionMarkDisplayed()
    }

    @Test
    fun `unselected album does not show selection mark`() {
        robot.setItemContent(album = createAlbum(name = "Unselected Album"), isSelected = false)
        robot.assertSelectionMarkNotDisplayed()
    }

    @Test
    fun `shuffle button present in ready state`() {
        robot.setContent(readyAlbumList(albums = listOf(createAlbum())))
        robot.assertTextDisplayed("Shuffle")
    }

    // endregion

    // region Callbacks

    @Test
    fun `clicking album invokes onAlbumClick`() {
        val album = createAlbum(name = "Clickable Album")
        robot.setContent(readyAlbumList(albums = listOf(album)))
        robot.clickText("Clickable Album")
        robot.lastAlbumClicked shouldBe album
    }

    @Test
    fun `long clicking album invokes onAlbumLongClick`() {
        val album = createAlbum(name = "Long Click Album")
        robot.setContent(readyAlbumList(albums = listOf(album)))
        robot.longClick("Long Click Album")
        robot.lastAlbumLongClicked shouldBe album
    }

    @Test
    fun `shuffle button invokes onShuffle`() {
        robot.setContent(readyAlbumList(albums = listOf(createAlbum())))
        robot.clickText("Shuffle")
        robot.shuffleClicked shouldBe true
    }

    // endregion

    // region Context menu (rendered via AlbumListItem — see AlbumListRobot doc)

    @Test
    fun `context menu shows standard items for local album`() {
        robot.setItemContent(album = createAlbum(mediaProviders = listOf(MediaProviderType.Shuttle)))
        robot.openContextMenu()

        robot.assertTextDisplayed("Play")
        robot.assertTextDisplayed("Add to Queue")
        robot.assertTextDisplayed("Add to Playlist")
        robot.assertTextDisplayed("Play Next")
        robot.assertTextDisplayed("Exclude")
        robot.assertTextDisplayed("Edit Tags")
    }

    @Test
    fun `context menu hides Edit Tags when any provider does not support it`() {
        robot.setItemContent(
            album = createAlbum(mediaProviders = listOf(MediaProviderType.Shuttle, MediaProviderType.Jellyfin)),
        )
        robot.openContextMenu()
        robot.assertTextNotDisplayed("Edit Tags")
    }

    @Test
    fun `context menu hides Edit Tags for remote-only provider`() {
        robot.setItemContent(album = createAlbum(mediaProviders = listOf(MediaProviderType.Plex)))
        robot.openContextMenu()
        robot.assertTextNotDisplayed("Edit Tags")
    }

    @Test
    fun `context menu invokes onPlay`() {
        val album = createAlbum(name = "Play Me")
        robot.setItemContent(album = album)
        robot.openContextMenu()
        robot.clickMenuItem("Play")
        robot.lastPlayedAlbum shouldBe album
    }

    @Test
    fun `context menu invokes onAddToQueue`() {
        val album = createAlbum(name = "Queue Me")
        robot.setItemContent(album = album)
        robot.openContextMenu()
        robot.clickMenuItem("Add to Queue")
        robot.lastAddedToQueue shouldBe album
    }

    @Test
    fun `context menu invokes onPlayNext`() {
        val album = createAlbum(name = "Next Album")
        robot.setItemContent(album = album)
        robot.openContextMenu()
        robot.clickMenuItem("Play Next")
        robot.lastPlayNext shouldBe album
    }

    @Test
    fun `context menu invokes onExclude`() {
        val album = createAlbum(name = "Exclude Me")
        robot.setItemContent(album = album)
        robot.openContextMenu()
        robot.clickMenuItem("Exclude")
        robot.lastExcluded shouldBe album
    }

    @Test
    fun `context menu invokes onEditTags for editable provider`() {
        val album = createAlbum(name = "Tag Me", mediaProviders = listOf(MediaProviderType.Shuttle))
        robot.setItemContent(album = album)
        robot.openContextMenu()
        robot.clickMenuItem("Edit Tags")
        robot.lastEditTags shouldBe album
    }

    // endregion
}
