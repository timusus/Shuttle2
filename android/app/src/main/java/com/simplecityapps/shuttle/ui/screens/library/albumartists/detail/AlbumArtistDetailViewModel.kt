package com.simplecityapps.shuttle.ui.screens.library.albumartists.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.mediaprovider.repository.albums.AlbumQuery
import com.simplecityapps.mediaprovider.repository.albums.AlbumRepository
import com.simplecityapps.mediaprovider.repository.artists.AlbumArtistQuery
import com.simplecityapps.mediaprovider.repository.artists.AlbumArtistRepository
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistQuery
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.playback.PlaybackOperations
import com.simplecityapps.playback.queue.QueueChangeCallback
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.playback.queue.QueueWatcher
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import com.simplecityapps.shuttle.ui.common.playback.PlaySongs
import com.simplecityapps.shuttle.ui.common.playback.ShuffleAlbums
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
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AlbumArtistDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val albumArtistRepository: AlbumArtistRepository,
    private val albumRepository: AlbumRepository,
    private val songRepository: SongRepository,
    private val playbackManager: PlaybackOperations,
    private val queueManager: QueueOperations,
    private val playSongs: PlaySongs,
    private val shuffleSongs: ShuffleSongs,
    private val shuffleAlbums: ShuffleAlbums,
    private val addToPlaylistUseCase: AddToPlaylist,
    private val playlistRepository: PlaylistRepository,
    queueWatcher: QueueWatcher,
) : ViewModel() {

    val albumArtist: AlbumArtist = AlbumArtistDetailFragmentArgs.fromSavedStateHandle(savedStateHandle).albumArtist

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

    val uiState: StateFlow<AlbumArtistDetailUiState> = combine(
        albumArtistRepository.getAlbumArtists(AlbumArtistQuery.AlbumArtistGroupKey(key = albumArtist.groupKey)),
        albumRepository.getAlbums(AlbumQuery.ArtistGroupKey(albumArtist.groupKey)),
        songRepository
            .getSongs(SongQuery.ArtistGroupKeys(listOf(SongQuery.ArtistGroupKey(key = albumArtist.groupKey))))
            .filterNotNull(),
        playlistRepository.getPlaylists(PlaylistQuery.All(mediaProviderType = null)),
        currentSong,
    ) { artists, albums, songs, playlists, currentSong ->
        val latestArtist = artists.firstOrNull() ?: albumArtist
        val sortedAlbums = albums.sortedByDescending { it.year ?: 0 }
        val albumOrder = sortedAlbums.withIndex().associate { (index, album) -> album.groupKey to index }
        val sortedSongs = songs.sortedWith(compareBy({ albumOrder[it.albumGroupKey] ?: Int.MAX_VALUE }, { it.track }))
        AlbumArtistDetailUiState(
            albumArtist = latestArtist,
            albums = sortedAlbums,
            songs = sortedSongs,
            playlists = playlists,
            currentSong = currentSong,
            loadingState = if (albums.isEmpty() && songs.isEmpty()) {
                AlbumArtistDetailUiState.LoadingState.Empty
            } else {
                AlbumArtistDetailUiState.LoadingState.Ready
            },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AlbumArtistDetailUiState(albumArtist = albumArtist),
    )

    private val _events = MutableSharedFlow<AlbumArtistDetailUiEvent>()
    val events: SharedFlow<AlbumArtistDetailUiEvent> = _events.asSharedFlow()

    // Song actions

    fun onSongClick(song: Song) {
        viewModelScope.launch {
            val songs = uiState.value.songs
            val result = playSongs(songs, position = songs.indexOf(song))
            if (result is PlaySongs.Result.Failure) {
                _events.emit(AlbumArtistDetailUiEvent.PlaybackFailed(result.message))
            }
        }
    }

    fun onAddToQueue(song: Song) {
        viewModelScope.launch {
            playbackManager.addToQueue(listOf(song))
            _events.emit(AlbumArtistDetailUiEvent.AddedToQueue(1))
        }
    }

    fun onPlayNext(song: Song) {
        viewModelScope.launch {
            playbackManager.playNext(listOf(song))
            _events.emit(AlbumArtistDetailUiEvent.AddedToQueue(1))
        }
    }

    fun onExcludeSong(song: Song) {
        viewModelScope.launch {
            songRepository.setExcluded(listOf(song), true)
            queueManager.remove(song)
        }
    }

    fun onEditSongTags(song: Song) {
        viewModelScope.launch {
            _events.emit(AlbumArtistDetailUiEvent.EditTags(listOf(song)))
        }
    }

    fun onSongDeleted(song: Song) {
        viewModelScope.launch {
            songRepository.remove(song)
            queueManager.remove(song)
        }
    }

    // Album actions

    fun onPlayAlbum(album: Album) {
        viewModelScope.launch {
            val songs = songRepository.getSongs(SongQuery.AlbumGroupKey(key = album.groupKey)).filterNotNull().firstOrNull().orEmpty()
            val result = playSongs(songs)
            if (result is PlaySongs.Result.Failure) {
                _events.emit(AlbumArtistDetailUiEvent.PlaybackFailed(result.message))
            }
        }
    }

    fun onAddAlbumToQueue(album: Album) {
        viewModelScope.launch {
            val songs = songRepository.getSongs(SongQuery.AlbumGroupKey(key = album.groupKey)).filterNotNull().firstOrNull().orEmpty()
            playbackManager.addToQueue(songs)
            _events.emit(AlbumArtistDetailUiEvent.AddedToQueue(songs.size))
        }
    }

    fun onPlayAlbumNext(album: Album) {
        viewModelScope.launch {
            val songs = songRepository.getSongs(SongQuery.AlbumGroupKey(key = album.groupKey)).filterNotNull().firstOrNull().orEmpty()
            playbackManager.playNext(songs)
            _events.emit(AlbumArtistDetailUiEvent.AddedToQueue(songs.size))
        }
    }

    fun onExcludeAlbum(album: Album) {
        viewModelScope.launch {
            val songs = songRepository.getSongs(SongQuery.AlbumGroupKey(key = album.groupKey)).filterNotNull().firstOrNull().orEmpty()
            songRepository.setExcluded(songs, true)
        }
    }

    fun onEditAlbumTags(album: Album) {
        viewModelScope.launch {
            val songs = songRepository.getSongs(SongQuery.AlbumGroupKey(key = album.groupKey)).filterNotNull().firstOrNull().orEmpty()
            _events.emit(AlbumArtistDetailUiEvent.EditTags(songs))
        }
    }

    // Artist-level toolbar actions

    fun onPlayAll() {
        viewModelScope.launch {
            val songs = uiState.value.songs
            if (songs.isEmpty()) return@launch
            val result = playSongs(songs)
            if (result is PlaySongs.Result.Failure) {
                _events.emit(AlbumArtistDetailUiEvent.PlaybackFailed(result.message))
            }
        }
    }

    fun onShuffleAll() {
        viewModelScope.launch {
            val songs = uiState.value.songs
            if (songs.isEmpty()) return@launch
            val result = shuffleSongs(songs)
            if (result is ShuffleSongs.Result.Failure) {
                _events.emit(AlbumArtistDetailUiEvent.PlaybackFailed(result.message))
            }
        }
    }

    fun onShuffleAlbums() {
        viewModelScope.launch {
            val songs = uiState.value.songs
            if (songs.isEmpty()) return@launch
            val result = shuffleAlbums(songs)
            if (result is ShuffleAlbums.Result.Failure) {
                _events.emit(AlbumArtistDetailUiEvent.PlaybackFailed(result.message))
            }
        }
    }

    fun onAddAllToQueue() {
        viewModelScope.launch {
            val songs = uiState.value.songs
            playbackManager.addToQueue(songs)
            _events.emit(AlbumArtistDetailUiEvent.AddedToQueue(songs.size))
        }
    }

    fun onPlayAllNext() {
        viewModelScope.launch {
            val songs = uiState.value.songs
            playbackManager.playNext(songs)
            _events.emit(AlbumArtistDetailUiEvent.AddedToQueue(songs.size))
        }
    }

    fun onEditArtistTags() {
        viewModelScope.launch {
            _events.emit(AlbumArtistDetailUiEvent.EditTags(uiState.value.songs))
        }
    }

    // Playlist actions

    fun addToPlaylist(playlist: Playlist, playlistData: PlaylistData, ignoreDuplicates: Boolean = false) {
        viewModelScope.launch {
            when (val result = addToPlaylistUseCase(playlist, playlistData, ignoreDuplicates)) {
                is AddToPlaylist.Result.Success ->
                    _events.emit(AlbumArtistDetailUiEvent.AddedToPlaylist(result.playlist, result.playlistData))
                is AddToPlaylist.Result.DuplicatesFound ->
                    _events.emit(
                        AlbumArtistDetailUiEvent.PlaylistDuplicatesFound(
                            result.playlist,
                            result.playlistData,
                            result.deduplicatedSongs,
                            result.duplicates,
                        )
                    )
                is AddToPlaylist.Result.Failure ->
                    _events.emit(AlbumArtistDetailUiEvent.PlaylistAddFailed(result.message))
            }
        }
    }
}
