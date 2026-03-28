package com.simplecityapps.shuttle.ui.screens.library.albumartists.detail

import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData

data class AlbumArtistDetailUiState(
    val albumArtist: AlbumArtist? = null,
    val albums: List<Album> = emptyList(),
    val songs: List<Song> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val currentSong: Song? = null,
    val loadingState: LoadingState = LoadingState.Loading,
) {
    enum class LoadingState { Loading, Ready, Empty }
}

sealed interface AlbumArtistDetailUiEvent {
    data class AddedToQueue(val songCount: Int) : AlbumArtistDetailUiEvent
    data class PlaybackFailed(val errorMessage: String?) : AlbumArtistDetailUiEvent
    data class EditTags(val songs: List<Song>) : AlbumArtistDetailUiEvent
    data class AddedToPlaylist(val playlist: Playlist, val playlistData: PlaylistData) : AlbumArtistDetailUiEvent
    data class PlaylistDuplicatesFound(
        val playlist: Playlist,
        val playlistData: PlaylistData,
        val deduplicatedSongs: PlaylistData.Songs,
        val duplicates: List<Song>,
    ) : AlbumArtistDetailUiEvent
    data class PlaylistAddFailed(val message: String?) : AlbumArtistDetailUiEvent
}
