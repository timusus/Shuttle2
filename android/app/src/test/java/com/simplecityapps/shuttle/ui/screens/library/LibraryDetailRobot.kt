package com.simplecityapps.shuttle.ui.screens.library

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import com.simplecityapps.shuttle.designsystem.theme.S2Theme
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.PlaylistSong
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.sorting.PlaylistSongSortOrder
import com.simplecityapps.shuttle.ui.actions.MediaActionType
import com.simplecityapps.shuttle.ui.screens.library.albumartists.detail.AlbumArtistDetailUiState
import com.simplecityapps.shuttle.ui.screens.library.albums.detail.AlbumDetailUiState

/**
 * Test robot for the library detail screens (album, album artist, genre, playlist, smart playlist). They share
 * [LibraryDetailScaffold], so one robot owns the selectors for all five; each `set*` renders one screen.
 */
class LibraryDetailRobot(private val rule: ComposeContentTestRule) {

    // -- Callback captures --

    var lastPlayed: Pair<List<Song>, Int>? = null
        private set
    var shuffleClicked = false
        private set
    var navigatedUp = false
        private set

    /** The item whose actions sheet was asked for: the screen's own item from the overflow, or a row's. */
    var lastMore: Any? = null
        private set
    var lastAlbumClicked: Album? = null
        private set
    var lastToggled: PlaylistSong? = null
        private set
    var selectionCleared = false
        private set
    var removedSelected = false
        private set
    var lastSelectionAction: MediaActionType? = null
        private set
    var lastSortOrder: PlaylistSongSortOrder? = null
        private set
    var lastDescending: Boolean? = null
        private set
    var exported = false
        private set

    // -- Content setup --

    fun setAlbum(uiState: AlbumDetailUiState) = render {
        AlbumDetailScreen(uiState, onNavigateUp = ::up, onPlay = ::play, onShuffle = ::shuffle, onAlbumMore = ::more, onSongMore = ::more)
    }

    fun setAlbumArtist(uiState: AlbumArtistDetailUiState) = render {
        AlbumArtistDetailScreen(
            uiState,
            onNavigateUp = ::up,
            onPlay = ::play,
            onShuffle = ::shuffle,
            onArtistMore = ::more,
            onAlbumClick = { lastAlbumClicked = it },
            onAlbumMore = ::more,
            onSongMore = ::more,
        )
    }

    fun setGenre(uiState: GenreDetailUiState) = render {
        GenreDetailScreen(
            uiState,
            onNavigateUp = ::up,
            onPlay = ::play,
            onShuffle = ::shuffle,
            onGenreMore = ::more,
            onAlbumClick = { lastAlbumClicked = it },
            onAlbumMore = ::more,
            onSongMore = ::more,
        )
    }

    fun setPlaylist(uiState: PlaylistDetailUiState) = render {
        PlaylistDetailScreen(
            uiState,
            onNavigateUp = ::up,
            onPlay = ::play,
            onShuffle = ::shuffle,
            onPlaylistMore = ::more,
            onSongMore = ::more,
            onToggleSelected = { lastToggled = it },
            onClearSelection = { selectionCleared = true },
            onRemoveSelected = { removedSelected = true },
            onSelectionAction = { lastSelectionAction = it },
            onSortOrderSelected = { lastSortOrder = it },
            onSortDescendingChanged = { lastDescending = it },
            onExport = { exported = true },
            onMove = { _, _ -> },
            onMoveFinished = {},
        )
    }

    fun setSmartPlaylist(uiState: SmartPlaylistDetailUiState) = render {
        SmartPlaylistDetailScreen(uiState, onNavigateUp = ::up, onPlay = ::play, onShuffle = ::shuffle, onPlaylistMore = { lastMore = uiState.smartPlaylist }, onSongMore = ::more)
    }

    private fun render(content: @Composable () -> Unit) {
        rule.setContent { S2Theme { content() } }
        rule.waitForIdle()
    }

    private fun up() {
        navigatedUp = true
    }

    private fun play(songs: List<Song>, index: Int) {
        lastPlayed = songs to index
    }

    private fun shuffle() {
        shuffleClicked = true
    }

    private fun more(item: Any) {
        lastMore = item
    }

    // -- Assertions --

    fun assertTextDisplayed(text: String, substring: Boolean = false) {
        rule.onAllNodesWithText(text, substring = substring)[0].assertIsDisplayed()
    }

    fun assertTextNotDisplayed(text: String, substring: Boolean = false) {
        check(rule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isEmpty()) { "\"$text\" is shown" }
    }

    /** [text] appears [times] times in the composed tree, e.g. a song listed under its album and in Songs. */
    fun assertTextShownTimes(text: String, times: Int) {
        val shown = rule.onAllNodesWithText(text).fetchSemanticsNodes().size
        check(shown == times) { "\"$text\" shown $shown times, expected $times" }
    }

    /** Whether a row shows the now-playing indicator. */
    fun assertNowPlayingShown() {
        rule.onAllNodesWithContentDescription("Now playing")[0].assertIsDisplayed()
    }

    fun assertReorderHandlesShown() {
        rule.onAllNodesWithContentDescription("Reorder")[0].assertIsDisplayed()
    }

    fun assertNoReorderHandles() {
        check(rule.onAllNodesWithContentDescription("Reorder").fetchSemanticsNodes().isEmpty()) { "Reorder handles are shown" }
    }

    // -- Interactions --

    fun clickPlay() = clickText("Play")

    fun clickShuffle() = clickText("Shuffle")

    fun clickNavigateUp() {
        rule.onNodeWithContentDescription("Navigate up").performClick()
        rule.waitForIdle()
    }

    /** The top bar's overflow, which opens the screen item's actions. */
    fun clickOverflow() {
        rule.onNodeWithTag("detail-more").performClick()
        rule.waitForIdle()
    }

    fun clickText(text: String) {
        scrollTo(text)
        rule.onAllNodesWithText(text)[0].performClick()
        rule.waitForIdle()
    }

    fun longClickText(text: String) {
        scrollTo(text)
        rule.onAllNodesWithText(text)[0].performTouchInput { longClick() }
        rule.waitForIdle()
    }

    /** Taps the [index]th row's "More options"; the top bar's overflow is not a row. */
    fun clickRowMore(index: Int) {
        rule.onAllNodes(hasContentDescription("More options") and !hasTestTag("detail-more"))[index].performClick()
        rule.waitForIdle()
    }

    fun clickContentDescription(label: String) {
        rule.onNodeWithContentDescription(label).performClick()
        rule.waitForIdle()
    }

    fun openSortMenu() = clickContentDescription("Sort by")

    /** Taps an item in an open menu, which renders in a popup outside the screen's list. */
    fun clickMenuItem(text: String, substring: Boolean = false) {
        rule.onNodeWithText(text, substring = substring).performClick()
        rule.waitForIdle()
    }

    fun scrollTo(text: String) {
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text))
    }
}
