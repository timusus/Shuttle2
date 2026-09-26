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
import com.simplecityapps.shuttle.ui.actions.GetFavoritesPlaylist
import com.simplecityapps.shuttle.ui.actions.ObservePlaylists
import com.simplecityapps.shuttle.ui.actions.RenamePlaylist
import com.simplecityapps.shuttle.ui.screens.library.SmartPlaylistId
import com.simplecityapps.shuttle.ui.screens.library.SortPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class PlaylistListViewModel @Inject constructor(
    observePlaylists: ObservePlaylists,
    private val createPlaylist: CreatePlaylist,
    private val renamePlaylist: RenamePlaylist,
    private val clearPlaylist: ClearPlaylist,
    private val deletePlaylist: DeletePlaylist,
    getFavoritesPlaylist: GetFavoritesPlaylist,
    private val sortPreferenceManager: SortPreferences,
    mediaImportObserver: SongImportStateProvider,
) : ViewModel() {

    private val _sortOrder = MutableStateFlow(sortPreferenceManager.sortOrderPlaylistList)
    private val favoritesPlaylist = flow { emit(getFavoritesPlaylist()) }

    val uiState: StateFlow<PlaylistListUiState> = combine(
        observePlaylists(),
        mediaImportObserver.songImportState,
        _sortOrder,
        favoritesPlaylist,
    ) { playlists, songImportState, sortOrder, favorites ->
        if (songImportState is SongImportState.ImportProgress) {
            PlaylistListUiState(
                loadingState = PlaylistListUiState.LoadingState.Scanning,
                scanProgress = songImportState.progress,
                sortOrder = sortOrder,
            )
        } else {
            PlaylistListUiState(
                playlists = playlists.filterNot { it.id == favorites.id }.sortedWith(sortOrder.comparator),
                smartPlaylists = SmartPlaylistId.entries.map { it.smartPlaylist },
                favoritesPlaylist = favorites,
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
        sortPreferenceManager.sortOrderPlaylistList = sortOrder
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
