package com.simplecityapps.shuttle.ui.screens.library.albumartists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.mediaprovider.SongImportState
import com.simplecityapps.mediaprovider.SongImportStateProvider
import com.simplecityapps.mediaprovider.repository.artists.AlbumArtistQuery
import com.simplecityapps.mediaprovider.repository.artists.AlbumArtistRepository
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistQuery
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.ui.actions.AddToPlaylist
import com.simplecityapps.shuttle.ui.actions.CreatePlaylist
import com.simplecityapps.shuttle.ui.actions.EnqueueSongs
import com.simplecityapps.shuttle.ui.actions.ExcludeSongs
import com.simplecityapps.shuttle.ui.actions.MediaSelection
import com.simplecityapps.shuttle.ui.actions.PlaySongs
import com.simplecityapps.shuttle.ui.actions.ResolveSongs
import com.simplecityapps.shuttle.ui.common.SelectionState
import com.simplecityapps.shuttle.ui.screens.library.ViewMode
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData
import com.simplecityapps.shuttle.ui.screens.playlistmenu.toMediaSelection
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

data class AlbumArtistListUiState(
    val albumArtists: List<AlbumArtist> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val selectedArtists: Set<AlbumArtist> = emptySet(),
    val viewMode: ViewMode = ViewMode.List,
    val loadingState: LoadingState = LoadingState.Loading,
    val scanProgress: Progress? = null,
) {
    enum class LoadingState { Loading, Scanning, Ready, Empty }

    val isSelecting: Boolean get() = selectedArtists.isNotEmpty()
}

sealed interface AlbumArtistListUiEvent {
    data class AddedToQueue(val artistCount: Int) : AlbumArtistListUiEvent
    data class PlaybackFailed(val errorMessage: String?) : AlbumArtistListUiEvent
    data class EditTags(val songs: List<Song>) : AlbumArtistListUiEvent
    data class AddedToPlaylist(val playlist: Playlist, val playlistData: PlaylistData) : AlbumArtistListUiEvent
    data class PlaylistDuplicatesFound(
        val playlist: Playlist,
        val playlistData: PlaylistData,
        val deduplicatedSongs: PlaylistData.Songs,
        val duplicates: List<Song>,
    ) : AlbumArtistListUiEvent
    data class PlaylistAddFailed(val message: String?) : AlbumArtistListUiEvent
}

