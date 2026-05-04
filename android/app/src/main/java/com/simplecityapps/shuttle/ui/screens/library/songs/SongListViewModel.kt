package com.simplecityapps.shuttle.ui.screens.library.songs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.mediaprovider.SongImportState
import com.simplecityapps.mediaprovider.SongImportStateProvider
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistQuery
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.mediaprovider.repository.songs.comparator
import com.simplecityapps.playback.PlaybackOperations
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.shuttle.di.IoDispatcher
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import com.simplecityapps.shuttle.sorting.SongSortOrder
import com.simplecityapps.shuttle.ui.common.SelectionState
import com.simplecityapps.shuttle.ui.common.playback.PlaySongs
import com.simplecityapps.shuttle.ui.common.playback.ShuffleSongs
import com.simplecityapps.shuttle.ui.common.playlist.AddToPlaylist
import com.simplecityapps.shuttle.ui.screens.library.SortPreferences
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SongListUiState(
    val songs: List<Song> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val selectedSongs: Set<Song> = emptySet(),
    val sortOrder: SongSortOrder = SongSortOrder.Default,
    val loadingState: LoadingState = LoadingState.Loading,
    val scanProgress: Progress? = null,
) {
    enum class LoadingState { Loading, Scanning, Ready, Empty }

    val isSelecting: Boolean get() = selectedSongs.isNotEmpty()
}

sealed interface SongListUiEvent {
    data class AddedToQueue(val songCount: Int) : SongListUiEvent
    data class PlaybackFailed(val errorMessage: String?) : SongListUiEvent
    data object LibraryEmpty : SongListUiEvent
    data class AddedToPlaylist(val playlist: Playlist, val playlistData: PlaylistData) : SongListUiEvent
    data class PlaylistDuplicatesFound(
        val playlist: Playlist,
        val playlistData: PlaylistData,
        val deduplicatedSongs: PlaylistData.Songs,
        val duplicates: List<Song>,
    ) : SongListUiEvent
    data class PlaylistAddFailed(val message: String?) : SongListUiEvent
}

