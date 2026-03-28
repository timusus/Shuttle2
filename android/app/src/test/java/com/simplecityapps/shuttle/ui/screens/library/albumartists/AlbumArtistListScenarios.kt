package com.simplecityapps.shuttle.ui.screens.library.albumartists

import com.simplecityapps.createAlbumArtist
import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.ui.screens.library.ViewMode

fun readyAlbumArtistList(
    albumArtists: List<AlbumArtist> = listOf(createAlbumArtist()),
    selectedArtists: Set<AlbumArtist> = emptySet(),
    viewMode: ViewMode = ViewMode.List,
) = AlbumArtistListUiState(
    albumArtists = albumArtists,
    selectedArtists = selectedArtists,
    viewMode = viewMode,
    loadingState = if (albumArtists.isEmpty()) AlbumArtistListUiState.LoadingState.Empty else AlbumArtistListUiState.LoadingState.Ready,
)

fun scanningAlbumArtistList(progress: Progress? = null) = AlbumArtistListUiState(
    loadingState = AlbumArtistListUiState.LoadingState.Scanning,
    scanProgress = progress,
)

fun emptyAlbumArtistList() = readyAlbumArtistList(albumArtists = emptyList())

val loadingAlbumArtistList = AlbumArtistListUiState(loadingState = AlbumArtistListUiState.LoadingState.Loading)
