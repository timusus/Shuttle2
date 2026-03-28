package com.simplecityapps.shuttle.ui.screens.library.albumartists

import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.ui.screens.library.ViewMode

data class AlbumArtistListUiState(
    val albumArtists: List<AlbumArtist> = emptyList(),
    val selectedArtists: Set<AlbumArtist> = emptySet(),
    val viewMode: ViewMode = ViewMode.List,
    val loadingState: LoadingState = LoadingState.Loading,
    val scanProgress: Progress? = null,
) {
    enum class LoadingState { Loading, Scanning, Ready, Empty }

    val isSelecting: Boolean get() = selectedArtists.isNotEmpty()
}

sealed interface AlbumArtistListUiEvent {
    data class AddedToQueue(val artistName: String) : AlbumArtistListUiEvent
    data class PlaybackFailed(val errorMessage: String?) : AlbumArtistListUiEvent
    data class EditTags(val songs: List<Song>) : AlbumArtistListUiEvent
}
