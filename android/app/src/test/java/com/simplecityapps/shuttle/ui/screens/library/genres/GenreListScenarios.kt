package com.simplecityapps.shuttle.ui.screens.library.genres

import com.simplecityapps.createGenre
import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.shuttle.model.Genre

fun readyGenreList(
    genres: List<Genre> = listOf(createGenre()),
) = GenreListViewModel.ViewState.Ready(genres)

fun scanningGenreList(progress: Progress? = null) =
    GenreListViewModel.ViewState.Scanning(progress)

fun emptyGenreList() = readyGenreList(genres = emptyList())

val loadingGenreList = GenreListViewModel.ViewState.Loading