@HiltViewModel
class AlbumArtistListViewModel @Inject constructor(
    private val albumArtistRepository: AlbumArtistRepository,
    private val playSongs: PlaySongs,
    private val addToPlaylistUseCase: AddToPlaylist,
    private val createPlaylistUseCase: CreatePlaylist,
    private val resolveSongs: ResolveSongs,
    private val enqueueSongs: EnqueueSongs,
    private val excludeSongs: ExcludeSongs,
    private val playlistRepository: PlaylistRepository,
    private val preferenceManager: ArtistListPreferences,
    mediaImportObserver: SongImportStateProvider,
) : ViewModel() {

    private val selectionState = SelectionState<AlbumArtist>()

    private val _viewMode = MutableStateFlow(preferenceManager.artistListViewMode)

    val uiState: StateFlow<AlbumArtistListUiState> = combine(
        albumArtistRepository.getAlbumArtists(AlbumArtistQuery.All()),
        mediaImportObserver.songImportState,
        selectionState.selectedItems,
        combine(_viewMode, playlistRepository.getPlaylists(PlaylistQuery.All(mediaProviderType = null))) { a, b -> a to b },
    ) { albumArtists, songImportState, selectedArtists, (viewMode, playlists) ->
        if (songImportState is SongImportState.ImportProgress) {
            AlbumArtistListUiState(
                loadingState = AlbumArtistListUiState.LoadingState.Scanning,
                scanProgress = songImportState.progress,
                viewMode = viewMode,
                selectedArtists = selectedArtists,
                playlists = playlists,
            )
        } else {
            AlbumArtistListUiState(
                albumArtists = albumArtists,
                selectedArtists = selectedArtists,
                viewMode = viewMode,
                playlists = playlists,
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

    private val _events = MutableSharedFlow<AlbumArtistListUiEvent>()
    val events: SharedFlow<AlbumArtistListUiEvent> = _events.asSharedFlow()

    fun onArtistClick(albumArtist: AlbumArtist) {
        selectionState.toggle(albumArtist)
    }

    fun onArtistLongClick(albumArtist: AlbumArtist) {
        selectionState.toggle(albumArtist)
    }

    fun onPlay(albumArtist: AlbumArtist) {
        viewModelScope.launch {
            val songs = resolveSongs(MediaSelection.AlbumArtists(albumArtist))
            val result = playSongs(songs)
            if (result is PlaySongs.Result.Failure) {
                _events.emit(AlbumArtistListUiEvent.PlaybackFailed(result.message))
            }
        }
    }

    fun onAddToQueue(albumArtist: AlbumArtist) {
        viewModelScope.launch {
            enqueueSongs(MediaSelection.AlbumArtists(albumArtist), EnqueueSongs.Position.End)
            _events.emit(AlbumArtistListUiEvent.AddedToQueue(1))
        }
    }

    fun onAddSelectedToQueue() {
        viewModelScope.launch {
            val selected = selectionState.selectedItems.value.toList()
            enqueueSongs(MediaSelection.AlbumArtists(selected), EnqueueSongs.Position.End)
            _events.emit(AlbumArtistListUiEvent.AddedToQueue(selected.size))
            selectionState.clear()
        }
    }

    fun onPlayNext(albumArtist: AlbumArtist) {
        viewModelScope.launch {
            enqueueSongs(MediaSelection.AlbumArtists(albumArtist), EnqueueSongs.Position.Next)
            _events.emit(AlbumArtistListUiEvent.AddedToQueue(1))
        }
    }

    fun onExclude(albumArtist: AlbumArtist) {
        viewModelScope.launch {
            excludeSongs(MediaSelection.AlbumArtists(albumArtist))
        }
    }

    fun onEditTags(albumArtist: AlbumArtist) {
        viewModelScope.launch {
            val songs = resolveSongs(MediaSelection.AlbumArtists(albumArtist))
            _events.emit(AlbumArtistListUiEvent.EditTags(songs))
        }
    }

    fun onEditTagsSelected() {
        viewModelScope.launch {
            val selected = selectionState.selectedItems.value.toList()
            val songs = resolveSongs(MediaSelection.AlbumArtists(selected))
            _events.emit(AlbumArtistListUiEvent.EditTags(songs))
            selectionState.clear()
        }
    }

    fun setViewMode(mode: ViewMode) {
        preferenceManager.artistListViewMode = mode
        _viewMode.value = mode
    }

    fun clearSelection() {
        selectionState.clear()
    }

    fun selectedArtists(): List<AlbumArtist> = selectionState.selectedItems.value.toList()

    fun addToPlaylist(playlist: Playlist, playlistData: PlaylistData, ignoreDuplicates: Boolean = false) {
        viewModelScope.launch {
            when (val result = addToPlaylistUseCase(playlist, playlistData.toMediaSelection(), ignoreDuplicates)) {
                is AddToPlaylist.Result.Success ->
                    _events.emit(AlbumArtistListUiEvent.AddedToPlaylist(result.playlist, playlistData))

                is AddToPlaylist.Result.DuplicatesFound ->
                    _events.emit(
                        AlbumArtistListUiEvent.PlaylistDuplicatesFound(
                            result.playlist,
                            playlistData,
                            PlaylistData.Songs(result.nonDuplicates),
                            result.duplicates
                        )
                    )

                is AddToPlaylist.Result.Failure ->
                    _events.emit(AlbumArtistListUiEvent.PlaylistAddFailed(result.message))
            }
        }
    }

    fun createPlaylist(name: String, playlistData: PlaylistData) {
        viewModelScope.launch {
            createPlaylistUseCase(name, playlistData.toMediaSelection())
        }
    }
}
