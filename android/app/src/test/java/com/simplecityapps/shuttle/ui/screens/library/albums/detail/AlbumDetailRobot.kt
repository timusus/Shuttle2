package com.simplecityapps.shuttle.ui.screens.library.albums.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData
import com.simplecityapps.shuttle.ui.theme.AppTheme
import kotlinx.collections.immutable.toImmutableList

class AlbumDetailRobot(private val rule: ComposeContentTestRule) {

    // -- Callback captures --

    var lastSongClicked: Song? = null
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

    // -- Content setup --

    fun setContent(
        uiState: AlbumDetailUiState,
        playlists: List<Playlist> = emptyList(),
    ) {
        renderAlbumDetail(
            uiState = uiState,
            playlists = playlists,
            onSongClick = { lastSongClicked = it },
        )
    }

    fun setContentWithViewModel(
        viewModel: AlbumDetailViewModel,
        playlists: List<Playlist> = emptyList(),
    ) {
        resetCallbacks()
        rule.setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            renderAlbumDetailContent(
                uiState = uiState,
                playlists = playlists,
                onSongClick = { viewModel.onSongClick(it) },
            )
        }
    }

    private fun renderAlbumDetail(
        uiState: AlbumDetailUiState,
        playlists: List<Playlist>,
        onSongClick: (Song) -> Unit,
    ) {
        resetCallbacks()
        rule.setContent {
            renderAlbumDetailContent(uiState, playlists, onSongClick)
        }
    }

    @Composable
    private fun renderAlbumDetailContent(
        uiState: AlbumDetailUiState,
        playlists: List<Playlist>,
        onSongClick: (Song) -> Unit,
    ) {
        AppTheme {
            AlbumDetail(
                uiState = uiState,
                playlists = playlists.toImmutableList(),
                onSongClick = onSongClick,
                onAddToQueue = { lastAddedToQueue = it },
                onAddToPlaylist = { playlist, data -> lastAddToPlaylist = playlist to data },
                onShowCreatePlaylistDialog = { lastCreatePlaylistDialog = it },
                onPlayNext = { lastPlayNext = it },
                onSongInfo = { lastSongInfo = it },
                onExclude = { lastExcluded = it },
                onEditTags = { lastEditTags = it },
                onDelete = { lastDeleted = it },
            )
        }
    }

    private fun resetCallbacks() {
        lastSongClicked = null
        lastAddedToQueue = null
        lastPlayNext = null
        lastSongInfo = null
        lastExcluded = null
        lastEditTags = null
        lastDeleted = null
        lastAddToPlaylist = null
        lastCreatePlaylistDialog = null
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

    fun assertCurrentSongHighlighted(songName: String) {
        rule.onNode(hasText(songName)).assertIsDisplayed()
        // The actual highlight is verified by the "Now playing" content description
        rule.onNodeWithContentDescription("Now playing").assertIsDisplayed()
    }

    fun assertCurrentSongNotHighlighted() {
        rule.onNodeWithContentDescription("Now playing").assertDoesNotExist()
    }

    // -- Interactions --

    fun clickText(text: String) {
        rule.onNodeWithText(text).performClick()
    }

    fun openContextMenu() {
        rule.onNodeWithContentDescription("Song context menu").performClick()
    }

    fun clickMenuItem(text: String) {
        rule.onNodeWithText(text).performClick()
    }
}
