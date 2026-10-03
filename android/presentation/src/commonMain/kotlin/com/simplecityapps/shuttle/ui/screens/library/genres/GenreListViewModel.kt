package com.simplecityapps.shuttle.ui.screens.library.genres

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.mediaprovider.SongImportState
import com.simplecityapps.mediaprovider.SongImportStateProvider
import com.simplecityapps.mediaprovider.repository.genres.comparator
import com.simplecityapps.shuttle.model.Genre
import com.simplecityapps.shuttle.sorting.GenreSortOrder
import com.simplecityapps.shuttle.sorting.LetterSection
import com.simplecityapps.shuttle.sorting.genreLetterIndex
import com.simplecityapps.shuttle.ui.actions.ObserveGenres
import com.simplecityapps.shuttle.ui.screens.library.IndexedList
import com.simplecityapps.shuttle.ui.screens.library.LibraryViewSetting
import com.simplecityapps.shuttle.ui.screens.library.ReadLibraryViewSetting
import com.simplecityapps.shuttle.ui.screens.library.SaveLibraryViewSetting
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
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
    /** The genres' letter sections when sorted by name ([genreLetterIndex]); null for any other sort. */
    val letterIndex: List<LetterSection>? = null,
) {
    /** [Scanning] while an import runs; the list still carries what's already imported, for a screen that keeps showing it. */
    enum class LoadingState { Loading, Scanning, Ready, Empty }
}

@ViewModelKey(GenreListViewModel::class)
@ContributesIntoMap(AppScope::class)
class GenreListViewModel @Inject constructor(
    observeGenres: ObserveGenres,
    readSetting: ReadLibraryViewSetting,
    private val saveSetting: SaveLibraryViewSetting,
    mediaImportObserver: SongImportStateProvider
) : ViewModel() {

    private val _sortOrder = MutableStateFlow(readSetting(LibraryViewSetting.GenreSort))

    private val sortedGenres = combine(observeGenres(), _sortOrder) { genres, sortOrder ->
        val sorted = genres.sortedWith(sortOrder.comparator)
        IndexedList(sorted, sortOrder, genreLetterIndex(sorted, sortOrder))
    }

    val uiState: StateFlow<GenreListUiState> = combine(
        sortedGenres,
        mediaImportObserver.songImportState,
    ) { sorted, songImportState ->
        val sortedGenres = sorted.items
        GenreListUiState(
            genres = sortedGenres,
            sortOrder = sorted.sortOrder,
            letterIndex = sorted.letterIndex,
            loadingState = when {
                songImportState is SongImportState.ImportProgress -> GenreListUiState.LoadingState.Scanning
                sortedGenres.isEmpty() -> GenreListUiState.LoadingState.Empty
                else -> GenreListUiState.LoadingState.Ready
            },
            scanProgress = (songImportState as? SongImportState.ImportProgress)?.progress,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = GenreListUiState(),
    )

    fun setSortOrder(sortOrder: GenreSortOrder) {
        saveSetting(LibraryViewSetting.GenreSort, sortOrder)
        _sortOrder.value = sortOrder
    }
}
