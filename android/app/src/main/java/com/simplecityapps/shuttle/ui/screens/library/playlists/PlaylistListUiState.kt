package com.simplecityapps.shuttle.ui.screens.library.playlists

import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistSortOrder
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.SmartPlaylist

data class PlaylistListUiState(
    val playlists: List<Playlist> = emptyList(),
    val smartPlaylists: List<SmartPlaylist> = emptyList(),
    /** The Favorites playlist, pinned first among the smart playlists; null until it's loaded. */
    val favoritesPlaylist: Playlist? = null,
    val sortOrder: PlaylistSortOrder = PlaylistSortOrder.Default,
    val loadingState: LoadingState = LoadingState.Loading,
    val scanProgress: Progress? = null,
) {
    enum class LoadingState { Loading, Scanning, Ready }
}
