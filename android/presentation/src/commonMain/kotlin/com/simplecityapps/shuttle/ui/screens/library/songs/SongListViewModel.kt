package com.simplecityapps.shuttle.ui.screens.library.songs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.mediaprovider.SongImportState
import com.simplecityapps.mediaprovider.SongImportStateProvider
import com.simplecityapps.mediaprovider.repository.songs.comparator
import com.simplecityapps.shuttle.di.IoDispatcher
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import com.simplecityapps.shuttle.sorting.LetterSection
import com.simplecityapps.shuttle.sorting.SongSortOrder
import com.simplecityapps.shuttle.sorting.songLetterIndex
import com.simplecityapps.shuttle.ui.actions.ObserveSongs
import com.simplecityapps.shuttle.ui.common.SelectionState
import com.simplecityapps.shuttle.ui.screens.library.IndexedList
import com.simplecityapps.shuttle.ui.screens.library.LibraryViewSetting
import com.simplecityapps.shuttle.ui.screens.library.ReadLibraryViewSetting
import com.simplecityapps.shuttle.ui.screens.library.SaveLibraryViewSetting
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SongListUiState(
    val songs: List<Song> = emptyList(),
    val selectedSongs: Set<Song> = emptySet(),
    val sortOrder: SongSortOrder = SongSortOrder.Default,
    val loadingState: LoadingState = LoadingState.Loading,
    val scanProgress: Progress? = null,
    /** The songs' letter sections for a name sort ([songLetterIndex]); null for any other sort. */
    val letterIndex: List<LetterSection>? = null,
) {
    /** [Scanning] while an import runs; the list still carries what's already imported, for a screen that keeps showing it. */
    enum class LoadingState { Loading, Scanning, Ready, Empty }

    val isSelecting: Boolean get() = selectedSongs.isNotEmpty()
}

@ViewModelKey(SongListViewModel::class)
@ContributesIntoMap(AppScope::class)
class SongListViewModel @Inject constructor(
    observeSongs: ObserveSongs,
    readSetting: ReadLibraryViewSetting,
    private val saveSetting: SaveLibraryViewSetting,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    mediaImportObserver: SongImportStateProvider,
) : ViewModel() {

    // We need to store Song.id instead of Song. Otherwise, when a song is
    // played/paused, it mutates so, when checking if it's contained in the
    // set of selected songs with Song.equals, it returns false.
    private val selectionState = SelectionState<Long>()

    private val _sortOrder = MutableStateFlow(readSetting(LibraryViewSetting.SongSort))

    private val sortedSongs = combine(observeSongs(SongQuery.All(sortOrder = _sortOrder.value)), _sortOrder) { songs, sortOrder ->
        val sorted = songs.sortedWith(sortOrder.comparator)
        IndexedList(sorted, sortOrder, songLetterIndex(sorted, sortOrder))
    }

    val uiState: StateFlow<SongListUiState> = combine(
        sortedSongs,
        mediaImportObserver.songImportState,
        selectionState.selectedItems,
    ) { sorted, songImportState, selectedSongIds ->
        val sortedSongs = sorted.items
        val selectedSongs = sortedSongs.filter { it.id in selectedSongIds }.toSet()
        SongListUiState(
            songs = sortedSongs,
            selectedSongs = selectedSongs,
            sortOrder = sorted.sortOrder,
            letterIndex = sorted.letterIndex,
            loadingState = when {
                songImportState is SongImportState.ImportProgress -> SongListUiState.LoadingState.Scanning
                sortedSongs.isEmpty() -> SongListUiState.LoadingState.Empty
                else -> SongListUiState.LoadingState.Ready
            },
            scanProgress = (songImportState as? SongImportState.ImportProgress)?.progress,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SongListUiState(),
    )

    /** A tap while selecting; outside selection mode the screen plays the song through its MediaActionsHost. */
    fun onSongClick(song: Song) {
        selectionState.toggle(song.id)
    }

    fun onSongLongClick(song: Song) {
        selectionState.toggle(song.id)
    }

    fun setSortOrder(sortOrder: SongSortOrder) {
        if (_sortOrder.value == sortOrder) {
            return
        }

        viewModelScope.launch {
            withContext(ioDispatcher) {
                saveSetting(LibraryViewSetting.SongSort, sortOrder)
                _sortOrder.value = sortOrder
            }
        }
    }

    fun clearSelection() {
        selectionState.clear()
    }
}
