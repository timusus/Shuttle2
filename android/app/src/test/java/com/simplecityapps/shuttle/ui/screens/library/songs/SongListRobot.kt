package com.simplecityapps.shuttle.ui.screens.library.songs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData
import com.simplecityapps.shuttle.ui.theme.AppTheme
import kotlinx.collections.immutable.toImmutableList

/**
 * Test robot for [SongList] Compose characterisation tests.
 *
 * Encapsulates selectors and interaction mechanics so tests express
 * *what* they verify, not *how* to find nodes. When the Compose
 * implementation changes (test tags, content descriptions, layout
 * structure), update this robot — not every test.
 */
class SongListRobot(private val rule: ComposeContentTestRule) {

    // -- Callback captures (populated by setContent) --

    var lastSongClicked: Song? = null
        private set
    var lastSongLongClicked: Song? = null
        private set
    var lastAddedToQueue: Song? = null
        private set
    var lastPlayNext: Song? = null
        private set
    var lastSongInfo: Song? = null
        private set
    var lastExcluded: Song? = null
        private set
    var lastEditTags: Song? = null
        private set
    var lastDeleted: Song? = null
        private set
    var lastAddToPlaylist: Pair<Playlist, PlaylistData>? = null
        private set
    var lastCreatePlaylistDialog: Song? = null
        private set
    var shuffleClicked = false
        private set

    // -- Content setup --

    fun setContent(
        uiState: SongListUiState,
        playlists: List<Playlist> = emptyList(),
    ) {
        renderSongList(
            uiState = uiState,
            playlists = playlists,
            onSongClick = { lastSongClicked = it },
            onSongLongClick = { lastSongLongClicked = it },
        )
    }

    fun setContentWithViewModel(
        viewModel: SongListViewModel,
        playlists: List<Playlist> = emptyList(),
    ) {
        resetCallbacks()
        rule.setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            renderSongListContent(
                uiState = uiState,
                playlists = playlists,
                onSongClick = { viewModel.onSongClick(it) },
                onSongLongClick = { viewModel.onSongLongClick(it) },
            )
        }
    }

    private fun renderSongList(
        uiState: SongListUiState,
        playlists: List<Playlist>,
        onSongClick: (Song) -> Unit,
        onSongLongClick: (Song) -> Unit,
    ) {
        resetCallbacks()
        rule.setContent {
            renderSongListContent(uiState, playlists, onSongClick, onSongLongClick)
        }
    }

    @Composable
    private fun renderSongListContent(
        uiState: SongListUiState,
        playlists: List<Playlist>,
        onSongClick: (Song) -> Unit,
        onSongLongClick: (Song) -> Unit,
    ) {
        AppTheme {
            SongList(
                uiState = uiState,
                playlists = playlists.toImmutableList(),
                onSongClick = onSongClick,
                onSongLongClick = onSongLongClick,
                onAddToQueue = { lastAddedToQueue = it },
                onAddToPlaylist = { playlist, data -> lastAddToPlaylist = playlist to data },
                onShowCreatePlaylistDialog = { lastCreatePlaylistDialog = it },
                onPlayNext = { lastPlayNext = it },
                onSongInfo = { lastSongInfo = it },
                onExclude = { lastExcluded = it },
                onEditTags = { lastEditTags = it },
                onDelete = { lastDeleted = it },
                onShuffle = { shuffleClicked = true },
            )
        }
    }

    private fun resetCallbacks() {
        lastSongClicked = null
        lastSongLongClicked = null
        lastAddedToQueue = null
        lastPlayNext = null
        lastSongInfo = null
        lastExcluded = null
        lastEditTags = null
        lastDeleted = null
        lastAddToPlaylist = null
        lastCreatePlaylistDialog = null
        shuffleClicked = false
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
        rule.onNodeWithContentDescription("Song context menu").performClick()
    }

    fun clickMenuItem(text: String) {
        rule.onNodeWithText(text).performClick()
    }
}
