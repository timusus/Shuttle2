package com.simplecityapps.shuttle.ui.screens.library.albums.detail

import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData

data class AlbumDetailUiState(
    val album: Album? = null,
    val songs: List<Song> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val currentSong: Song? = null,
    val loadingState: LoadingState = LoadingState.Loading,
) {
    enum class LoadingState { Loading, Ready, Empty }
}

sealed interface AlbumDetailUiEvent {
    data class AddedToQueue(val songCount: Int) : AlbumDetailUiEvent
    data class PlaybackFailed(val errorMessage: String?) : AlbumDetailUiEvent
    data class EditTags(val songs: List<Song>) : AlbumDetailUiEvent
    data class AddedToPlaylist(val playlist: Playlist, val playlistData: PlaylistData) : AlbumDetailUiEvent
    data class PlaylistDuplicatesFound(
        val playlist: Playlist,
        val playlistData: PlaylistData,
        val deduplicatedSongs: PlaylistData.Songs,
        val duplicates: List<Song>,
    ) : AlbumDetailUiEvent
    data class PlaylistAddFailed(val message: String?) : AlbumDetailUiEvent
}