@HiltViewModel
class SongListViewModel @Inject constructor(
    private val songRepository: SongRepository,
    private val playbackManager: PlaybackOperations,
    private val queueManager: QueueOperations,
    private val playSongs: PlaySongs,
    private val shuffleSongs: ShuffleSongs,
    private val addToPlaylistUseCase: AddToPlaylist,
    private val playlistRepository: PlaylistRepository,
    private val sortPreferenceManager: SortPreferences,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    mediaImportObserver: SongImportStateProvider,
) : ViewModel() {

    // We need to store Song.id instead of Song. Otherwise, when a song is
    // played/paused, it mutates so, when checking if it's contained in the
    // set of selected songs with Song.equals, it returns false.
    private val selectionState = SelectionState<Long>()

    private val _sortOrder = MutableStateFlow(sortPreferenceManager.sortOrderSongList)

    val uiState: StateFlow<SongListUiState> = combine(
        songRepository
            .getSongs(SongQuery.All(sortOrder = sortPreferenceManager.sortOrderSongList))
            .filterNotNull(),
        mediaImportObserver.songImportState,
        selectionState.selectedItems,
        _sortOrder,
        playlistRepository.getPlaylists(PlaylistQuery.All(mediaProviderType = null)),
    ) { songs, songImportState, selectedSongIds, sortOrder, playlists ->
        val selectedSongs = songs.filter { it.id in selectedSongIds }.toSet()

        if (songImportState is SongImportState.ImportProgress) {
            SongListUiState(
                loadingState = SongListUiState.LoadingState.Scanning,
                scanProgress = songImportState.progress,
                sortOrder = sortOrder,
                selectedSongs = selectedSongs,
                playlists = playlists,
            )
        } else {
            val sortedSongs = songs.sortedWith(sortOrder.comparator)
            SongListUiState(
                songs = sortedSongs,
                selectedSongs = selectedSongs,
                sortOrder = sortOrder,
                playlists = playlists,
                loadingState = if (sortedSongs.isEmpty()) {
                    SongListUiState.LoadingState.Empty
                } else {
                    SongListUiState.LoadingState.Ready
                },
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SongListUiState(),
    )

    private val _events = MutableSharedFlow<SongListUiEvent>()
    val events: SharedFlow<SongListUiEvent> = _events.asSharedFlow()

    fun onSongClick(song: Song) {
        if (selectionState.isActive()) {
            selectionState.toggle(song.id)
        } else {
            play(song)
        }
    }

    fun onSongLongClick(song: Song) {
        selectionState.toggle(song.id)
    }

    private fun play(song: Song) {
        viewModelScope.launch {
            val songs = uiState.value.songs.ifEmpty { listOf(song) }
            val result = playSongs(songs, position = songs.indexOf(song))
            if (result is PlaySongs.Result.Failure) {
                _events.emit(SongListUiEvent.PlaybackFailed(result.message))
            }
        }
    }

    fun onAddToQueue(song: Song) {
        viewModelScope.launch {
            playbackManager.addToQueue(listOf(song))
            _events.emit(SongListUiEvent.AddedToQueue(1))
        }
    }

    fun onAddSelectedToQueue() {
        viewModelScope.launch {
            val selected = selectedSongs()
            playbackManager.addToQueue(selected)
            _events.emit(SongListUiEvent.AddedToQueue(selected.size))
            selectionState.clear()
        }
    }

    fun onPlayNext(song: Song) {
        viewModelScope.launch {
            playbackManager.playNext(listOf(song))
            _events.emit(SongListUiEvent.AddedToQueue(1))
        }
    }

    fun onExclude(song: Song) {
        viewModelScope.launch {
            songRepository.setExcluded(listOf(song), true)
            queueManager.remove(song)
        }
    }

    fun onSongDeleted(song: Song) {
        viewModelScope.launch {
            songRepository.remove(song)
            queueManager.remove(song)
        }
    }

    fun onShuffle() {
        val songs = uiState.value.songs
        if (songs.isEmpty()) {
            viewModelScope.launch { _events.emit(SongListUiEvent.LibraryEmpty) }
            return
        }
        viewModelScope.launch {
            val result = shuffleSongs(songs)
            if (result is ShuffleSongs.Result.Failure) {
                _events.emit(SongListUiEvent.PlaybackFailed(result.message))
            }
        }
    }

    fun setSortOrder(sortOrder: SongSortOrder) {
        if (sortPreferenceManager.sortOrderSongList == sortOrder) {
            return
        }

        viewModelScope.launch {
            withContext(ioDispatcher) {
                sortPreferenceManager.sortOrderSongList = sortOrder
                _sortOrder.value = sortOrder
            }
        }
    }

    fun clearSelection() {
        selectionState.clear()
    }

    fun selectedSongs(): List<Song> = uiState.value.selectedSongs.toList()

    fun addToPlaylist(playlist: Playlist, playlistData: PlaylistData, ignoreDuplicates: Boolean = false) {
        viewModelScope.launch {
            when (val result = addToPlaylistUseCase(playlist, playlistData, ignoreDuplicates)) {
                is AddToPlaylist.Result.Success ->
                    _events.emit(SongListUiEvent.AddedToPlaylist(result.playlist, result.playlistData))
                is AddToPlaylist.Result.DuplicatesFound ->
                    _events.emit(
                        SongListUiEvent.PlaylistDuplicatesFound(
                            result.playlist,
                            result.playlistData,
                            result.deduplicatedSongs,
                            result.duplicates
                        )
                    )
                is AddToPlaylist.Result.Failure ->
                    _events.emit(SongListUiEvent.PlaylistAddFailed(result.message))
            }
        }
    }

    fun createPlaylist(name: String, playlistData: PlaylistData) {
        viewModelScope.launch {
            val songs = addToPlaylistUseCase.resolveSongs(playlistData)
            playlistRepository.createPlaylist(name, MediaProviderType.Shuttle, songs, null)
        }
    }
}
