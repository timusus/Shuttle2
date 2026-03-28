package com.simplecityapps.shuttle.ui.screens.library.playlists

import com.simplecityapps.createPlaylist
import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.SmartPlaylist

fun readyPlaylistList(
    playlists: List<Playlist> = listOf(createPlaylist()),
    smartPlaylists: List<SmartPlaylist> = emptyList(),
) = PlaylistListUiState(
    playlists = playlists,
    smartPlaylists = smartPlaylists,
    loadingState = PlaylistListUiState.LoadingState.Ready,
)

fun scanningPlaylistList(progress: Progress? = null) = PlaylistListUiState(
    loadingState = PlaylistListUiState.LoadingState.Scanning,
    scanProgress = progress,
)

fun emptyPlaylistList() = PlaylistListUiState(
    loadingState = PlaylistListUiState.LoadingState.Empty,
)

val loadingPlaylistList = PlaylistListUiState(
    loadingState = PlaylistListUiState.LoadingState.Loading,
)
