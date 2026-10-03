package com.simplecityapps.shuttle.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.mediaprovider.repository.genres.GenreQuery
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.ui.actions.ObserveGenreCovers
import com.simplecityapps.shuttle.ui.actions.ObserveGenres
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The songs whose covers make up a genre's mosaic (#652), the same ones its Library row draws: up to four from
 * different albums. Apart from [GenreDetailViewModel] so a screen that draws no mosaic (Android's) never runs the query.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GenreDetailCoversViewModel @AssistedInject constructor(
    @Assisted genreName: String,
    observeGenres: ObserveGenres,
    observeGenreCovers: ObserveGenreCovers,
) : ViewModel() {
    @AssistedFactory
    @ManualViewModelAssistedFactoryKey(Factory::class)
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(genreName: String): GenreDetailCoversViewModel
    }

    val uiState: StateFlow<List<Song>> = observeGenres(GenreQuery.GenreName(genreName))
        .map { it.firstOrNull() }
        .distinctUntilChanged { old, new -> old?.name == new?.name }
        .flatMapLatest { genre ->
            if (genre == null) flowOf(emptyList()) else observeGenreCovers(listOf(genre)).map { it[genre.name].orEmpty() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
