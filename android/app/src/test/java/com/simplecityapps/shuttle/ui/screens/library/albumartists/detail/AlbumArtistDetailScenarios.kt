package com.simplecityapps.shuttle.ui.screens.library.albumartists.detail

import com.simplecityapps.createAlbum
import com.simplecityapps.createAlbumArtist
import com.simplecityapps.createSong
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.Song

fun readyAlbumArtistDetail(
    albumArtist: AlbumArtist = createAlbumArtist(),
    albums: List<Album> = listOf(createAlbum()),
    songs: List<Song> = listOf(createSong()),
    currentSong: Song? = null,
) = AlbumArtistDetailUiState(
    albumArtist = albumArtist,
    albums = albums,
    songs = songs,
    currentSong = currentSong,
    loadingState = if (albums.isEmpty() && songs.isEmpty()) {
        AlbumArtistDetailUiState.LoadingState.Empty
    } else {
        AlbumArtistDetailUiState.LoadingState.Ready
    },
)

fun emptyAlbumArtistDetail(
    albumArtist: AlbumArtist = createAlbumArtist(albumCount = 0, songCount = 0),
) = readyAlbumArtistDetail(albumArtist = albumArtist, albums = emptyList(), songs = emptyList())

val loadingAlbumArtistDetail = AlbumArtistDetailUiState(
    loadingState = AlbumArtistDetailUiState.LoadingState.Loading,
)
