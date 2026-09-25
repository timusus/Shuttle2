package com.simplecityapps.shuttle.ui.screens.library.albums.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.mediaprovider.repository.albums.AlbumQuery
import com.simplecityapps.mediaprovider.repository.albums.AlbumRepository
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistQuery
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import com.simplecityapps.shuttle.ui.actions.AddToPlaylist
import com.simplecityapps.shuttle.ui.actions.DeleteSongs
import com.simplecityapps.shuttle.ui.actions.EnqueueSongs
import com.simplecityapps.shuttle.ui.actions.ExcludeSongs
import com.simplecityapps.shuttle.ui.actions.MediaSelection
import com.simplecityapps.shuttle.ui.actions.PlaySongs
import com.simplecityapps.shuttle.ui.actions.ShuffleSongs
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData
import com.simplecityapps.shuttle.ui.screens.playlistmenu.toMediaSelection
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AlbumDetailUiState(
    val album: Album? = null,
    val songs: List<Song> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val currentSong: Song? = null,
    val loadingState: LoadingState = LoadingState.Loading,
) {
    enum class LoadingState { Loading, Ready, Empty }
}

sealed interface AlbumDetailUiEvent {
    data class AddedToQueue(val songCount: Int) : AlbumDetailUiEvent
    data class PlaybackFailed(val errorMessage: String?) : AlbumDetailUiEvent
    data class EditTags(val songs: List<Song>) : AlbumDetailUiEvent
    data class AddedToPlaylist(val playlist: Playlist, val playlistData: PlaylistData) : AlbumDetailUiEvent
    data class PlaylistDuplicatesFound(
        val playlist: Playlist,
        val playlistData: PlaylistData,
        val deduplicatedSongs: PlaylistData.Songs,
        val duplicates: List<Song>,
    ) : AlbumDetailUiEvent
    data class PlaylistAddFailed(val message: String?) : AlbumDetailUiEvent
    data object DeleteFailed : AlbumDetailUiEvent
}

@HiltViewModel
class AlbumDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val songRepository: SongRepository,
    private val albumRepository: AlbumRepository,
    private val queueManager: QueueOperations,
    private val playSongs: PlaySongs,
    private val enqueueSongs: EnqueueSongs,
    private val excludeSongs: ExcludeSongs,
    private val deleteSongs: DeleteSongs,
    private val shuffleSongs: ShuffleSongs,
    private val addToPlaylistUseCase: AddToPlaylist,
    private val playlistRepository: PlaylistRepository,
) : ViewModel() {

    val album: Album = AlbumDetailFragmentArgs.fromSavedStateHandle(savedStateHandle).album

    private val currentSong: Flow<Song?> = queueManager.queueStateFlow
        .map { queueState -> queueState.currentItem?.song }
        .distinctUntilChanged()

    val uiState: StateFlow<AlbumDetailUiState> = combine(
        songRepository
            .getSongs(SongQuery.AlbumGroupKey(key = album.groupKey))
            .filterNotNull(),
        albumRepository.getAlbums(AlbumQuery.AlbumGroupKey(album.groupKey)),
        currentSong,
        playlistRepository.getPlaylists(PlaylistQuery.All(mediaProviderType = null)),
    ) { songs, albums, currentSong, playlists ->
        val latestAlbum = albums.firstOrNull() ?: album
        AlbumDetailUiState(
            album = latestAlbum,
            songs = songs,
            playlists = playlists,
            currentSong = currentSong,
            loadingState = if (songs.isEmpty()) {
                AlbumDetailUiState.LoadingState.Empty
            } else {
                AlbumDetailUiState.LoadingState.Ready
            },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AlbumDetailUiState(album = album),
    )

    private val _events = MutableSharedFlow<AlbumDetailUiEvent>()
    val events: SharedFlow<AlbumDetailUiEvent> = _events.asSharedFlow()

    fun onSongClick(song: Song) {
        // Snapshot synchronously, at click time -- see SongListViewModel.play()'s comment for why
        // reading uiState.value inside the launched coroutine can race a still-settling uiState.
        val songs = uiState.value.songs
        val position = songs.indexOf(song)
        viewModelScope.launch {
            val result = playSongs(songs, position = position)
            if (result is PlaySongs.Result.Failure) {
                _events.emit(AlbumDetailUiEvent.PlaybackFailed(result.message))
            }
        }
    }

    fun onShuffle() {
        val songs = uiState.value.songs
        if (songs.isEmpty()) return
        viewModelScope.launch {
            val result = shuffleSongs(songs)
            if (result is ShuffleSongs.Result.Failure) {
                _events.emit(AlbumDetailUiEvent.PlaybackFailed(result.message))
            }
        }
    }

    fun onAddToQueue(song: Song) {
        viewModelScope.launch {
            enqueueSongs(MediaSelection.Songs(song), EnqueueSongs.Position.End)
            _events.emit(AlbumDetailUiEvent.AddedToQueue(1))
        }
    }

    fun onAddAlbumToQueue() {
        viewModelScope.launch {
            val songs = uiState.value.songs
            enqueueSongs(MediaSelection.Songs(songs), EnqueueSongs.Position.End)
            _events.emit(AlbumDetailUiEvent.AddedToQueue(songs.size))
        }
    }

    fun onPlayNext(song: Song) {
        viewModelScope.launch {
            enqueueSongs(MediaSelection.Songs(song), EnqueueSongs.Position.Next)
            _events.emit(AlbumDetailUiEvent.AddedToQueue(1))
        }
    }

    fun onPlayAlbumNext() {
        viewModelScope.launch {
            val songs = uiState.value.songs
            enqueueSongs(MediaSelection.Songs(songs), EnqueueSongs.Position.Next)
            _events.emit(AlbumDetailUiEvent.AddedToQueue(songs.size))
        }
    }

    fun onExclude(song: Song) {
        viewModelScope.launch {
            excludeSongs(MediaSelection.Songs(song))
        }
    }

    fun onEditTags(song: Song) {
        viewModelScope.launch {
            _events.emit(AlbumDetailUiEvent.EditTags(listOf(song)))
        }
    }

    fun onEditAlbumTags() {
        viewModelScope.launch {
            _events.emit(AlbumDetailUiEvent.EditTags(uiState.value.songs))
        }
    }

    fun onDelete(song: Song) {
        viewModelScope.launch {
            if (deleteSongs(MediaSelection.Songs(song)).failed.isNotEmpty()) _events.emit(AlbumDetailUiEvent.DeleteFailed)
        }
    }

    fun addToPlaylist(playlist: Playlist, playlistData: PlaylistData, ignoreDuplicates: Boolean = false) {
        viewModelScope.launch {
            when (val result = addToPlaylistUseCase(playlist, playlistData.toMediaSelection(), ignoreDuplicates)) {
                is AddToPlaylist.Result.Success ->
                    _events.emit(AlbumDetailUiEvent.AddedToPlaylist(result.playlist, playlistData))

                is AddToPlaylist.Result.DuplicatesFound ->
                    _events.emit(
                        AlbumDetailUiEvent.PlaylistDuplicatesFound(
                            result.playlist,
                            playlistData,
                            PlaylistData.Songs(result.nonDuplicates),
                            result.duplicates,
                        )
                    )

                is AddToPlaylist.Result.Failure ->
                    _events.emit(AlbumDetailUiEvent.PlaylistAddFailed(result.message))
            }
        }
    }
}
