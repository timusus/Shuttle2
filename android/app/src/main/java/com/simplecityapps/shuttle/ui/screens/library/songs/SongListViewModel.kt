package com.simplecityapps.shuttle.ui.screens.library.songs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.mediaprovider.SongImportState
import com.simplecityapps.mediaprovider.SongImportStateProvider
import com.simplecityapps.mediaprovider.repository.songs.comparator
import com.simplecityapps.shuttle.di.IoDispatcher
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import com.simplecityapps.shuttle.sorting.SongSortOrder
import com.simplecityapps.shuttle.ui.actions.AddToPlaylist
import com.simplecityapps.shuttle.ui.actions.CreatePlaylist
import com.simplecityapps.shuttle.ui.actions.DeleteSongs
import com.simplecityapps.shuttle.ui.actions.EnqueueSongs
import com.simplecityapps.shuttle.ui.actions.ExcludeSongs
import com.simplecityapps.shuttle.ui.actions.MediaSelection
import com.simplecityapps.shuttle.ui.actions.ObservePlaylists
import com.simplecityapps.shuttle.ui.actions.ObserveSongs
import com.simplecityapps.shuttle.ui.actions.PlaySongs
import com.simplecityapps.shuttle.ui.actions.ShuffleSongs
import com.simplecityapps.shuttle.ui.common.SelectionState
import com.simplecityapps.shuttle.ui.screens.library.SortPreferences
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData
import com.simplecityapps.shuttle.ui.screens.playlistmenu.toMediaSelection
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
    data object DeleteFailed : SongListUiEvent
}

@HiltViewModel
class SongListViewModel @Inject constructor(
    observeSongs: ObserveSongs,
    private val playSongs: PlaySongs,
    private val shuffleSongs: ShuffleSongs,
    private val addToPlaylistUseCase: AddToPlaylist,
    private val createPlaylistUseCase: CreatePlaylist,
    private val enqueueSongs: EnqueueSongs,
    private val excludeSongs: ExcludeSongs,
    private val deleteSongs: DeleteSongs,
    observePlaylists: ObservePlaylists,
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
        observeSongs(SongQuery.All(sortOrder = sortPreferenceManager.sortOrderSongList)),
        mediaImportObserver.songImportState,
        selectionState.selectedItems,
        _sortOrder,
        observePlaylists(),
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
        // Snapshot the list synchronously, at click time: uiState's combine() can still be
        // settling (import in progress, a sort re-emission), and viewModelScope.launch defers
        // this coroutine's body to a later dispatch, so reading uiState.value.songs inside the
        // launch block can race a newer emission and land indexOf(song) on the wrong position.
        val songs = uiState.value.songs.ifEmpty { listOf(song) }
        val position = songs.indexOf(song)
        viewModelScope.launch {
            val result = playSongs(songs, position = position)
            if (result is PlaySongs.Result.Failure) {
                _events.emit(SongListUiEvent.PlaybackFailed(result.message))
            }
        }
    }

    fun onAddToQueue(song: Song) {
        viewModelScope.launch {
            enqueueSongs(MediaSelection.Songs(song), EnqueueSongs.Position.End)
            _events.emit(SongListUiEvent.AddedToQueue(1))
        }
    }

    fun onAddSelectedToQueue() {
        viewModelScope.launch {
            val selected = selectedSongs()
            enqueueSongs(MediaSelection.Songs(selected), EnqueueSongs.Position.End)
            _events.emit(SongListUiEvent.AddedToQueue(selected.size))
            selectionState.clear()
        }
    }

    fun onPlayNext(song: Song) {
        viewModelScope.launch {
            enqueueSongs(MediaSelection.Songs(song), EnqueueSongs.Position.Next)
            _events.emit(SongListUiEvent.AddedToQueue(1))
        }
    }

    fun onExclude(song: Song) {
        viewModelScope.launch {
            excludeSongs(MediaSelection.Songs(song))
        }
    }

    fun onDelete(song: Song) {
        viewModelScope.launch {
            if (deleteSongs(MediaSelection.Songs(song)).failed.isNotEmpty()) _events.emit(SongListUiEvent.DeleteFailed)
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
            when (val result = addToPlaylistUseCase(playlist, playlistData.toMediaSelection(), ignoreDuplicates)) {
                is AddToPlaylist.Result.Success ->
                    _events.emit(SongListUiEvent.AddedToPlaylist(result.playlist, playlistData))

                is AddToPlaylist.Result.DuplicatesFound ->
                    _events.emit(
                        SongListUiEvent.PlaylistDuplicatesFound(
                            result.playlist,
                            playlistData,
                            PlaylistData.Songs(result.nonDuplicates),
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
            createPlaylistUseCase(name, playlistData.toMediaSelection())
        }
    }
}
