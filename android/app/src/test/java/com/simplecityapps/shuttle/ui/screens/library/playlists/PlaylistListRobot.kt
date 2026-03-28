package com.simplecityapps.shuttle.ui.screens.library.playlists

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.SmartPlaylist
import com.simplecityapps.shuttle.ui.theme.AppTheme

/**
 * Test robot for [PlaylistList] and [PlaylistListItem] Compose characterisation tests.
 *
 * View state tests render via [PlaylistList]. Context menu tests render via
 * [PlaylistListItem] directly, because the FastScroller overlay in PlaylistList
 * causes DropdownMenu popups to be immediately dismissed under Robolectric.
 * This detail is encapsulated here — tests don't need to know about it.
 */
class PlaylistListRobot(private val rule: ComposeContentTestRule) {

    // -- Callback captures --

    var lastClickedPlaylist: Playlist? = null
        private set
    var lastClickedSmartPlaylist: SmartPlaylist? = null
        private set
    var lastPlayedPlaylist: Playlist? = null
        private set
    var lastAddedToQueue: Playlist? = null
        private set
    var lastPlayNext: Playlist? = null
        private set
    var lastDeletedPlaylist: Playlist? = null
        private set
    var lastClearedPlaylist: Playlist? = null
        private set
    var lastRenamedPlaylist: Playlist? = null
        private set

    private fun callbacks() = Callbacks(
        onPlaylistClick = { lastClickedPlaylist = it },
        onSmartPlaylistClick = { lastClickedSmartPlaylist = it },
        onPlay = { lastPlayedPlaylist = it },
        onAddToQueue = { lastAddedToQueue = it },
        onPlayNext = { lastPlayNext = it },
        onDelete = { lastDeletedPlaylist = it },
        onClear = { lastClearedPlaylist = it },
        onRename = { lastRenamedPlaylist = it },
    )

    private fun resetCallbacks() {
        lastClickedPlaylist = null
        lastClickedSmartPlaylist = null
        lastPlayedPlaylist = null
        lastAddedToQueue = null
        lastPlayNext = null
        lastDeletedPlaylist = null
        lastClearedPlaylist = null
        lastRenamedPlaylist = null
    }

    // -- Content setup --

    /** Render the full [PlaylistList] composable (for view state tests). */
    fun setContent(uiState: PlaylistListUiState) {
        resetCallbacks()
        rule.setContent {
            renderPlaylistListContent(uiState)
        }
    }

    /** Render with a real [PlaylistListViewModel] (for integration tests). */
    fun setContentWithViewModel(viewModel: PlaylistListViewModel) {
        resetCallbacks()
        rule.setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            renderPlaylistListContent(uiState)
        }
    }

    /** Render a single [PlaylistListItem] (for context menu tests). */
    fun setItemContent(playlist: Playlist) {
        resetCallbacks()
        val cb = callbacks()
        rule.setContent {
            AppTheme {
                PlaylistListItem(
                    playlist = playlist,
                    onPlaylistClick = cb.onPlaylistClick,
                    onPlay = cb.onPlay,
                    onAddToQueue = cb.onAddToQueue,
                    onPlayNext = cb.onPlayNext,
                    onDelete = cb.onDelete,
                    onClear = cb.onClear,
                    onRename = cb.onRename,
                )
            }
        }
    }

    @Composable
    private fun renderPlaylistListContent(uiState: PlaylistListUiState) {
        val cb = callbacks()
        AppTheme {
            PlaylistList(
                uiState = uiState,
                onPlaylistClick = cb.onPlaylistClick,
                onSmartPlaylistClick = cb.onSmartPlaylistClick,
                onPlay = cb.onPlay,
                onAddToQueue = cb.onAddToQueue,
                onPlayNext = cb.onPlayNext,
                onDelete = cb.onDelete,
                onClear = cb.onClear,
                onRename = cb.onRename,
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

    // -- Interactions --

    fun clickText(text: String) {
        rule.onNodeWithText(text).performClick()
    }

    fun openContextMenu() {
        rule.onNodeWithContentDescription("Playlist menu").performClick()
    }

    fun clickMenuItem(text: String) {
        rule.onNodeWithText(text).performClick()
    }

    private data class Callbacks(
        val onPlaylistClick: (Playlist) -> Unit,
        val onSmartPlaylistClick: (SmartPlaylist) -> Unit,
        val onPlay: (Playlist) -> Unit,
        val onAddToQueue: (Playlist) -> Unit,
        val onPlayNext: (Playlist) -> Unit,
        val onDelete: (Playlist) -> Unit,
        val onClear: (Playlist) -> Unit,
        val onRename: (Playlist) -> Unit,
    )
}
