package com.simplecityapps.shuttle.ui.screens.library.albums

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.mediaprovider.SongImportState
import com.simplecityapps.mediaprovider.SongImportStateProvider
import com.simplecityapps.mediaprovider.repository.albums.comparator
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.sorting.AlbumSortOrder
import com.simplecityapps.shuttle.ui.actions.ObserveAlbums
import com.simplecityapps.shuttle.ui.actions.ObserveSongs
import com.simplecityapps.shuttle.ui.actions.ShuffleAlbums
import com.simplecityapps.shuttle.ui.common.SelectionState
import com.simplecityapps.shuttle.ui.screens.library.SortPreferences
import com.simplecityapps.shuttle.ui.screens.library.ViewMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.random.Random
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AlbumListViewModel @Inject constructor(
    observeAlbums: ObserveAlbums,
    private val observeSongs: ObserveSongs,
    private val shuffleAlbums: ShuffleAlbums,
    private val sortPreferenceManager: SortPreferences,
    private val viewModePreferenceManager: AlbumListPreferences,
    mediaImportObserver: SongImportStateProvider,
    private val random: Random,
) : ViewModel() {

    private val selectionState = SelectionState<Album>()

    private val _sortOrder = MutableStateFlow(sortPreferenceManager.sortOrderAlbumList)
    private val _viewMode = MutableStateFlow(viewModePreferenceManager.albumListViewMode)

    // Not persisted: a fresh app process starts with a new shuffle even if Random remains the
    // selected sort order. Only reassigned when the user (re)selects Random, so library
    // re-emissions (scans, play counts) while on this screen don't reshuffle the list.
    private val _randomSeed = MutableStateFlow(random.nextLong())

    val uiState: StateFlow<AlbumListUiState> = combine(
        observeAlbums(),
        mediaImportObserver.songImportState,
        selectionState.selectedItems,
        _sortOrder,
        combine(_viewMode, _randomSeed) { viewMode, randomSeed -> viewMode to randomSeed },
    ) { albums, songImportState, selectedAlbums, sortOrder, (viewMode, randomSeed) ->
        if (songImportState is SongImportState.ImportProgress) {
            AlbumListUiState(
                loadingState = AlbumListUiState.LoadingState.Scanning,
                scanProgress = songImportState.progress,
                sortOrder = sortOrder,
                viewMode = viewMode,
                selectedAlbums = selectedAlbums,
            )
        } else {
            val sortedAlbums = albums.sortedWith(sortOrder.comparator(randomSeed))
            AlbumListUiState(
                albums = sortedAlbums,
                selectedAlbums = selectedAlbums,
                viewMode = viewMode,
                sortOrder = sortOrder,
                loadingState = if (sortedAlbums.isEmpty()) {
                    AlbumListUiState.LoadingState.Empty
                } else {
                    AlbumListUiState.LoadingState.Ready
                },
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AlbumListUiState(),
    )

    private val _events = MutableSharedFlow<AlbumListUiEvent>()
    val events: SharedFlow<AlbumListUiEvent> = _events.asSharedFlow()

    fun onAlbumClick(album: Album) {
        selectionState.toggle(album)
    }

    fun onAlbumLongClick(album: Album) {
        selectionState.toggle(album)
    }

    fun onShuffle() {
        viewModelScope.launch {
            // ShuffleAlbums groups songs by albumGroupKey without reordering within a group, so
            // each album's songs must already be in track order before it shuffles the album order.
            val allSongs = observeSongs().firstOrNull().orEmpty()
                .sortedWith(compareBy({ it.albumGroupKey.key }, { it.albumGroupKey.albumArtistGroupKey?.key }, { it.disc }, { it.track }))
            val result = shuffleAlbums(allSongs)
            if (result is ShuffleAlbums.Result.Failure) {
                _events.emit(AlbumListUiEvent.PlaybackFailed(result.message))
            }
        }
    }

    fun setSortOrder(sortOrder: AlbumSortOrder) {
        sortPreferenceManager.sortOrderAlbumList = sortOrder
        if (sortOrder == AlbumSortOrder.Random) {
            _randomSeed.value = random.nextLong()
        }
        _sortOrder.value = sortOrder
    }

    fun setViewMode(mode: ViewMode) {
        viewModePreferenceManager.albumListViewMode = mode
        _viewMode.value = mode
    }

    fun clearSelection() {
        selectionState.clear()
    }
}
