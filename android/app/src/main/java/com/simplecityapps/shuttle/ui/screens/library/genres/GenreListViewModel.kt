package com.simplecityapps.shuttle.ui.screens.library.genres

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.mediaprovider.SongImportState
import com.simplecityapps.mediaprovider.SongImportStateProvider
import com.simplecityapps.mediaprovider.repository.genres.comparator
import com.simplecityapps.shuttle.model.Genre
import com.simplecityapps.shuttle.sorting.GenreSortOrder
import com.simplecityapps.shuttle.ui.actions.ObserveGenres
import com.simplecityapps.shuttle.ui.screens.library.SortPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class GenreListUiState(
    val genres: List<Genre> = emptyList(),
    val loadingState: LoadingState = LoadingState.Loading,
    val scanProgress: Progress? = null,
    val sortOrder: GenreSortOrder = GenreSortOrder.Default,
) {
    enum class LoadingState { Loading, Scanning, Ready, Empty }
}

@HiltViewModel
class GenreListViewModel @Inject constructor(
    observeGenres: ObserveGenres,
    private val sortPreferenceManager: SortPreferences,
    mediaImportObserver: SongImportStateProvider
) : ViewModel() {

    private val _sortOrder = MutableStateFlow(sortPreferenceManager.sortOrderGenreList)

    val uiState: StateFlow<GenreListUiState> = combine(
        observeGenres(),
        mediaImportObserver.songImportState,
        _sortOrder,
    ) { genres, songImportState, sortOrder ->
        if (songImportState is SongImportState.ImportProgress) {
            GenreListUiState(
                loadingState = GenreListUiState.LoadingState.Scanning,
                scanProgress = songImportState.progress,
                sortOrder = sortOrder,
            )
        } else {
            val sortedGenres = genres.sortedWith(sortOrder.comparator)
            GenreListUiState(
                genres = sortedGenres,
                sortOrder = sortOrder,
                loadingState = if (sortedGenres.isEmpty()) {
                    GenreListUiState.LoadingState.Empty
                } else {
                    GenreListUiState.LoadingState.Ready
                },
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = GenreListUiState(),
    )

    fun setSortOrder(sortOrder: GenreSortOrder) {
        sortPreferenceManager.sortOrderGenreList = sortOrder
        _sortOrder.value = sortOrder
    }
}
