package com.simplecityapps.shuttle.ui.screens.library.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.mediaprovider.SongImportState
import com.simplecityapps.mediaprovider.SongImportStateProvider
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistQuery
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistSortOrder
import com.simplecityapps.playback.PlaybackOperations
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.ui.common.playback.PlaySongs
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
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class PlaylistListViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val playbackManager: PlaybackOperations,
    private val playSongs: PlaySongs,
    private val sortPreferenceManager: SortPreferences,
    mediaImportObserver: SongImportStateProvider,
) : ViewModel() {

    private val _sortOrder = MutableStateFlow(sortPreferenceManager.sortOrderPlaylistList)

    val uiState: StateFlow<PlaylistListUiState> = combine(
        playlistRepository.getPlaylists(PlaylistQuery.All(mediaProviderType = null)),
        playlistRepository.getSmartPlaylists(),
        mediaImportObserver.songImportState,
        _sortOrder,
    ) { playlists, smartPlaylists, songImportState, sortOrder ->
        if (songImportState is SongImportState.ImportProgress) {
            PlaylistListUiState(
                loadingState = PlaylistListUiState.LoadingState.Scanning,
                scanProgress = songImportState.progress,
                sortOrder = sortOrder,
            )
        } else {
            PlaylistListUiState(
                playlists = playlists.sortedWith(sortOrder.comparator),
                smartPlaylists = smartPlaylists,
                sortOrder = sortOrder,
                loadingState = if (playlists.isEmpty() && smartPlaylists.isEmpty()) {
                    PlaylistListUiState.LoadingState.Empty
                } else {
                    PlaylistListUiState.LoadingState.Ready
                },
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
            val songs = playlistRepository.getSongsForPlaylist(playlist).firstOrNull().orEmpty().map { it.song }
            val result = playSongs(songs)
            if (result is PlaySongs.Result.Failure) {
                _events.emit(PlaylistListUiEvent.PlaybackFailed(result.message))
            }
        }
    }

    fun onAddToQueue(playlist: Playlist) {
        viewModelScope.launch {
            val songs = playlistRepository.getSongsForPlaylist(playlist).firstOrNull().orEmpty().map { it.song }
            playbackManager.addToQueue(songs)
            _events.emit(PlaylistListUiEvent.AddedToQueue(playlist.name))
        }
    }

    fun onPlayNext(playlist: Playlist) {
        viewModelScope.launch {
            val songs = playlistRepository.getSongsForPlaylist(playlist).firstOrNull().orEmpty().map { it.song }
            playbackManager.playNext(songs)
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
}
