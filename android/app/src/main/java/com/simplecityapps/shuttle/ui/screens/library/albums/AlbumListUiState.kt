package com.simplecityapps.shuttle.ui.screens.library.albums

import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.sorting.AlbumSortOrder
import com.simplecityapps.shuttle.ui.screens.library.ViewMode

data class AlbumListUiState(
    val albums: List<Album> = emptyList(),
    val selectedAlbums: Set<Album> = emptySet(),
    val viewMode: ViewMode = ViewMode.List,
    val sortOrder: AlbumSortOrder = AlbumSortOrder.Default,
    val loadingState: LoadingState = LoadingState.Loading,
    val scanProgress: Progress? = null,
) {
    enum class LoadingState { Loading, Scanning, Ready, Empty }

    val isSelecting: Boolean get() = selectedAlbums.isNotEmpty()
}

sealed interface AlbumListUiEvent {
    data class AddedToQueue(val albumCount: Int) : AlbumListUiEvent
    data class PlaybackFailed(val errorMessage: String?) : AlbumListUiEvent
    data class EditTags(val songs: List<Song>) : AlbumListUiEvent
    data object LibraryEmpty : AlbumListUiEvent
}
