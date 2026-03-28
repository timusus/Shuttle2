package com.simplecityapps.shuttle.ui.screens.library.songs

import com.simplecityapps.createSong
import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.sorting.SongSortOrder

fun readySongList(
    songs: List<Song> = listOf(createSong()),
    selectedSongs: Set<Song> = emptySet(),
    sortOrder: SongSortOrder = SongSortOrder.Default,
) = SongListViewModel.ViewState.Ready(songs, selectedSongs, sortOrder)

fun scanningSongList(progress: Progress? = null) =
    SongListViewModel.ViewState.Scanning(progress)

fun emptySongList() = readySongList(songs = emptyList())

val loadingSongList = SongListViewModel.ViewState.Loading
