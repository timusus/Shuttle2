package com.simplecityapps.shuttle.ui.screens.library.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.mediaprovider.SongImportState
import com.simplecityapps.mediaprovider.SongImportStateProvider
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistQuery
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistSortOrder
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.ui.actions.EnqueueSongs
import com.simplecityapps.shuttle.ui.actions.MediaSelection
import com.simplecityapps.shuttle.ui.actions.PlaySongs
import com.simplecityapps.shuttle.ui.actions.ResolveSongs
import com.simplecityapps.shuttle.ui.screens.library.SmartPlaylistId
import com.simplecityapps.shuttle.ui.screens.library.SortPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class PlaylistListViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val playSongs: PlaySongs,
    private val resolveSongs: ResolveSongs,
    private val enqueueSongs: EnqueueSongs,
    private val sortPreferenceManager: SortPreferences,
    mediaImportObserver: SongImportStateProvider,
) : ViewModel() {

    private val _sortOrder = MutableStateFlow(sortPreferenceManager.sortOrderPlaylistList)

    val uiState: StateFlow<PlaylistListUiState> = combine(
        playlistRepository.getPlaylists(PlaylistQuery.All(mediaProviderType = null)),
        mediaImportObserver.songImportState,
        _sortOrder,
    ) { playlists, songImportState, sortOrder ->
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
                sortOrder = sortOrder,
                loadingState = PlaylistListUiState.LoadingState.Ready,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PlaylistListUiState(),
    )

    private val _events = MutableSharedFlow<PlaylistListUiEvent>()
    val events: SharedFlow<PlaylistListUiEvent> = _events.asSharedFlow()

    fun setSortOrder(sortOrder: PlaylistSortOrder) {
        sortPreferenceManager.sortOrderPlaylistList = sortOrder
        _sortOrder.value = sortOrder
    }

    fun onPlay(playlist: Playlist) {
        viewModelScope.launch {
            val songs = resolveSongs(MediaSelection.Playlists(playlist))
            val result = playSongs(songs)
            if (result is PlaySongs.Result.Failure) {
                _events.emit(PlaylistListUiEvent.PlaybackFailed(result.message))
            }
        }
    }

    fun onAddToQueue(playlist: Playlist) {
        viewModelScope.launch {
            enqueueSongs(MediaSelection.Playlists(playlist), EnqueueSongs.Position.End)
            _events.emit(PlaylistListUiEvent.AddedToQueue(playlist.name))
        }
    }

    fun onPlayNext(playlist: Playlist) {
        viewModelScope.launch {
            enqueueSongs(MediaSelection.Playlists(playlist), EnqueueSongs.Position.Next)
            _events.emit(PlaylistListUiEvent.AddedToQueue(playlist.name))
        }
    }

    fun onDelete(playlist: Playlist) {
        viewModelScope.launch {
            playlistRepository.deletePlaylist(playlist)
        }
    }

    fun onClear(playlist: Playlist) {
        viewModelScope.launch {
            playlistRepository.clearPlaylist(playlist)
        }
    }

    fun onRename(playlist: Playlist, name: String) {
        viewModelScope.launch {
            playlistRepository.renamePlaylist(playlist, name)
        }
    }

    /** Creates an empty local playlist (the Playlists tab's "New playlist"). */
    fun onCreatePlaylist(name: String) {
        viewModelScope.launch {
            playlistRepository.createPlaylist(name, MediaProviderType.Shuttle, songs = null, externalId = null)
        }
    }
}
