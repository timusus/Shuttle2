package com.simplecityapps.shuttle.ui.screens.library.genres

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.ui.actions.ObserveGenreCovers
import com.simplecityapps.shuttle.ui.actions.ObserveGenres
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/** Kept apart from [GenreListViewModel] so the list shows first and a screen with no artwork skips these queries. */
@OptIn(ExperimentalCoroutinesApi::class)
@ViewModelKey(GenreCoversViewModel::class)
@ContributesIntoMap(AppScope::class)
class GenreCoversViewModel @Inject constructor(
    observeGenres: ObserveGenres,
    observeGenreCovers: ObserveGenreCovers,
) : ViewModel() {

    val uiState: StateFlow<Map<String, List<Song>>> = observeGenres()
        .flatMapLatest { genres -> observeGenreCovers(genres) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyMap(),
        )
}
