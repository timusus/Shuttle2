package com.simplecityapps.shuttle.ui.screens.library.folders

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
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

/**
 * Test robot for [FolderList] and [FolderListItem] Compose characterisation tests.
 *
 * View state tests render via [FolderList]. Context menu tests render via [FolderListItem] directly, because
 * the FastScroller overlay in FolderList causes DropdownMenu popups to be immediately dismissed under
 * Robolectric.
 */
class FolderListRobot(private val rule: ComposeContentTestRule) {

    // -- Callback captures --

    var lastOpenedFolder: Folder? = null
        private set
    var navigatedUp: Boolean = false
        private set
    var lastPlayedFolder: Folder? = null
        private set
    var lastShuffledFolder: Folder? = null
        private set
    var lastFolderAddedToQueue: Folder? = null
        private set
    var lastFolderPlayedNext: Folder? = null
        private set
    var lastClickedSong: Song? = null
        private set
    var lastAddToPlaylist: Pair<Playlist, PlaylistData>? = null
        private set
    var lastCreatePlaylistDialog: Folder? = null
        private set

    private fun resetCallbacks() {
        lastOpenedFolder = null
        navigatedUp = false
        lastPlayedFolder = null
        lastShuffledFolder = null
        lastFolderAddedToQueue = null
        lastFolderPlayedNext = null
        lastClickedSong = null
        lastAddToPlaylist = null
        lastCreatePlaylistDialog = null
    }

    // -- Content setup --

    /** Render the full [FolderList] composable (for view state tests). */
    fun setContent(
        uiState: FolderListUiState,
        playlists: List<Playlist> = emptyList(),
    ) {
        resetCallbacks()
        rule.setContent {
            RenderFolderList(uiState, playlists)
        }
    }

    /** Render with a real [FolderListViewModel] (for integration tests). */
    fun setContentWithViewModel(viewModel: FolderListViewModel) {
        resetCallbacks()
        rule.setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            RenderFolderList(
                uiState = uiState,
                playlists = uiState.playlists,
                onFolderClick = viewModel::onFolderClick,
                onNavigateUp = viewModel::onNavigateUp,
            )
        }
    }

    /** Render a single [FolderListItem] (for context menu tests). */
    fun setItemContent(
        folder: Folder,
        playlists: List<Playlist> = emptyList(),
    ) {
        resetCallbacks()
        rule.setContent {
            AppTheme {
                FolderListItem(
                    folder = folder,
                    playlists = playlists.toImmutableList(),
                    onFolderClick = { lastOpenedFolder = it },
                    onPlayFolder = { lastPlayedFolder = it },
                    onShuffleFolder = { lastShuffledFolder = it },
                    onAddToQueue = { lastFolderAddedToQueue = it },
                    onPlayNext = { lastFolderPlayedNext = it },
                    onAddToPlaylist = { playlist, data -> lastAddToPlaylist = playlist to data },
                    onShowCreatePlaylistDialog = { lastCreatePlaylistDialog = it },
                )
            }
        }
    }

    @Composable
    private fun RenderFolderList(
        uiState: FolderListUiState,
        playlists: List<Playlist>,
        onFolderClick: (Folder) -> Unit = { lastOpenedFolder = it },
        onNavigateUp: () -> Unit = { navigatedUp = true },
    ) {
        AppTheme {
            FolderList(
                uiState = uiState,
                playlists = playlists.toImmutableList(),
                onFolderClick = { folder ->
                    lastOpenedFolder = folder
                    onFolderClick(folder)
                },
                onNavigateUp = {
                    navigatedUp = true
                    onNavigateUp()
                },
                onPlayFolder = { lastPlayedFolder = it },
                onShuffleFolder = { lastShuffledFolder = it },
                onAddFolderToQueue = { lastFolderAddedToQueue = it },
                onPlayFolderNext = { lastFolderPlayedNext = it },
                onSongClick = { lastClickedSong = it },
                onAddToPlaylist = { playlist, data -> lastAddToPlaylist = playlist to data },
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

    fun assertCanNavigateUp() {
        rule.onNodeWithContentDescription("Up one folder").assertIsDisplayed()
    }

    fun assertCannotNavigateUp() {
        rule.onNodeWithContentDescription("Up one folder").assertDoesNotExist()
    }

    // -- Interactions --

    fun clickText(text: String) {
        rule.onNodeWithText(text).performClick()
    }

    fun navigateUp() {
        rule.onNodeWithContentDescription("Up one folder").performClick()
    }

    fun openContextMenu() {
        rule.onNodeWithContentDescription("Folder menu").performClick()
    }

    fun clickMenuItem(text: String) {
        rule.onNodeWithText(text).performClick()
    }
}
