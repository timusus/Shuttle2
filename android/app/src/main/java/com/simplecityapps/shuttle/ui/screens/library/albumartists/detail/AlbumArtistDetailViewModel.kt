package com.simplecityapps.shuttle.ui.screens.library.albumartists.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.mediaprovider.repository.albums.AlbumQuery
import com.simplecityapps.mediaprovider.repository.artists.AlbumArtistQuery
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtistGroupKey
import com.simplecityapps.shuttle.model.AlbumGroupKey
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import com.simplecityapps.shuttle.ui.actions.AddToPlaylist
import com.simplecityapps.shuttle.ui.actions.DeleteSongs
import com.simplecityapps.shuttle.ui.actions.EnqueueSongs
import com.simplecityapps.shuttle.ui.actions.ExcludeSongs
import com.simplecityapps.shuttle.ui.actions.MediaSelection
import com.simplecityapps.shuttle.ui.actions.ObserveAlbumArtists
import com.simplecityapps.shuttle.ui.actions.ObserveAlbums
import com.simplecityapps.shuttle.ui.actions.ObservePlaylists
import com.simplecityapps.shuttle.ui.actions.ObserveSongs
import com.simplecityapps.shuttle.ui.actions.PlaySongs
import com.simplecityapps.shuttle.ui.actions.ResolveSongs
import com.simplecityapps.shuttle.ui.actions.ShuffleAlbums
import com.simplecityapps.shuttle.ui.actions.ShuffleSongs
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData
import com.simplecityapps.shuttle.ui.screens.playlistmenu.toMediaSelection
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One album artist's albums and songs, loaded by [groupKey], the key its route carries. */
@HiltViewModel(assistedFactory = AlbumArtistDetailViewModel.Factory::class)
class AlbumArtistDetailViewModel @AssistedInject constructor(
    @Assisted private val groupKey: AlbumArtistGroupKey,
    private val observeAlbumArtists: ObserveAlbumArtists,
    private val observeAlbums: ObserveAlbums,
    private val observeSongs: ObserveSongs,
    private val queueManager: QueueOperations,
    private val playSongs: PlaySongs,
    private val resolveSongs: ResolveSongs,
    private val enqueueSongs: EnqueueSongs,
    private val excludeSongs: ExcludeSongs,
    private val deleteSongs: DeleteSongs,
    private val shuffleSongs: ShuffleSongs,
    private val shuffleAlbums: ShuffleAlbums,
    private val addToPlaylistUseCase: AddToPlaylist,
    private val observePlaylists: ObservePlaylists,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(groupKey: AlbumArtistGroupKey): AlbumArtistDetailViewModel
    }

    private val currentSong: Flow<Song?> = queueManager.queueStateFlow
        .map { queueState -> queueState.currentItem?.song }
        .distinctUntilChanged()

    private val expandedAlbums = MutableStateFlow<Set<AlbumGroupKey>>(emptySet())

    val uiState: StateFlow<AlbumArtistDetailUiState> = combine(
        observeAlbumArtists(AlbumArtistQuery.AlbumArtistGroupKey(key = groupKey)),
        observeAlbums(AlbumQuery.ArtistGroupKey(groupKey)),
        observeSongs(SongQuery.ArtistGroupKeys(listOf(SongQuery.ArtistGroupKey(key = groupKey)))),
        observePlaylists(),
        currentSong,
    ) { artists, albums, songs, playlists, currentSong ->
        val sortedAlbums = albums.sortedByDescending { it.year ?: 0 }
        val albumOrder = sortedAlbums.withIndex().associate { (index, album) -> album.groupKey to index }
        val sortedSongs = songs.sortedWith(compareBy({ albumOrder[it.albumGroupKey] ?: Int.MAX_VALUE }, { it.disc }, { it.track }))
        AlbumArtistDetailUiState(
            albumArtist = artists.firstOrNull(),
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
    }.combine(expandedAlbums) { state, expanded ->
        state.copy(expandedAlbums = expanded)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AlbumArtistDetailUiState(),
    )

    private val _events = MutableSharedFlow<AlbumArtistDetailUiEvent>()
    val events: SharedFlow<AlbumArtistDetailUiEvent> = _events.asSharedFlow()

    // Song actions

    fun onSongClick(song: Song) {
        // Snapshot synchronously, at click time -- see SongListViewModel.play()'s comment for why
        // reading uiState.value inside the launched coroutine can race a still-settling uiState.
        val songs = uiState.value.songs
        val position = songs.indexOf(song)
        viewModelScope.launch {
            val result = playSongs(songs, position = position)
            if (result is PlaySongs.Result.Failure) {
                _events.emit(AlbumArtistDetailUiEvent.PlaybackFailed(result.message))
            }
        }
    }

    fun onAddToQueue(song: Song) {
        viewModelScope.launch {
            enqueueSongs(MediaSelection.Songs(song), EnqueueSongs.Position.End)
            _events.emit(AlbumArtistDetailUiEvent.AddedToQueue(1))
        }
    }

    fun onPlayNext(song: Song) {
        viewModelScope.launch {
            enqueueSongs(MediaSelection.Songs(song), EnqueueSongs.Position.Next)
            _events.emit(AlbumArtistDetailUiEvent.AddedToQueue(1))
        }
    }

    fun onExcludeSong(song: Song) {
        viewModelScope.launch {
            excludeSongs(MediaSelection.Songs(song))
        }
    }

    fun onEditSongTags(song: Song) {
        viewModelScope.launch {
            _events.emit(AlbumArtistDetailUiEvent.EditTags(listOf(song)))
        }
    }

    fun onDelete(song: Song) {
        viewModelScope.launch {
            if (deleteSongs(MediaSelection.Songs(song)).failed.isNotEmpty()) _events.emit(AlbumArtistDetailUiEvent.DeleteFailed)
        }
    }

    // Album actions

    /** Unfolds (or folds) the album's track list in place, rather than navigating away. */
    fun onAlbumClick(album: Album) {
        val key = album.groupKey ?: return
        expandedAlbums.update { expanded ->
            if (key in expanded) expanded - key else expanded + key
        }
    }

    /** Plays a song from within an expanded album, queueing that album's songs rather than the artist's. */
    fun onAlbumSongClick(song: Song, songs: List<Song>) {
        viewModelScope.launch {
            val result = playSongs(songs, position = songs.indexOf(song))
            if (result is PlaySongs.Result.Failure) {
                _events.emit(AlbumArtistDetailUiEvent.PlaybackFailed(result.message))
            }
        }
    }

    fun onPlayAlbum(album: Album) {
        viewModelScope.launch {
            val songs = resolveSongs(MediaSelection.Albums(album))
            val result = playSongs(songs)
            if (result is PlaySongs.Result.Failure) {
                _events.emit(AlbumArtistDetailUiEvent.PlaybackFailed(result.message))
            }
        }
    }

    fun onAddAlbumToQueue(album: Album) {
        viewModelScope.launch {
            val songs = resolveSongs(MediaSelection.Albums(album))
            enqueueSongs(MediaSelection.Songs(songs), EnqueueSongs.Position.End)
            _events.emit(AlbumArtistDetailUiEvent.AddedToQueue(songs.size))
        }
    }

    fun onPlayAlbumNext(album: Album) {
        viewModelScope.launch {
            val songs = resolveSongs(MediaSelection.Albums(album))
            enqueueSongs(MediaSelection.Songs(songs), EnqueueSongs.Position.Next)
            _events.emit(AlbumArtistDetailUiEvent.AddedToQueue(songs.size))
        }
    }

    fun onExcludeAlbum(album: Album) {
        viewModelScope.launch {
            excludeSongs(MediaSelection.Albums(album))
        }
    }

    fun onEditAlbumTags(album: Album) {
        viewModelScope.launch {
            val songs = resolveSongs(MediaSelection.Albums(album))
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
            enqueueSongs(MediaSelection.Songs(songs), EnqueueSongs.Position.End)
            _events.emit(AlbumArtistDetailUiEvent.AddedToQueue(songs.size))
        }
    }

    fun onPlayAllNext() {
        viewModelScope.launch {
            val songs = uiState.value.songs
            enqueueSongs(MediaSelection.Songs(songs), EnqueueSongs.Position.Next)
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
            when (val result = addToPlaylistUseCase(playlist, playlistData.toMediaSelection(), ignoreDuplicates)) {
                is AddToPlaylist.Result.Success ->
                    _events.emit(AlbumArtistDetailUiEvent.AddedToPlaylist(result.playlist, playlistData))

                is AddToPlaylist.Result.DuplicatesFound ->
                    _events.emit(
                        AlbumArtistDetailUiEvent.PlaylistDuplicatesFound(
                            result.playlist,
                            playlistData,
                            PlaylistData.Songs(result.nonDuplicates),
                            result.duplicates,
                        )
                    )

                is AddToPlaylist.Result.Failure ->
                    _events.emit(AlbumArtistDetailUiEvent.PlaylistAddFailed(result.message))
            }
        }
    }
}
