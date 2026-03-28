package com.simplecityapps.shuttle.ui.screens.library.albums

import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.sorting.AlbumSortOrder
import com.simplecityapps.shuttle.ui.screens.library.ViewMode
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData

data class AlbumListUiState(
    val albums: List<Album> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val selectedAlbums: Set<Album> = emptySet(),
    val viewMode: ViewMode = ViewMode.List,
    val sortOrder: AlbumSortOrder = AlbumSortOrder.Default,
    val loadingState: LoadingState = LoadingState.Loading,
    val scanProgress: Progress? = null,
) {
    enum class LoadingState { Loading, Scanning, Ready, Empty }

    val isSelecting: Boolean get() = selectedAlbums.isNotEmpty()
}

sealed interface AlbumListUiEvent {
    data class AddedToQueue(val albumCount: Int) : AlbumListUiEvent
    data class PlaybackFailed(val errorMessage: String?) : AlbumListUiEvent
    data class EditTags(val songs: List<Song>) : AlbumListUiEvent
    data object LibraryEmpty : AlbumListUiEvent
    data class AddedToPlaylist(val playlist: Playlist, val playlistData: PlaylistData) : AlbumListUiEvent
    data class PlaylistDuplicatesFound(
        val playlist: Playlist,
        val playlistData: PlaylistData,
        val deduplicatedSongs: PlaylistData.Songs,
        val duplicates: List<Song>,
    ) : AlbumListUiEvent
    data class PlaylistAddFailed(val message: String?) : AlbumListUiEvent
}
