package com.simplecityapps.shuttle.ui.screens.library.albums.detail

import com.simplecityapps.createAlbum
import com.simplecityapps.createSong
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.Song

fun readyAlbumDetail(
    album: Album = createAlbum(),
    songs: List<Song> = listOf(createSong()),
    currentSong: Song? = null,
) = AlbumDetailUiState(
    album = album,
    songs = songs,
    currentSong = currentSong,
    loadingState = if (songs.isEmpty()) AlbumDetailUiState.LoadingState.Empty else AlbumDetailUiState.LoadingState.Ready,
)

fun emptyAlbumDetail(
    album: Album = createAlbum(songCount = 0, duration = 0),
) = readyAlbumDetail(album = album, songs = emptyList())

val loadingAlbumDetail = AlbumDetailUiState(loadingState = AlbumDetailUiState.LoadingState.Loading)
