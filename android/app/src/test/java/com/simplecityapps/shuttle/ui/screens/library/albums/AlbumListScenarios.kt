package com.simplecityapps.shuttle.ui.screens.library.albums

import com.simplecityapps.createAlbum
import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.sorting.AlbumSortOrder
import com.simplecityapps.shuttle.ui.screens.library.ViewMode

fun readyAlbumList(
    albums: List<Album> = listOf(createAlbum()),
    selectedAlbums: Set<Album> = emptySet(),
    viewMode: ViewMode = ViewMode.List,
    sortOrder: AlbumSortOrder = AlbumSortOrder.Default,
) = AlbumListUiState(
    albums = albums,
    selectedAlbums = selectedAlbums,
    viewMode = viewMode,
    sortOrder = sortOrder,
    loadingState = if (albums.isEmpty()) AlbumListUiState.LoadingState.Empty else AlbumListUiState.LoadingState.Ready,
)

fun scanningAlbumList(progress: Progress? = null) = AlbumListUiState(
    loadingState = AlbumListUiState.LoadingState.Scanning,
    scanProgress = progress,
)

fun emptyAlbumList() = readyAlbumList(albums = emptyList())

val loadingAlbumList = AlbumListUiState(loadingState = AlbumListUiState.LoadingState.Loading)
