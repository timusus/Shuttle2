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
import com.simplecityapps.shuttle.sorting.SongSortOrder
import com.simplecityapps.shuttle.ui.actions.ObserveSongs
import com.simplecityapps.shuttle.ui.common.SelectionState
import com.simplecityapps.shuttle.ui.screens.library.LibraryViewSetting
import com.simplecityapps.shuttle.ui.screens.library.ReadLibraryViewSetting
import com.simplecityapps.shuttle.ui.screens.library.SaveLibraryViewSetting
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
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
) {
    enum class LoadingState { Loading, Scanning, Ready, Empty }

    val isSelecting: Boolean get() = selectedSongs.isNotEmpty()
}

@HiltViewModel
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

    val uiState: StateFlow<SongListUiState> = combine(
        observeSongs(SongQuery.All(sortOrder = _sortOrder.value)),
        mediaImportObserver.songImportState,
        selectionState.selectedItems,
        _sortOrder,
    ) { songs, songImportState, selectedSongIds, sortOrder ->
        val selectedSongs = songs.filter { it.id in selectedSongIds }.toSet()

        if (songImportState is SongImportState.ImportProgress) {
            SongListUiState(
                loadingState = SongListUiState.LoadingState.Scanning,
                scanProgress = songImportState.progress,
                sortOrder = sortOrder,
                selectedSongs = selectedSongs,
            )
        } else {
            val sortedSongs = songs.sortedWith(sortOrder.comparator)
            SongListUiState(
                songs = sortedSongs,
                selectedSongs = selectedSongs,
                sortOrder = sortOrder,
                loadingState = if (sortedSongs.isEmpty()) {
                    SongListUiState.LoadingState.Empty
                } else {
                    SongListUiState.LoadingState.Ready
                },
            )
        }
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
