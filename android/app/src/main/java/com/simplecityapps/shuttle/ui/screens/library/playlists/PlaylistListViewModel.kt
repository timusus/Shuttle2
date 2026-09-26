package com.simplecityapps.shuttle.ui.screens.library.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.mediaprovider.SongImportState
import com.simplecityapps.mediaprovider.SongImportStateProvider
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistSortOrder
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.ui.actions.ClearPlaylist
import com.simplecityapps.shuttle.ui.actions.CreatePlaylist
import com.simplecityapps.shuttle.ui.actions.DeletePlaylist
import com.simplecityapps.shuttle.ui.actions.ObservePlaylistCovers
import com.simplecityapps.shuttle.ui.actions.ObservePlaylists
import com.simplecityapps.shuttle.ui.actions.RenamePlaylist
import com.simplecityapps.shuttle.ui.screens.library.LibraryViewSetting
import com.simplecityapps.shuttle.ui.screens.library.ReadLibraryViewSetting
import com.simplecityapps.shuttle.ui.screens.library.SaveLibraryViewSetting
import com.simplecityapps.shuttle.ui.screens.library.SmartPlaylistId
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlaylistListViewModel @Inject constructor(
    observePlaylists: ObservePlaylists,
    private val createPlaylist: CreatePlaylist,
    private val renamePlaylist: RenamePlaylist,
    private val clearPlaylist: ClearPlaylist,
    private val deletePlaylist: DeletePlaylist,
    readSetting: ReadLibraryViewSetting,
    private val saveSetting: SaveLibraryViewSetting,
    mediaImportObserver: SongImportStateProvider,
    observePlaylistCovers: ObservePlaylistCovers,
) : ViewModel() {

    private val _sortOrder = MutableStateFlow(readSetting(LibraryViewSetting.PlaylistSort))

    /** The playlists with their covers; the list shows first and the covers follow as they load. */
    private val playlistsWithCovers = observePlaylists().flatMapLatest { playlists ->
        observePlaylistCovers(playlists).map { covers -> playlists to covers }.onStart { emit(playlists to emptyMap()) }
    }

    val uiState: StateFlow<PlaylistListUiState> = combine(
        playlistsWithCovers,
        mediaImportObserver.songImportState,
        _sortOrder,
    ) { (playlists, covers), songImportState, sortOrder ->
        if (songImportState is SongImportState.ImportProgress) {
            PlaylistListUiState(
                loadingState = PlaylistListUiState.LoadingState.Scanning,
                scanProgress = songImportState.progress,
                sortOrder = sortOrder,
            )
        } else {
            PlaylistListUiState(
                playlists = playlists.sortedWith(sortOrder.comparator),
                smartPlaylists = SmartPlaylistId.entries.map { it.smartPlaylist },
                covers = covers,
                sortOrder = sortOrder,
                loadingState = PlaylistListUiState.LoadingState.Ready,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PlaylistListUiState(),
    )

    fun setSortOrder(sortOrder: PlaylistSortOrder) {
        saveSetting(LibraryViewSetting.PlaylistSort, sortOrder)
        _sortOrder.value = sortOrder
    }

    fun onDelete(playlist: Playlist) {
        viewModelScope.launch {
            deletePlaylist(playlist)
        }
    }

    fun onClear(playlist: Playlist) {
        viewModelScope.launch {
            clearPlaylist(playlist)
        }
    }

    fun onRename(playlist: Playlist, name: String) {
        viewModelScope.launch {
            renamePlaylist(playlist, name)
        }
    }

    /** Creates an empty local playlist (the Playlists tab's "New playlist"). */
    fun onCreatePlaylist(name: String) {
        viewModelScope.launch {
            createPlaylist(name, selection = null)
        }
    }
}
