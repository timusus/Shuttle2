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
import com.simplecityapps.shuttle.ui.screens.library.LibraryViewSetting
import com.simplecityapps.shuttle.ui.screens.library.ReadLibraryViewSetting
import com.simplecityapps.shuttle.ui.screens.library.SaveLibraryViewSetting
import com.simplecityapps.shuttle.ui.screens.library.ViewMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.random.Random
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AlbumListViewModel @Inject constructor(
    observeAlbums: ObserveAlbums,
    private val observeSongs: ObserveSongs,
    private val shuffleAlbums: ShuffleAlbums,
    readSetting: ReadLibraryViewSetting,
    private val saveSetting: SaveLibraryViewSetting,
    mediaImportObserver: SongImportStateProvider,
    private val random: Random,
) : ViewModel() {

    private val selectionState = SelectionState<Album>()

    private val _sortOrder = MutableStateFlow(readSetting(LibraryViewSetting.AlbumSort))
    private val _viewMode = MutableStateFlow(readSetting(LibraryViewSetting.AlbumViewMode))

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
            // A failure isn't shown: nothing ever collected the event this used to emit.
            shuffleAlbums(allSongs)
        }
    }

    fun setSortOrder(sortOrder: AlbumSortOrder) {
        saveSetting(LibraryViewSetting.AlbumSort, sortOrder)
        if (sortOrder == AlbumSortOrder.Random) {
            _randomSeed.value = random.nextLong()
        }
        _sortOrder.value = sortOrder
    }

    fun setViewMode(mode: ViewMode) {
        saveSetting(LibraryViewSetting.AlbumViewMode, mode)
        _viewMode.value = mode
    }

    fun clearSelection() {
        selectionState.clear()
    }
}
