package com.simplecityapps.shuttle.ui.screens.library.albumartists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.mediaprovider.SongImportState
import com.simplecityapps.mediaprovider.SongImportStateProvider
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.ui.actions.ObserveAlbumArtists
import com.simplecityapps.shuttle.ui.common.SelectionState
import com.simplecityapps.shuttle.ui.screens.library.LibraryViewSetting
import com.simplecityapps.shuttle.ui.screens.library.ReadLibraryViewSetting
import com.simplecityapps.shuttle.ui.screens.library.SaveLibraryViewSetting
import com.simplecityapps.shuttle.ui.screens.library.ViewMode
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class AlbumArtistListUiState(
    val albumArtists: List<AlbumArtist> = emptyList(),
    val selectedArtists: Set<AlbumArtist> = emptySet(),
    val viewMode: ViewMode = ViewMode.List,
    val loadingState: LoadingState = LoadingState.Loading,
    val scanProgress: Progress? = null,
) {
    /** [Scanning] while an import runs; the list still carries what's already imported, for a screen that keeps showing it. */
    enum class LoadingState { Loading, Scanning, Ready, Empty }

    val isSelecting: Boolean get() = selectedArtists.isNotEmpty()
}

@ViewModelKey(AlbumArtistListViewModel::class)
@ContributesIntoMap(AppScope::class)
class AlbumArtistListViewModel @Inject constructor(
    observeAlbumArtists: ObserveAlbumArtists,
    readSetting: ReadLibraryViewSetting,
    private val saveSetting: SaveLibraryViewSetting,
    mediaImportObserver: SongImportStateProvider,
) : ViewModel() {

    private val selectionState = SelectionState<AlbumArtist>()

    private val _viewMode = MutableStateFlow(readSetting(LibraryViewSetting.ArtistViewMode))

    val uiState: StateFlow<AlbumArtistListUiState> = combine(
        observeAlbumArtists(),
        mediaImportObserver.songImportState,
        selectionState.selectedItems,
        _viewMode,
    ) { albumArtists, songImportState, selectedArtists, viewMode ->
        AlbumArtistListUiState(
            albumArtists = albumArtists,
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

    fun setViewMode(mode: ViewMode) {
        saveSetting(LibraryViewSetting.ArtistViewMode, mode)
        _viewMode.value = mode
    }

    fun clearSelection() {
        selectionState.clear()
    }
}
