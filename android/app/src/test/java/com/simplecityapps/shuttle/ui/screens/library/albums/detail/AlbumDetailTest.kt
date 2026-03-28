package com.simplecityapps.shuttle.ui.screens.library.albums.detail

import androidx.compose.ui.test.junit4.createComposeRule
import com.simplecityapps.createAlbum
import com.simplecityapps.createSong
import com.simplecityapps.shuttle.model.MediaProviderType
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AlbumDetailTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val robot = AlbumDetailRobot(composeTestRule)

    // region View state rendering

    @Test
    fun `loading state shows loading indicator`() {
        robot.setContent(loadingAlbumDetail)
        robot.assertTextDisplayed("Loading…")
    }

    @Test
    fun `empty state shows empty message`() {
        robot.setContent(emptyAlbumDetail())
        robot.assertTextDisplayed("No songs")
    }

    @Test
    fun `ready state shows album name`() {
        robot.setContent(
            readyAlbumDetail(
                album = createAlbum(name = "Abbey Road"),
            )
        )
        robot.assertTextDisplayed("Abbey Road")
    }

    @Test
    fun `ready state shows album year`() {
        robot.setContent(
            readyAlbumDetail(
                album = createAlbum(name = "Abbey Road", year = 1969),
            )
        )
        robot.assertSubtextDisplayed("1969")
    }

    @Test
    fun `ready state shows song name`() {
        robot.setContent(
            readyAlbumDetail(
                songs = listOf(createSong(name = "Come Together")),
            )
        )
        robot.assertTextDisplayed("Come Together")
    }

    @Test
    fun `ready state shows multiple songs`() {
        robot.setContent(
            readyAlbumDetail(
                songs = listOf(
                    createSong(id = 1, name = "Come Together", track = 1),
                    createSong(id = 2, name = "Something", track = 2),
                    createSong(id = 3, name = "Here Comes the Sun", track = 3),
                ),
            )
        )
        robot.assertTextDisplayed("Come Together")
        robot.assertTextDisplayed("Something")
        robot.assertTextDisplayed("Here Comes the Sun")
    }

    @Test
    fun `ready state shows track number`() {
        robot.setContent(
            readyAlbumDetail(
                songs = listOf(createSong(name = "Track Five", track = 5)),
            )
        )
        robot.assertTextDisplayed("5")
    }

    @Test
    fun `disc number header shown when multiple discs`() {
        robot.setContent(
            readyAlbumDetail(
                songs = listOf(
                    createSong(id = 1, name = "Disc 1 Song", track = 1, disc = 1),
                    createSong(id = 2, name = "Disc 2 Song", track = 1, disc = 2),
                ),
            )
        )
        robot.assertTextDisplayed("Disc 1")
        robot.assertTextDisplayed("Disc 2")
    }

    @Test
    fun `grouping header shown when songs have groupings`() {
        robot.setContent(
            readyAlbumDetail(
                songs = listOf(
                    createSong(id = 1, name = "Movement I", track = 1, grouping = "Symphony No. 5"),
                    createSong(id = 2, name = "Movement II", track = 2, grouping = "Symphony No. 5"),
                ),
            )
        )
        robot.assertTextDisplayed("Symphony No. 5")
    }

    @Test
    fun `disc number header not shown when single disc`() {
        robot.setContent(
            readyAlbumDetail(
                songs = listOf(
                    createSong(id = 1, name = "Song One", track = 1),
                    createSong(id = 2, name = "Song Two", track = 2),
                ),
            )
        )
        robot.assertTextNotDisplayed("Disc 1")
    }

    @Test
    fun `current song is highlighted`() {
        val song = createSong(id = 1, name = "Now Playing Song")
        robot.setContent(
            readyAlbumDetail(
                songs = listOf(song),
                currentSong = song,
            )
        )
        robot.assertCurrentSongHighlighted("Now Playing Song")
    }

    @Test
    fun `non-current song is not highlighted`() {
        robot.setContent(
            readyAlbumDetail(
                songs = listOf(createSong(id = 1, name = "Some Song")),
                currentSong = null,
            )
        )
        robot.assertCurrentSongNotHighlighted()
    }

    // endregion

    // region Callbacks

    @Test
    fun `song click invokes onSongClick`() {
        val song = createSong(name = "Click Me")
        robot.setContent(readyAlbumDetail(songs = listOf(song)))
        robot.clickText("Click Me")
        robot.lastSongClicked shouldBe song
    }

    // endregion

    // region Context menu

    @Test
    fun `context menu shows standard items for local song`() {
        robot.setContent(
            readyAlbumDetail(
                songs = listOf(createSong(mediaProvider = MediaProviderType.Shuttle)),
            )
        )
        robot.openContextMenu()

        robot.assertTextDisplayed("Add to Queue")
        robot.assertTextDisplayed("Add to Playlist")
        robot.assertTextDisplayed("Play Next")
        robot.assertTextDisplayed("Song Info")
        robot.assertTextDisplayed("Exclude")
        robot.assertTextDisplayed("Edit Tags")
        robot.assertTextDisplayed("Delete")
    }

    @Test
    fun `context menu hides Edit Tags for non-tag-editing provider`() {
        robot.setContent(
            readyAlbumDetail(
                songs = listOf(createSong(mediaProvider = MediaProviderType.Jellyfin)),
            )
        )
        robot.openContextMenu()
        robot.assertTextNotDisplayed("Edit Tags")
    }

    @Test
    fun `context menu invokes onAddToQueue`() {
        val song = createSong(name = "Queue Me")
        robot.setContent(readyAlbumDetail(songs = listOf(song)))
        robot.openContextMenu()
        robot.clickMenuItem("Add to Queue")
        robot.lastAddedToQueue shouldBe song
    }

    @Test
    fun `context menu invokes onPlayNext`() {
        val song = createSong(name = "Next Song")
        robot.setContent(readyAlbumDetail(songs = listOf(song)))
        robot.openContextMenu()
        robot.clickMenuItem("Play Next")
        robot.lastPlayNext shouldBe song
    }

    @Test
    fun `context menu invokes onSongInfo`() {
        val song = createSong(name = "Info Song")
        robot.setContent(readyAlbumDetail(songs = listOf(song)))
        robot.openContextMenu()
        robot.clickMenuItem("Song Info")
        robot.lastSongInfo shouldBe song
    }

    @Test
    fun `context menu invokes onExclude`() {
        val song = createSong(name = "Exclude Me")
        robot.setContent(readyAlbumDetail(songs = listOf(song)))
        robot.openContextMenu()
        robot.clickMenuItem("Exclude")
        robot.lastExcluded shouldBe song
    }

    @Test
    fun `context menu invokes onEditTags for Shuttle provider`() {
        val song = createSong(name = "Tag Me", mediaProvider = MediaProviderType.Shuttle)
        robot.setContent(readyAlbumDetail(songs = listOf(song)))
        robot.openContextMenu()
        robot.clickMenuItem("Edit Tags")
        robot.lastEditTags shouldBe song
    }

    @Test
    fun `context menu invokes onDelete for deletable song`() {
        val song = createSong(name = "Delete Me", mediaProvider = MediaProviderType.Shuttle)
        robot.setContent(readyAlbumDetail(songs = listOf(song)))
        robot.openContextMenu()
        robot.clickMenuItem("Delete")
        robot.lastDeleted shouldBe song
    }

    // endregion
}
