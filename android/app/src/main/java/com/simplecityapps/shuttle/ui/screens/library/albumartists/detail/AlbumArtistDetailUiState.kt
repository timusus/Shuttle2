package com.simplecityapps.shuttle.ui.screens.library.albumartists.detail

import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.AlbumGroupKey
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.Song

data class AlbumArtistDetailUiState(
    val albumArtist: AlbumArtist? = null,
    val albums: List<Album> = emptyList(),
    val songs: List<Song> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val currentSong: Song? = null,
    /** Albums whose track list is unfolded in place, keyed the same way songs are grouped. */
    val expandedAlbums: Set<AlbumGroupKey> = emptySet(),
    val loadingState: LoadingState = LoadingState.Loading,
) {
    enum class LoadingState { Loading, Ready, Empty }

    /** The artist's songs belonging to [album], in track order. */
    fun songsForAlbum(album: Album): List<Song> = album.groupKey?.let { key ->
        songs.filter { it.albumGroupKey == key }
    }.orEmpty()
}
