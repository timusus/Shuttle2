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
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.playback.PlaybackOperations
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import com.simplecityapps.shuttle.ui.common.SelectionState
import com.simplecityapps.shuttle.ui.common.playback.PlaySongs
import com.simplecityapps.shuttle.ui.common.playlist.AddToPlaylist
import com.simplecityapps.shuttle.ui.screens.library.ViewMode
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData
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
    private val songRepository: SongRepository,
    private val playbackManager: PlaybackOperations,
    private val playSongs: PlaySongs,
    private val addToPlaylistUseCase: AddToPlaylist,
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
            val songs = getSongsForArtist(albumArtist)
            val result = playSongs(songs)
            if (result is PlaySongs.Result.Failure) {
                _events.emit(AlbumArtistListUiEvent.PlaybackFailed(result.message))
            }
        }
    }

    fun onAddToQueue(albumArtist: AlbumArtist) {
        viewModelScope.launch {
            val songs = getSongsForArtist(albumArtist)
            playbackManager.addToQueue(songs)
            _events.emit(AlbumArtistListUiEvent.AddedToQueue(1))
        }
    }

    fun onAddSelectedToQueue() {
        viewModelScope.launch {
            val selected = selectionState.selectedItems.value.toList()
            val songs = getSongsForArtists(selected)
            playbackManager.addToQueue(songs)
            _events.emit(AlbumArtistListUiEvent.AddedToQueue(selected.size))
            selectionState.clear()
        }
    }

    fun onPlayNext(albumArtist: AlbumArtist) {
        viewModelScope.launch {
            val songs = getSongsForArtist(albumArtist)
            playbackManager.playNext(songs)
            _events.emit(AlbumArtistListUiEvent.AddedToQueue(1))
        }
    }

    fun onExclude(albumArtist: AlbumArtist) {
        viewModelScope.launch {
            val songs = getSongsForArtist(albumArtist)
            songRepository.setExcluded(songs, true)
        }
    }

    fun onEditTags(albumArtist: AlbumArtist) {
        viewModelScope.launch {
            val songs = getSongsForArtist(albumArtist)
            _events.emit(AlbumArtistListUiEvent.EditTags(songs))
        }
    }

    fun onEditTagsSelected() {
        viewModelScope.launch {
            val selected = selectionState.selectedItems.value.toList()
            val songs = getSongsForArtists(selected)
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
            when (val result = addToPlaylistUseCase(playlist, playlistData, ignoreDuplicates)) {
                is AddToPlaylist.Result.Success ->
                    _events.emit(AlbumArtistListUiEvent.AddedToPlaylist(result.playlist, result.playlistData))
                is AddToPlaylist.Result.DuplicatesFound ->
                    _events.emit(
                        AlbumArtistListUiEvent.PlaylistDuplicatesFound(
                            result.playlist,
                            result.playlistData,
                            result.deduplicatedSongs,
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
            val songs = addToPlaylistUseCase.resolveSongs(playlistData)
            playlistRepository.createPlaylist(name, MediaProviderType.Shuttle, songs, null)
        }
    }

    private suspend fun getSongsForArtist(albumArtist: AlbumArtist): List<Song> = songRepository
        .getSongs(SongQuery.ArtistGroupKeys(listOf(SongQuery.ArtistGroupKey(key = albumArtist.groupKey))))
        .firstOrNull()
        .orEmpty()

    private suspend fun getSongsForArtists(albumArtists: List<AlbumArtist>): List<Song> = songRepository
        .getSongs(SongQuery.ArtistGroupKeys(albumArtists.map { SongQuery.ArtistGroupKey(key = it.groupKey) }))
        .firstOrNull()
        .orEmpty()
}
