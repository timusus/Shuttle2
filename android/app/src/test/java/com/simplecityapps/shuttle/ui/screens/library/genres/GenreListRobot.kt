package com.simplecityapps.shuttle.ui.screens.library.genres

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import com.simplecityapps.shuttle.model.Genre
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData
import com.simplecityapps.shuttle.ui.theme.AppTheme
import kotlinx.collections.immutable.toImmutableList

/**
 * Test robot for [GenreList] and [GenreListItem] Compose characterisation tests.
 *
 * View state tests render via [GenreList]. Context menu tests render via
 * [GenreListItem] directly, because the [FastScroller] overlay in GenreList
 * causes DropdownMenu popups to be immediately dismissed under Robolectric.
 * This detail is encapsulated here — tests don't need to know about it.
 */
class GenreListRobot(private val rule: ComposeContentTestRule) {

    // -- Callback captures --

    var lastSelectedGenre: Genre? = null; private set
    var lastPlayedGenre: Genre? = null; private set
    var lastAddedToQueue: Genre? = null; private set
    var lastPlayNext: Genre? = null; private set
    var lastExcluded: Genre? = null; private set
    var lastEditTags: Genre? = null; private set
    var lastAddToPlaylist: Pair<Playlist, PlaylistData>? = null; private set
    var lastCreatePlaylistDialog: Genre? = null; private set

    private fun callbacks() = Callbacks(
        onSelectGenre = { lastSelectedGenre = it },
        onPlayGenre = { lastPlayedGenre = it },
        onAddToQueue = { lastAddedToQueue = it },
        onPlayNext = { lastPlayNext = it },
        onExclude = { lastExcluded = it },
        onEditTags = { lastEditTags = it },
        onAddToPlaylist = { playlist, data -> lastAddToPlaylist = playlist to data },
        onShowCreatePlaylistDialog = { lastCreatePlaylistDialog = it },
    )

    private fun resetCallbacks() {
        lastSelectedGenre = null
        lastPlayedGenre = null
        lastAddedToQueue = null
        lastPlayNext = null
        lastExcluded = null
        lastEditTags = null
        lastAddToPlaylist = null
        lastCreatePlaylistDialog = null
    }

    // -- Content setup --

    /** Render the full [GenreList] composable (for view state tests). */
    fun setContent(
        viewState: GenreListViewModel.ViewState,
        playlists: List<Playlist> = emptyList(),
    ) {
        resetCallbacks()
        val cb = callbacks()
        rule.setContent {
            AppTheme {
                GenreList(
                    viewState = viewState,
                    playlists = playlists.toImmutableList(),
                    onSelectGenre = cb.onSelectGenre,
                    onPlayGenre = cb.onPlayGenre,
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

    /** Render a single [GenreListItem] (for context menu tests). */
    fun setItemContent(
        genre: Genre,
        playlists: List<Playlist> = emptyList(),
    ) {
        resetCallbacks()
        val cb = callbacks()
        rule.setContent {
            AppTheme {
                GenreListItem(
                    genre = genre,
                    playlists = playlists.toImmutableList(),
                    onSelectGenre = cb.onSelectGenre,
                    onPlayGenre = cb.onPlayGenre,
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
        rule.onNodeWithContentDescription("Genre menu").performClick()
    }

    fun clickMenuItem(text: String) {
        rule.onNodeWithText(text).performClick()
    }

    private data class Callbacks(
        val onSelectGenre: (Genre) -> Unit,
        val onPlayGenre: (Genre) -> Unit,
        val onAddToQueue: (Genre) -> Unit,
        val onPlayNext: (Genre) -> Unit,
        val onExclude: (Genre) -> Unit,
        val onEditTags: (Genre) -> Unit,
        val onAddToPlaylist: (Playlist, PlaylistData) -> Unit,
        val onShowCreatePlaylistDialog: (Genre) -> Unit,
    )
}
