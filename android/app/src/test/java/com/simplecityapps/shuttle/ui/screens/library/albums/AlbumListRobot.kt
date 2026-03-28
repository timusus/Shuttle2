package com.simplecityapps.shuttle.ui.screens.library.albums

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData
import com.simplecityapps.shuttle.ui.theme.AppTheme
import kotlinx.collections.immutable.toImmutableList

/**
 * Test robot for [AlbumList] and [AlbumListItem] Compose characterisation tests.
 *
 * View state tests render via [AlbumList]. Context menu tests render via
 * [AlbumListItem] directly, because the [FastScroller] overlay in AlbumList
 * causes DropdownMenu popups to be immediately dismissed under Robolectric.
 */
class AlbumListRobot(private val rule: ComposeContentTestRule) {

    // -- Callback captures --

    var lastAlbumClicked: Album? = null
        private set
    var lastAlbumLongClicked: Album? = null
        private set
    var lastPlayedAlbum: Album? = null
        private set
    var lastAddedToQueue: Album? = null
        private set
    var lastPlayNext: Album? = null
        private set
    var lastExcluded: Album? = null
        private set
    var lastEditTags: Album? = null
        private set
    var lastAddToPlaylist: Pair<Playlist, PlaylistData>? = null
        private set
    var lastCreatePlaylistDialog: Album? = null
        private set
    var shuffleClicked: Boolean = false
        private set

    private fun callbacks() = Callbacks(
        onAlbumClick = { lastAlbumClicked = it },
        onAlbumLongClick = { lastAlbumLongClicked = it },
        onPlay = { lastPlayedAlbum = it },
        onAddToQueue = { lastAddedToQueue = it },
        onPlayNext = { lastPlayNext = it },
        onExclude = { lastExcluded = it },
        onEditTags = { lastEditTags = it },
        onAddToPlaylist = { playlist, data -> lastAddToPlaylist = playlist to data },
        onShowCreatePlaylistDialog = { lastCreatePlaylistDialog = it },
        onShuffle = { shuffleClicked = true },
    )

    private fun resetCallbacks() {
        lastAlbumClicked = null
        lastAlbumLongClicked = null
        lastPlayedAlbum = null
        lastAddedToQueue = null
        lastPlayNext = null
        lastExcluded = null
        lastEditTags = null
        lastAddToPlaylist = null
        lastCreatePlaylistDialog = null
        shuffleClicked = false
    }

    // -- Content setup --

    /** Render the full [AlbumList] composable (for view state tests). */
    fun setContent(
        uiState: AlbumListUiState,
        playlists: List<Playlist> = emptyList(),
    ) {
        resetCallbacks()
        rule.setContent {
            renderAlbumListContent(uiState, playlists)
        }
    }

    /** Render with a real [AlbumListViewModel] (for integration tests). */
    fun setContentWithViewModel(
        viewModel: AlbumListViewModel,
        playlists: List<Playlist> = emptyList(),
    ) {
        resetCallbacks()
        val cb = callbacks()
        rule.setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            AppTheme {
                AlbumList(
                    uiState = uiState,
                    playlists = playlists.toImmutableList(),
                    onAlbumClick = { viewModel.onAlbumClick(it) },
                    onAlbumLongClick = { viewModel.onAlbumLongClick(it) },
                    onPlay = cb.onPlay,
                    onAddToQueue = cb.onAddToQueue,
                    onPlayNext = cb.onPlayNext,
                    onExclude = cb.onExclude,
                    onEditTags = cb.onEditTags,
                    onAddToPlaylist = cb.onAddToPlaylist,
                    onShowCreatePlaylistDialog = cb.onShowCreatePlaylistDialog,
                    onShuffle = cb.onShuffle,
                )
            }
        }
    }

    /** Render a single [AlbumListItem] (for context menu tests). */
    fun setItemContent(
        album: Album,
        isSelected: Boolean = false,
        playlists: List<Playlist> = emptyList(),
    ) {
        resetCallbacks()
        val cb = callbacks()
        rule.setContent {
            AppTheme {
                AlbumListItem(
                    album = album,
                    isSelected = isSelected,
                    playlists = playlists.toImmutableList(),
                    onClick = cb.onAlbumClick,
                    onLongClick = cb.onAlbumLongClick,
                    onPlay = cb.onPlay,
                    onAddToQueue = cb.onAddToQueue,
                    onPlayNext = cb.onPlayNext,
                    onExclude = cb.onExclude,
                    onEditTags = cb.onEditTags,
                    onAddToPlaylist = cb.onAddToPlaylist,
                    onShowCreatePlaylistDialog = cb.onShowCreatePlaylistDialog,
                )
            }
        }
    }

    @Composable
    private fun renderAlbumListContent(
        uiState: AlbumListUiState,
        playlists: List<Playlist>,
    ) {
        val cb = callbacks()
        AppTheme {
            AlbumList(
                uiState = uiState,
                playlists = playlists.toImmutableList(),
                onAlbumClick = cb.onAlbumClick,
                onAlbumLongClick = cb.onAlbumLongClick,
                onPlay = cb.onPlay,
                onAddToQueue = cb.onAddToQueue,
                onPlayNext = cb.onPlayNext,
                onExclude = cb.onExclude,
                onEditTags = cb.onEditTags,
                onAddToPlaylist = cb.onAddToPlaylist,
                onShowCreatePlaylistDialog = cb.onShowCreatePlaylistDialog,
                onShuffle = cb.onShuffle,
            )
        }
    }

    // -- Assertions --

    fun assertTextDisplayed(text: String) {
        rule.onNodeWithText(text).assertIsDisplayed()
    }

    fun assertTextNotDisplayed(text: String) {
        rule.onNodeWithText(text).assertDoesNotExist()
    }

    fun assertSubtextDisplayed(text: String) {
        rule.onNode(hasText(text, substring = true)).assertIsDisplayed()
    }

    fun assertGridLayout() {
        rule.onNodeWithTag("albums-grid").assertIsDisplayed()
    }

    fun assertListLayout() {
        rule.onNodeWithTag("albums-list").assertIsDisplayed()
    }

    fun assertSelectionMarkDisplayed() {
        rule.onNodeWithContentDescription("Selection mark").assertIsDisplayed()
    }

    fun assertSelectionMarkNotDisplayed() {
        rule.onNodeWithContentDescription("Selection mark").assertDoesNotExist()
    }

    // -- Interactions --

    fun clickText(text: String) {
        rule.onNodeWithText(text).performClick()
    }

    fun longClick(text: String) {
        rule.onNodeWithText(text).performTouchInput { longClick() }
    }

    fun openContextMenu() {
        rule.onNodeWithContentDescription("Album menu").performClick()
    }

    fun clickMenuItem(text: String) {
        rule.onNodeWithText(text).performClick()
    }

    private data class Callbacks(
        val onAlbumClick: (Album) -> Unit,
        val onAlbumLongClick: (Album) -> Unit,
        val onPlay: (Album) -> Unit,
        val onAddToQueue: (Album) -> Unit,
        val onPlayNext: (Album) -> Unit,
        val onExclude: (Album) -> Unit,
        val onEditTags: (Album) -> Unit,
        val onAddToPlaylist: (Playlist, PlaylistData) -> Unit,
        val onShowCreatePlaylistDialog: (Album) -> Unit,
        val onShuffle: () -> Unit,
    )
}
