package com.simplecityapps.shuttle.ui.screens.library.albumartists.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData
import com.simplecityapps.shuttle.ui.theme.AppTheme
import kotlinx.collections.immutable.toImmutableList

class AlbumArtistDetailRobot(private val rule: ComposeContentTestRule) {

    // -- Callback captures --

    var lastAlbumClicked: Album? = null
        private set
    var lastAlbumOpened: Album? = null
        private set
    var lastAlbumSongClicked: Pair<Song, List<Song>>? = null
        private set
    var lastAlbumPlay: Album? = null
        private set
    var lastAlbumAddedToQueue: Album? = null
        private set
    var lastAlbumPlayNext: Album? = null
        private set
    var lastAlbumExcluded: Album? = null
        private set
    var lastAlbumEditTags: Album? = null
        private set

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
    var lastAlbumAddToPlaylist: Pair<Playlist, PlaylistData>? = null
        private set
    var lastAlbumCreatePlaylistDialog: Album? = null
        private set

    private var renderedUiState: MutableState<AlbumArtistDetailUiState>? = null

    // -- Content setup --

    fun setContent(
        uiState: AlbumArtistDetailUiState,
        playlists: List<Playlist> = emptyList(),
    ) {
        renderContent(uiState = uiState, playlists = playlists)
    }

    /** Pushes a new [uiState] into the existing composition, as a re-emitted StateFlow value would. */
    fun updateContent(uiState: AlbumArtistDetailUiState) {
        renderedUiState?.value = uiState
    }

    fun setContentWithViewModel(
        viewModel: AlbumArtistDetailViewModel,
        playlists: List<Playlist> = emptyList(),
    ) {
        resetCallbacks()
        rule.setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            renderComposable(uiState = uiState, playlists = playlists)
        }
    }

    private fun renderContent(
        uiState: AlbumArtistDetailUiState,
        playlists: List<Playlist>,
    ) {
        resetCallbacks()
        val state = mutableStateOf(uiState)
        renderedUiState = state
        rule.setContent {
            val current by state
            renderComposable(current, playlists)
        }
    }

    @Composable
    private fun renderComposable(
        uiState: AlbumArtistDetailUiState,
        playlists: List<Playlist>,
    ) {
        AppTheme {
            AlbumArtistDetail(
                uiState = uiState,
                playlists = playlists.toImmutableList(),
                onNavigateUp = {},
                onPlay = {},
                onShuffle = {},
                onShuffleAlbums = {},
                onAddAllToQueue = {},
                onPlayAllNext = {},
                onEditArtistTags = {},
                onAddArtistToPlaylist = {},
                onAlbumClick = { lastAlbumClicked = it },
                onOpenAlbum = { lastAlbumOpened = it },
                onAlbumPlay = { lastAlbumPlay = it },
                onAlbumAddToQueue = { lastAlbumAddedToQueue = it },
                onAlbumPlayNext = { lastAlbumPlayNext = it },
                onAlbumExclude = { lastAlbumExcluded = it },
                onAlbumEditTags = { lastAlbumEditTags = it },
                onAlbumAddToPlaylist = { playlist, data -> lastAlbumAddToPlaylist = playlist to data },
                onAlbumShowCreatePlaylistDialog = { lastAlbumCreatePlaylistDialog = it },
                onSongClick = { lastSongClicked = it },
                onAlbumSongClick = { song, songs -> lastAlbumSongClicked = song to songs },
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
        lastAlbumClicked = null
        lastAlbumOpened = null
        lastAlbumSongClicked = null
        lastAlbumPlay = null
        lastAlbumAddedToQueue = null
        lastAlbumPlayNext = null
        lastAlbumExcluded = null
        lastAlbumEditTags = null
        lastAlbumAddToPlaylist = null
        lastAlbumCreatePlaylistDialog = null
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

    /** A track rendered inside an expanded album, as opposed to the artist-wide Songs section. */
    fun assertAlbumTrackDisplayed(songName: String) {
        albumTrack(songName).assertIsDisplayed()
    }

    fun assertAlbumTrackNotDisplayed(songName: String) {
        albumTrack(songName).assertDoesNotExist()
    }

    fun assertCurrentSongHighlighted(songName: String) {
        rule.onNode(hasText(songName)).assertIsDisplayed()
        rule.onNodeWithContentDescription("Now playing").assertIsDisplayed()
    }

    fun assertCurrentSongNotHighlighted() {
        rule.onNodeWithContentDescription("Now playing").assertDoesNotExist()
    }

    fun assertTopBarTitleDisplayed(text: String, substring: Boolean = false) {
        rule.onNode(hasText(text, substring = substring) and hasAnyAncestor(hasTestTag("detail-top-bar-title"))).assertIsDisplayed()
    }

    fun assertTopBarTitleNotDisplayed(text: String) {
        rule.onNode(hasText(text) and hasAnyAncestor(hasTestTag("detail-top-bar-title"))).assertDoesNotExist()
    }

    // -- Interactions --

    /** Scrolls the list so the hero artwork and metadata header sit above the top bar. */
    fun scrollPastHeader() {
        rule.onNode(hasScrollToIndexAction()).performScrollToIndex(3)
    }

    fun clickText(text: String) {
        rule.onNodeWithText(text).performClick()
    }

    fun openSongContextMenu() {
        rule.onNodeWithContentDescription("Song context menu").performClick()
    }

    fun clickAlbumTrack(songName: String) {
        albumTrack(songName).performClick()
    }

    // The row is clickable, so its text is merged into the row node rather than left on descendants.
    private fun albumTrack(songName: String) = rule.onNode(hasTestTag("detail-song-row") and hasText(songName))

    fun clickAlbumArtwork() {
        rule.onAllNodesWithTag("album-artwork").onFirst().performClick()
    }

    fun openAlbumContextMenu() {
        rule.onAllNodesWithContentDescription("Album menu").onFirst().performClick()
    }

    fun clickMenuItem(text: String) {
        rule.onNodeWithText(text).performClick()
    }
}
