package com.simplecityapps.shuttle.ui.screens.library

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
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
import com.simplecityapps.shuttle.designsystem.component.SongOfflineState
import com.simplecityapps.shuttle.designsystem.theme.S2Theme
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.PlaylistSong
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.sorting.ArtistSongSortOrder
import com.simplecityapps.shuttle.sorting.PlaylistSongSortOrder
import com.simplecityapps.shuttle.ui.actions.MediaActionType
import com.simplecityapps.shuttle.ui.common.downloads.LocalDownloadOfflineStates
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
    var lastAlbumToggled: Album? = null
        private set

    /** The Appears On album tapped open on an artist's page. */
    var lastAppearsOnOpened: Album? = null
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

    /** The sort picked from an artist's songs header, and whether its Expand all / Collapse all was tapped. */
    var lastArtistSortOrder: ArtistSongSortOrder? = null
        private set
    var expandedAll = false
        private set
    var collapsedAll = false
        private set
    var exported = false
        private set

    private var backDispatcher: OnBackPressedDispatcher? = null

    // -- Content setup --

    fun setAlbum(uiState: AlbumDetailUiState) = render {
        AlbumDetailScreen(uiState, onNavigateUp = ::up, onPlay = ::play, onShuffle = ::shuffle, onAlbumMore = ::more, onSongMore = ::more, onOpenAlbum = { lastAlbumClicked = it }, onMoreByAlbumMore = ::more)
    }

    fun setAlbumArtist(uiState: AlbumArtistDetailUiState) = render {
        AlbumArtistDetailScreen(
            uiState,
            onNavigateUp = ::up,
            onPlay = ::play,
            onShuffle = ::shuffle,
            onArtistMore = ::more,
            onToggleAlbum = { lastAlbumToggled = it },
            onOpenAlbum = { lastAlbumClicked = it },
            onAlbumMore = ::more,
            onSongMore = ::more,
            onAppearsOnClick = { lastAppearsOnOpened = it },
            onSortOrderSelected = { lastArtistSortOrder = it },
            onExpandAll = { expandedAll = true },
            onCollapseAll = { collapsedAll = true },
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

    fun setPlaylist(uiState: PlaylistDetailUiState, offline: Map<String, SongOfflineState> = emptyMap()) = render {
        CompositionLocalProvider(LocalDownloadOfflineStates provides offline) {
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
    }

    fun setSmartPlaylist(uiState: SmartPlaylistDetailUiState) = render {
        SmartPlaylistDetailScreen(uiState, onNavigateUp = ::up, onPlay = ::play, onShuffle = ::shuffle, onPlaylistMore = { lastMore = uiState.smartPlaylist }, onSongMore = ::more)
    }

    private fun render(content: @Composable () -> Unit) {
        rule.setContent {
            backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
            S2Theme { content() }
        }
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

    /** [upper] is laid out above [lower] in the list; both must be composed. */
    fun assertTextAbove(upper: String, lower: String) {
        val top = rule.onAllNodesWithText(upper)[0].fetchSemanticsNode().boundsInRoot.top
        val bottom = rule.onAllNodesWithText(lower)[0].fetchSemanticsNode().boundsInRoot.top
        check(top < bottom) { "\"$upper\" ($top) is not above \"$lower\" ($bottom)" }
    }

    /** Whether a node labelled [label] (a content description) is composed. */
    fun assertContentDescriptionShown(label: String, shown: Boolean = true) {
        val count = rule.onAllNodesWithContentDescription(label).fetchSemanticsNodes().size
        check((count > 0) == shown) { "\"$label\" shown $count times" }
    }

    /** Whether an artist's pinned album header shows over the list, and that it names [albumTitle]. */
    fun assertPinnedAlbum(albumTitle: String?) {
        val pinned = rule.onAllNodes(hasTestTag("artist-pinned-album")).fetchSemanticsNodes()
        if (albumTitle == null) {
            check(pinned.isEmpty()) { "an album header is pinned" }
        } else {
            check(pinned.size == 1) { "no album header is pinned" }
            rule.onNode(hasText(albumTitle) and hasAnyAncestor(hasTestTag("artist-pinned-album"))).assertExists()
        }
    }

    fun assertReorderHandlesShown() {
        rule.onAllNodesWithContentDescription("Reorder")[0].assertIsDisplayed()
    }

    fun assertNoReorderHandles() {
        check(rule.onAllNodesWithContentDescription("Reorder").fetchSemanticsNodes().isEmpty()) { "Reorder handles are shown" }
    }

    // -- Interactions --

    /** Whether the screen takes a back press, rather than leaving it to the back stack. */
    fun backIsHandled(): Boolean = checkNotNull(backDispatcher).hasEnabledCallbacks()

    fun pressBack() {
        rule.runOnUiThread { checkNotNull(backDispatcher).onBackPressed() }
        rule.waitForIdle()
    }

    fun clickPlay() = clickText("Play")

    fun clickShuffle() = clickText("Shuffle")

    fun clickNavigateUp() {
        rule.onNodeWithContentDescription("Back").performClick()
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

    /** Taps [albumTitle]'s thumbnail, which opens the album rather than folding its songs. */
    fun clickOpenAlbum(albumTitle: String) {
        rule.onNodeWithContentDescription("Open $albumTitle").performClick()
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

    /** Opens an artist's sort menu, from its songs header, labelled with the current sort. */
    fun openArtistSortMenu() {
        scrollToTag("artist-sort")
        rule.onNodeWithTag("artist-sort").performClick()
        rule.waitForIdle()
    }

    /** Taps an item in an open menu, which renders in a popup outside the screen's list. */
    fun clickMenuItem(text: String, substring: Boolean = false) {
        rule.onNodeWithText(text, substring = substring).performClick()
        rule.waitForIdle()
    }

    fun scrollToTag(tag: String) {
        rule.onNode(hasScrollToNodeAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).performScrollToNode(hasTestTag(tag))
        rule.waitForIdle()
    }

    fun scrollTo(text: String) {
        // The screen's own list, not a row scrolling sideways within it (an artist's Appears On)
        rule.onNode(hasScrollToNodeAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).performScrollToNode(hasText(text))
    }
}
