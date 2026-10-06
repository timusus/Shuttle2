package com.simplecityapps.shuttle.ui.screens.library.albumartists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.mediaprovider.SongImportState
import com.simplecityapps.mediaprovider.SongImportStateProvider
import com.simplecityapps.mediaprovider.repository.artists.AlbumArtistQuery
import com.simplecityapps.mediaprovider.repository.artists.comparator
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.settings.ArtistSettings
import com.simplecityapps.shuttle.settings.ObserveSetting
import com.simplecityapps.shuttle.sorting.AlbumArtistSortOrder
import com.simplecityapps.shuttle.sorting.LetterSection
import com.simplecityapps.shuttle.sorting.albumArtistLetterIndex
import com.simplecityapps.shuttle.ui.actions.ObserveArtists
import com.simplecityapps.shuttle.ui.common.SelectionState
import com.simplecityapps.shuttle.ui.screens.library.IndexedList
import com.simplecityapps.shuttle.ui.screens.library.LibraryViewSetting
import com.simplecityapps.shuttle.ui.screens.library.ReadLibraryViewSetting
import com.simplecityapps.shuttle.ui.screens.library.SaveLibraryViewSetting
import com.simplecityapps.shuttle.ui.screens.library.ViewMode
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

data class AlbumArtistListUiState(
    val albumArtists: List<AlbumArtist> = emptyList(),
    val selectedArtists: Set<AlbumArtist> = emptySet(),
    val viewMode: ViewMode = ViewMode.List,
    val sortOrder: AlbumArtistSortOrder = AlbumArtistSortOrder.Default,
    val loadingState: LoadingState = LoadingState.Loading,
    val scanProgress: Progress? = null,
    /** The artists' letter sections for a name-first sort ([albumArtistLetterIndex]); null for any other sort. */
    val letterIndex: List<LetterSection>? = null,
) {
    /** [Scanning] while an import runs; the list still carries what's already imported, for a screen that keeps showing it. */
    enum class LoadingState { Loading, Scanning, Ready, Empty }

    val isSelecting: Boolean get() = selectedArtists.isNotEmpty()
}

@ViewModelKey(AlbumArtistListViewModel::class)
@ContributesIntoMap(AppScope::class)
class AlbumArtistListViewModel @Inject constructor(
    observeArtists: ObserveArtists,
    observeSetting: ObserveSetting,
    readSetting: ReadLibraryViewSetting,
    private val saveSetting: SaveLibraryViewSetting,
    mediaImportObserver: SongImportStateProvider,
) : ViewModel() {

    private val selectionState = SelectionState<AlbumArtist>()

    private val _viewMode = MutableStateFlow(readSetting(LibraryViewSetting.ArtistViewMode))

    private val _sortOrder = MutableStateFlow(readSetting(LibraryViewSetting.ArtistSort))

    // Sorted and indexed as the library or the sort changes, not on each import progress tick (#627).
    // The album artists, or with ArtistSettings.ShowCreditedArtists on, every artist a song credits as well (#637).
    @OptIn(ExperimentalCoroutinesApi::class)
    private val artists = observeSetting(ArtistSettings.ShowCreditedArtists)
        .distinctUntilChanged()
        .flatMapLatest { showCredited -> observeArtists(if (showCredited) AlbumArtistQuery.Credited() else AlbumArtistQuery.All()) }

    private val sortedArtists = combine(artists, _sortOrder) { albumArtists, sortOrder ->
        val sorted = albumArtists.sortedWith(sortOrder.comparator)
        IndexedList(sorted, sortOrder, albumArtistLetterIndex(sorted, sortOrder))
    }

    val uiState: StateFlow<AlbumArtistListUiState> = combine(
        sortedArtists,
        mediaImportObserver.songImportState,
        selectionState.selectedItems,
        _viewMode,
    ) { sorted, songImportState, selectedArtists, viewMode ->
        val albumArtists = sorted.items
        AlbumArtistListUiState(
            albumArtists = albumArtists,
            sortOrder = sorted.sortOrder,
            letterIndex = sorted.letterIndex,
            selectedArtists = selectedArtists,
            viewMode = viewMode,
            loadingState = when {
                songImportState is SongImportState.ImportProgress -> AlbumArtistListUiState.LoadingState.Scanning
                albumArtists.isEmpty() -> AlbumArtistListUiState.LoadingState.Empty
                else -> AlbumArtistListUiState.LoadingState.Ready
            },
            scanProgress = (songImportState as? SongImportState.ImportProgress)?.progress,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AlbumArtistListUiState(),
    )

    fun onArtistClick(albumArtist: AlbumArtist) {
        selectionState.toggle(albumArtist)
    }

    fun onArtistLongClick(albumArtist: AlbumArtist) {
        selectionState.toggle(albumArtist)
    }

    fun setSortOrder(sortOrder: AlbumArtistSortOrder) {
        saveSetting(LibraryViewSetting.ArtistSort, sortOrder)
        _sortOrder.value = sortOrder
    }

    fun setViewMode(mode: ViewMode) {
        saveSetting(LibraryViewSetting.ArtistViewMode, mode)
        _viewMode.value = mode
    }

    fun clearSelection() {
        selectionState.clear()
    }
}
