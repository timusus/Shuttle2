package com.simplecityapps.shuttle.ui.screens.library.albums.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.mediaprovider.repository.albums.AlbumQuery
import com.simplecityapps.mediaprovider.repository.albums.AlbumRepository
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistQuery
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.playback.PlaybackOperations
import com.simplecityapps.playback.queue.QueueChangeCallback
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.playback.queue.QueueWatcher
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import com.simplecityapps.shuttle.ui.common.playback.PlaySongs
import com.simplecityapps.shuttle.ui.common.playback.ShuffleSongs
import com.simplecityapps.shuttle.ui.common.playlist.AddToPlaylist
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
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
}

@HiltViewModel
class AlbumDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val songRepository: SongRepository,
    private val albumRepository: AlbumRepository,
    private val playbackManager: PlaybackOperations,
    private val queueManager: QueueOperations,
    private val playSongs: PlaySongs,
    private val shuffleSongs: ShuffleSongs,
    private val addToPlaylistUseCase: AddToPlaylist,
    private val playlistRepository: PlaylistRepository,
    queueWatcher: QueueWatcher,
) : ViewModel() {

    val album: Album = AlbumDetailFragmentArgs.fromSavedStateHandle(savedStateHandle).album

    private val currentSong: Flow<Song?> = callbackFlow {
        val callback = object : QueueChangeCallback {
            override fun onQueuePositionChanged(oldPosition: Int?, newPosition: Int?) {
                trySend(queueManager.getCurrentItem()?.song)
            }
        }
        trySend(queueManager.getCurrentItem()?.song)
        queueWatcher.addCallback(callback)
        awaitClose { queueWatcher.removeCallback(callback) }
    }

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
        viewModelScope.launch {
            val songs = uiState.value.songs
            val result = playSongs(songs, position = songs.indexOf(song))
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
            playbackManager.addToQueue(listOf(song))
            _events.emit(AlbumDetailUiEvent.AddedToQueue(1))
        }
    }

    fun onAddAlbumToQueue() {
        viewModelScope.launch {
            val songs = uiState.value.songs
            playbackManager.addToQueue(songs)
            _events.emit(AlbumDetailUiEvent.AddedToQueue(songs.size))
        }
    }

    fun onPlayNext(song: Song) {
        viewModelScope.launch {
            playbackManager.playNext(listOf(song))
            _events.emit(AlbumDetailUiEvent.AddedToQueue(1))
        }
    }

    fun onPlayAlbumNext() {
        viewModelScope.launch {
            val songs = uiState.value.songs
            playbackManager.playNext(songs)
            _events.emit(AlbumDetailUiEvent.AddedToQueue(songs.size))
        }
    }

    fun onExclude(song: Song) {
        viewModelScope.launch {
            songRepository.setExcluded(listOf(song), true)
            queueManager.remove(song)
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

    fun onSongDeleted(song: Song) {
        viewModelScope.launch {
            songRepository.remove(song)
            queueManager.remove(song)
        }
    }

    fun addToPlaylist(playlist: Playlist, playlistData: PlaylistData, ignoreDuplicates: Boolean = false) {
        viewModelScope.launch {
            when (val result = addToPlaylistUseCase(playlist, playlistData, ignoreDuplicates)) {
                is AddToPlaylist.Result.Success ->
                    _events.emit(AlbumDetailUiEvent.AddedToPlaylist(result.playlist, result.playlistData))
                is AddToPlaylist.Result.DuplicatesFound ->
                    _events.emit(
                        AlbumDetailUiEvent.PlaylistDuplicatesFound(
                            result.playlist,
                            result.playlistData,
                            result.deduplicatedSongs,
                            result.duplicates,
                        )
                    )
                is AddToPlaylist.Result.Failure ->
                    _events.emit(AlbumDetailUiEvent.PlaylistAddFailed(result.message))
            }
        }
    }
}
