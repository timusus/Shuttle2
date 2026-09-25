package com.simplecityapps.shuttle.ui.screens.library.albumartists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.mediaprovider.SongImportState
import com.simplecityapps.mediaprovider.SongImportStateProvider
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.ui.actions.ObserveAlbumArtists
import com.simplecityapps.shuttle.ui.common.SelectionState
import com.simplecityapps.shuttle.ui.screens.library.ViewMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
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
    enum class LoadingState { Loading, Scanning, Ready, Empty }

    val isSelecting: Boolean get() = selectedArtists.isNotEmpty()
}

@HiltViewModel
class AlbumArtistListViewModel @Inject constructor(
    observeAlbumArtists: ObserveAlbumArtists,
    private val preferenceManager: ArtistListPreferences,
    mediaImportObserver: SongImportStateProvider,
) : ViewModel() {

    private val selectionState = SelectionState<AlbumArtist>()

    private val _viewMode = MutableStateFlow(preferenceManager.artistListViewMode)

    val uiState: StateFlow<AlbumArtistListUiState> = combine(
        observeAlbumArtists(),
        mediaImportObserver.songImportState,
        selectionState.selectedItems,
        _viewMode,
    ) { albumArtists, songImportState, selectedArtists, viewMode ->
        if (songImportState is SongImportState.ImportProgress) {
            AlbumArtistListUiState(
                loadingState = AlbumArtistListUiState.LoadingState.Scanning,
                scanProgress = songImportState.progress,
                viewMode = viewMode,
                selectedArtists = selectedArtists,
            )
        } else {
            AlbumArtistListUiState(
                albumArtists = albumArtists,
                selectedArtists = selectedArtists,
                viewMode = viewMode,
                loadingState = if (albumArtists.isEmpty()) {
                    AlbumArtistListUiState.LoadingState.Empty
                } else {
                    AlbumArtistListUiState.LoadingState.Ready
                },
            )
        }
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
        preferenceManager.artistListViewMode = mode
        _viewMode.value = mode
    }

    fun clearSelection() {
        selectionState.clear()
    }
}
