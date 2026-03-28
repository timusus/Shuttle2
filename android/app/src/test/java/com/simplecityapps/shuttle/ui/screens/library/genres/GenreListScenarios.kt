package com.simplecityapps.shuttle.ui.screens.library.genres

import com.simplecityapps.createGenre
import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.shuttle.model.Genre

fun readyGenreList(
    genres: List<Genre> = listOf(createGenre()),
) = GenreListUiState(
    genres = genres,
    loadingState = GenreListUiState.LoadingState.Ready,
)

fun scanningGenreList(progress: Progress? = null) =
    GenreListUiState(
        loadingState = GenreListUiState.LoadingState.Scanning,
        scanProgress = progress,
    )

fun emptyGenreList() = GenreListUiState(
    loadingState = GenreListUiState.LoadingState.Empty,
)

val loadingGenreList = GenreListUiState(
    loadingState = GenreListUiState.LoadingState.Loading,
)
