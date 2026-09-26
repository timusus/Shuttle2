package com.simplecityapps.shuttle.ui.screens.library.playlists

import com.simplecityapps.createPlaylist
import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.SmartPlaylist
import com.simplecityapps.shuttle.model.Song

fun readyPlaylistList(
    playlists: List<Playlist> = listOf(createPlaylist()),
    smartPlaylists: List<SmartPlaylist> = emptyList(),
    favoritesPlaylist: Playlist? = null,
    covers: Map<Long, List<Song>> = emptyMap(),
) = PlaylistListUiState(
    playlists = playlists,
    smartPlaylists = smartPlaylists,
    favoritesPlaylist = favoritesPlaylist,
    covers = covers,
    loadingState = PlaylistListUiState.LoadingState.Ready,
)

fun scanningPlaylistList(progress: Progress? = null) = PlaylistListUiState(
    loadingState = PlaylistListUiState.LoadingState.Scanning,
    scanProgress = progress,
)

val loadingPlaylistList = PlaylistListUiState(
    loadingState = PlaylistListUiState.LoadingState.Loading,
)
