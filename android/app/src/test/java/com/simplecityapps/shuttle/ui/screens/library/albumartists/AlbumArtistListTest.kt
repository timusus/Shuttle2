package com.simplecityapps.shuttle.ui.screens.library.albumartists

import androidx.compose.ui.test.junit4.createComposeRule
import com.simplecityapps.createAlbumArtist
import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.ui.screens.library.ViewMode
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AlbumArtistListTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val robot = AlbumArtistListRobot(composeTestRule)

    // region View state rendering

    @Test
    fun `loading state shows loading indicator`() {
        robot.setContent(loadingAlbumArtistList)
        robot.assertTextDisplayed("Loading…")
    }

    @Test
    fun `scanning state shows scan progress message`() {
        robot.setContent(scanningAlbumArtistList(Progress(50, 200)))
        robot.assertTextDisplayed("Scanning your library")
    }

    @Test
    fun `scanning state with null progress shows scan message`() {
        robot.setContent(scanningAlbumArtistList())
        robot.assertTextDisplayed("Scanning your library")
    }

    @Test
    fun `ready state with empty artists shows empty message`() {
        robot.setContent(emptyAlbumArtistList())
        robot.assertTextDisplayed("No artists")
    }

    @Test
    fun `ready state shows artist name`() {
        robot.setContent(readyAlbumArtistList(albumArtists = listOf(createAlbumArtist(name = "Pink Floyd"))))
        robot.assertTextDisplayed("Pink Floyd")
    }

    @Test
    fun `ready state shows album and song counts`() {
        robot.setContent(
            readyAlbumArtistList(
                albumArtists = listOf(createAlbumArtist(name = "Tool", albumCount = 3, songCount = 42))
            )
        )
        robot.assertSubtextDisplayed("3 albums")
        robot.assertSubtextDisplayed("42 songs")
    }

    @Test
    fun `ready state shows multiple artists`() {
        robot.setContent(
            readyAlbumArtistList(
                albumArtists = listOf(
                    createAlbumArtist(name = "Pink Floyd"),
                    createAlbumArtist(name = "Led Zeppelin"),
                    createAlbumArtist(name = "The Beatles"),
                )
            )
        )
        robot.assertTextDisplayed("Pink Floyd")
        robot.assertTextDisplayed("Led Zeppelin")
        robot.assertTextDisplayed("The Beatles")
    }

    @Test
    fun `grid mode renders grid layout`() {
        robot.setContent(
            readyAlbumArtistList(
                albumArtists = listOf(createAlbumArtist(name = "Pink Floyd")),
                viewMode = ViewMode.Grid,
            )
        )
        robot.assertGridLayout()
        robot.assertTextDisplayed("Pink Floyd")
    }

    @Test
    fun `list mode renders list layout`() {
        robot.setContent(
            readyAlbumArtistList(
                albumArtists = listOf(createAlbumArtist(name = "Pink Floyd")),
                viewMode = ViewMode.List,
            )
        )
        robot.assertListLayout()
        robot.assertTextDisplayed("Pink Floyd")
    }

    @Test
    fun `selected artist shows selection mark`() {
        val artist = createAlbumArtist(name = "Selected Artist")
        robot.setItemContent(albumArtist = artist, isSelected = true)
        robot.assertSelectionMarkDisplayed()
    }

    @Test
    fun `unselected artist does not show selection mark`() {
        robot.setItemContent(albumArtist = createAlbumArtist(name = "Unselected Artist"), isSelected = false)
        robot.assertSelectionMarkNotDisplayed()
    }

    // endregion

    // region Callbacks

    @Test
    fun `clicking artist invokes onArtistClick`() {
        val artist = createAlbumArtist(name = "Clickable Artist")
        robot.setContent(readyAlbumArtistList(albumArtists = listOf(artist)))
        robot.clickText("Clickable Artist")
        robot.lastArtistClicked shouldBe artist
    }

    @Test
    fun `long clicking artist invokes onArtistLongClick`() {
        val artist = createAlbumArtist(name = "Long Click Artist")
        robot.setContent(readyAlbumArtistList(albumArtists = listOf(artist)))
        robot.longClick("Long Click Artist")
        robot.lastArtistLongClicked shouldBe artist
    }

    // endregion

    // region Context menu (rendered via AlbumArtistListItem — see AlbumArtistListRobot doc)

    @Test
    fun `context menu shows standard items for local artist`() {
        robot.setItemContent(albumArtist = createAlbumArtist(mediaProviders = listOf(MediaProviderType.Shuttle)))
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
            albumArtist = createAlbumArtist(mediaProviders = listOf(MediaProviderType.Shuttle, MediaProviderType.Jellyfin)),
        )
        robot.openContextMenu()
        robot.assertTextNotDisplayed("Edit Tags")
    }

    @Test
    fun `context menu hides Edit Tags for remote-only provider`() {
        robot.setItemContent(albumArtist = createAlbumArtist(mediaProviders = listOf(MediaProviderType.Plex)))
        robot.openContextMenu()
        robot.assertTextNotDisplayed("Edit Tags")
    }

    @Test
    fun `context menu invokes onPlay`() {
        val artist = createAlbumArtist(name = "Play Me")
        robot.setItemContent(albumArtist = artist)
        robot.openContextMenu()
        robot.clickMenuItem("Play")
        robot.lastPlayedArtist shouldBe artist
    }

    @Test
    fun `context menu invokes onAddToQueue`() {
        val artist = createAlbumArtist(name = "Queue Me")
        robot.setItemContent(albumArtist = artist)
        robot.openContextMenu()
        robot.clickMenuItem("Add to Queue")
        robot.lastAddedToQueue shouldBe artist
    }

    @Test
    fun `context menu invokes onPlayNext`() {
        val artist = createAlbumArtist(name = "Next Artist")
        robot.setItemContent(albumArtist = artist)
        robot.openContextMenu()
        robot.clickMenuItem("Play Next")
        robot.lastPlayNext shouldBe artist
    }

    @Test
    fun `context menu invokes onExclude`() {
        val artist = createAlbumArtist(name = "Exclude Me")
        robot.setItemContent(albumArtist = artist)
        robot.openContextMenu()
        robot.clickMenuItem("Exclude")
        robot.lastExcluded shouldBe artist
    }

    @Test
    fun `context menu invokes onEditTags for editable provider`() {
        val artist = createAlbumArtist(name = "Tag Me", mediaProviders = listOf(MediaProviderType.Shuttle))
        robot.setItemContent(albumArtist = artist)
        robot.openContextMenu()
        robot.clickMenuItem("Edit Tags")
        robot.lastEditTags shouldBe artist
    }

    // endregion
}
