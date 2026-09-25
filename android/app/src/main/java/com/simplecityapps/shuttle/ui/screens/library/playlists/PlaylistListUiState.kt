package com.simplecityapps.shuttle.ui.screens.library.playlists

import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistSortOrder
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.SmartPlaylist

data class PlaylistListUiState(
    val playlists: List<Playlist> = emptyList(),
    val smartPlaylists: List<SmartPlaylist> = emptyList(),
    val sortOrder: PlaylistSortOrder = PlaylistSortOrder.Default,
    val loadingState: LoadingState = LoadingState.Loading,
    val scanProgress: Progress? = null,
) {
    enum class LoadingState { Loading, Scanning, Ready }
}

sealed interface PlaylistListUiEvent {
    data class AddedToQueue(val playlistName: String) : PlaylistListUiEvent
    data class PlaybackFailed(val errorMessage: String?) : PlaylistListUiEvent
}
