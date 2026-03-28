package com.simplecityapps.shuttle.ui.screens.library.songs

import com.simplecityapps.createSong
import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.sorting.SongSortOrder

fun readySongList(
    songs: List<Song> = listOf(createSong()),
    selectedSongs: Set<Song> = emptySet(),
    sortOrder: SongSortOrder = SongSortOrder.Default,
) = SongListUiState(
    songs = songs,
    selectedSongs = selectedSongs,
    sortOrder = sortOrder,
    loadingState = if (songs.isEmpty()) SongListUiState.LoadingState.Empty else SongListUiState.LoadingState.Ready,
)

fun scanningSongList(progress: Progress? = null) = SongListUiState(
    loadingState = SongListUiState.LoadingState.Scanning,
    scanProgress = progress,
)

fun emptySongList() = readySongList(songs = emptyList())

val loadingSongList = SongListUiState(loadingState = SongListUiState.LoadingState.Loading)
