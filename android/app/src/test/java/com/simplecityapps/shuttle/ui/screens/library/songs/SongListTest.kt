package com.simplecityapps.shuttle.ui.screens.library.songs

import androidx.compose.ui.test.junit4.createComposeRule
import com.simplecityapps.createSong
import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.shuttle.model.MediaProviderType
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SongListTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val robot = SongListRobot(composeTestRule)

    // region View state rendering

    @Test
    fun `loading state shows loading indicator`() {
        robot.setContent(loadingSongList)
        robot.assertTextDisplayed("Loading…")
    }

    @Test
    fun `scanning state shows scan progress message`() {
        robot.setContent(scanningSongList(Progress(50, 200)))
        robot.assertTextDisplayed("Scanning your library")
    }

    @Test
    fun `scanning state with null progress shows scan message`() {
        robot.setContent(scanningSongList())
        robot.assertTextDisplayed("Scanning your library")
    }

    @Test
    fun `ready state with empty songs shows empty message`() {
        robot.setContent(emptySongList())
        robot.assertTextDisplayed("No songs")
    }

    @Test
    fun `ready state shows song name`() {
        robot.setContent(readySongList(songs = listOf(createSong(name = "My Great Song"))))
        robot.assertTextDisplayed("My Great Song")
    }

    @Test
    fun `ready state shows artist and album as subtitle`() {
        robot.setContent(
            readySongList(
                songs = listOf(createSong(name = "Track 1", albumArtist = "The Artist", album = "The Album"))
            )
        )
        robot.assertSubtextDisplayed("The Artist")
        robot.assertSubtextDisplayed("The Album")
    }

    @Test
    fun `ready state shows multiple songs`() {
        robot.setContent(
            readySongList(
                songs = listOf(
                    createSong(id = 1, name = "First Song"),
                    createSong(id = 2, name = "Second Song"),
                    createSong(id = 3, name = "Third Song"),
                )
            )
        )
        robot.assertTextDisplayed("First Song")
        robot.assertTextDisplayed("Second Song")
        robot.assertTextDisplayed("Third Song")
    }

    @Test
    fun `ready state shows shuffle button`() {
        robot.setContent(readySongList())
        robot.assertTextDisplayed("Shuffle")
    }

    @Test
    fun `selected song shows selection mark`() {
        val song = createSong(id = 1)
        robot.setContent(readySongList(songs = listOf(song), selectedSongs = setOf(song)))
        robot.assertSelectionMarkDisplayed()
    }

    @Test
    fun `unselected song does not show selection mark`() {
        robot.setContent(readySongList(songs = listOf(createSong(id = 1))))
        robot.assertSelectionMarkNotDisplayed()
    }

    // endregion

    // region Callbacks

    @Test
    fun `shuffle button invokes onShuffle`() {
        robot.setContent(readySongList())
        robot.clickText("Shuffle")
        robot.shuffleClicked shouldBe true
    }

    // endregion

    // region Context menu

    @Test
    fun `context menu shows standard items for local song`() {
        robot.setContent(readySongList(songs = listOf(createSong(mediaProvider = MediaProviderType.Shuttle))))
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
        robot.setContent(readySongList(songs = listOf(createSong(mediaProvider = MediaProviderType.Jellyfin))))
        robot.openContextMenu()
        robot.assertTextNotDisplayed("Edit Tags")
    }

    @Test
    fun `context menu invokes onAddToQueue`() {
        val song = createSong(name = "Queue Me")
        robot.setContent(readySongList(songs = listOf(song)))
        robot.openContextMenu()
        robot.clickMenuItem("Add to Queue")
        robot.lastAddedToQueue shouldBe song
    }

    @Test
    fun `context menu invokes onPlayNext`() {
        val song = createSong(name = "Next Song")
        robot.setContent(readySongList(songs = listOf(song)))
        robot.openContextMenu()
        robot.clickMenuItem("Play Next")
        robot.lastPlayNext shouldBe song
    }

    @Test
    fun `context menu invokes onSongInfo`() {
        val song = createSong(name = "Info Song")
        robot.setContent(readySongList(songs = listOf(song)))
        robot.openContextMenu()
        robot.clickMenuItem("Song Info")
        robot.lastSongInfo shouldBe song
    }

    @Test
    fun `context menu invokes onExclude`() {
        val song = createSong(name = "Exclude Me")
        robot.setContent(readySongList(songs = listOf(song)))
        robot.openContextMenu()
        robot.clickMenuItem("Exclude")
        robot.lastExcluded shouldBe song
    }

    @Test
    fun `context menu invokes onEditTags for Shuttle provider`() {
        val song = createSong(name = "Tag Me", mediaProvider = MediaProviderType.Shuttle)
        robot.setContent(readySongList(songs = listOf(song)))
        robot.openContextMenu()
        robot.clickMenuItem("Edit Tags")
        robot.lastEditTags shouldBe song
    }

    @Test
    fun `context menu invokes onDelete for deletable song`() {
        val song = createSong(name = "Delete Me", mediaProvider = MediaProviderType.Shuttle)
        robot.setContent(readySongList(songs = listOf(song)))
        robot.openContextMenu()
        robot.clickMenuItem("Delete")
        robot.lastDeleted shouldBe song
    }

    // endregion
}
