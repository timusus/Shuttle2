package com.simplecityapps.shuttle.ui.screens.library.albumartists

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData
import com.simplecityapps.shuttle.ui.theme.AppTheme
import kotlinx.collections.immutable.toImmutableList

/**
 * Test robot for [AlbumArtistList] and [AlbumArtistListItem] Compose characterisation tests.
 *
 * View state tests render via [AlbumArtistList]. Context menu tests render via
 * [AlbumArtistListItem] directly, because the [FastScroller] overlay in AlbumArtistList
 * causes DropdownMenu popups to be immediately dismissed under Robolectric.
 */
class AlbumArtistListRobot(private val rule: ComposeContentTestRule) {

    // -- Callback captures --

    var lastArtistClicked: AlbumArtist? = null
        private set
    var lastArtistLongClicked: AlbumArtist? = null
        private set
    var lastPlayedArtist: AlbumArtist? = null
        private set
    var lastAddedToQueue: AlbumArtist? = null
        private set
    var lastPlayNext: AlbumArtist? = null
        private set
    var lastExcluded: AlbumArtist? = null
        private set
    var lastEditTags: AlbumArtist? = null
        private set
    var lastAddToPlaylist: Pair<Playlist, PlaylistData>? = null
        private set
    var lastCreatePlaylistDialog: AlbumArtist? = null
        private set

    private fun callbacks() = Callbacks(
        onArtistClick = { lastArtistClicked = it },
        onArtistLongClick = { lastArtistLongClicked = it },
        onPlay = { lastPlayedArtist = it },
        onAddToQueue = { lastAddedToQueue = it },
        onPlayNext = { lastPlayNext = it },
        onExclude = { lastExcluded = it },
        onEditTags = { lastEditTags = it },
        onAddToPlaylist = { playlist, data -> lastAddToPlaylist = playlist to data },
        onShowCreatePlaylistDialog = { lastCreatePlaylistDialog = it },
    )

    private fun resetCallbacks() {
        lastArtistClicked = null
        lastArtistLongClicked = null
        lastPlayedArtist = null
        lastAddedToQueue = null
        lastPlayNext = null
        lastExcluded = null
        lastEditTags = null
        lastAddToPlaylist = null
        lastCreatePlaylistDialog = null
    }

    // -- Content setup --

    /** Render the full [AlbumArtistList] composable (for view state tests). */
    fun setContent(
        uiState: AlbumArtistListUiState,
        playlists: List<Playlist> = emptyList(),
    ) {
        resetCallbacks()
        rule.setContent {
            renderAlbumArtistListContent(uiState, playlists)
        }
    }

    /** Render with a real [AlbumArtistListViewModel] (for integration tests). */
    fun setContentWithViewModel(
        viewModel: AlbumArtistListViewModel,
        playlists: List<Playlist> = emptyList(),
    ) {
        resetCallbacks()
        val cb = callbacks()
        rule.setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            AppTheme {
                AlbumArtistList(
                    uiState = uiState,
                    playlists = playlists.toImmutableList(),
                    onArtistClick = { viewModel.onArtistClick(it) },
                    onArtistLongClick = { viewModel.onArtistLongClick(it) },
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

    /** Render a single [AlbumArtistListItem] (for context menu tests). */
    fun setItemContent(
        albumArtist: AlbumArtist,
        isSelected: Boolean = false,
        playlists: List<Playlist> = emptyList(),
    ) {
        resetCallbacks()
        val cb = callbacks()
        rule.setContent {
            AppTheme {
                AlbumArtistListItem(
                    albumArtist = albumArtist,
                    isSelected = isSelected,
                    playlists = playlists.toImmutableList(),
                    onClick = cb.onArtistClick,
                    onLongClick = cb.onArtistLongClick,
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
    private fun renderAlbumArtistListContent(
        uiState: AlbumArtistListUiState,
        playlists: List<Playlist>,
    ) {
        val cb = callbacks()
        AppTheme {
            AlbumArtistList(
                uiState = uiState,
                playlists = playlists.toImmutableList(),
                onArtistClick = cb.onArtistClick,
                onArtistLongClick = cb.onArtistLongClick,
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
        rule.onNodeWithTag("album-artists-grid").assertIsDisplayed()
    }

    fun assertListLayout() {
        rule.onNodeWithTag("album-artists-list").assertIsDisplayed()
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
        rule.onNodeWithContentDescription("Album artist menu").performClick()
    }

    fun clickMenuItem(text: String) {
        rule.onNodeWithText(text).performClick()
    }

    private data class Callbacks(
        val onArtistClick: (AlbumArtist) -> Unit,
        val onArtistLongClick: (AlbumArtist) -> Unit,
        val onPlay: (AlbumArtist) -> Unit,
        val onAddToQueue: (AlbumArtist) -> Unit,
        val onPlayNext: (AlbumArtist) -> Unit,
        val onExclude: (AlbumArtist) -> Unit,
        val onEditTags: (AlbumArtist) -> Unit,
        val onAddToPlaylist: (Playlist, PlaylistData) -> Unit,
        val onShowCreatePlaylistDialog: (AlbumArtist) -> Unit,
    )
}
