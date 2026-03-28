package com.simplecityapps.shuttle.ui.screens.library.albumartists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.mediaprovider.SongImportState
import com.simplecityapps.mediaprovider.SongImportStateProvider
import com.simplecityapps.mediaprovider.repository.artists.AlbumArtistQuery
import com.simplecityapps.mediaprovider.repository.artists.AlbumArtistRepository
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.playback.PlaybackOperations
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import com.simplecityapps.shuttle.ui.common.SelectionState
import com.simplecityapps.shuttle.ui.screens.library.ViewMode
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
}

@HiltViewModel
class AlbumArtistListViewModel @Inject constructor(
    private val albumArtistRepository: AlbumArtistRepository,
    private val songRepository: SongRepository,
    private val playbackManager: PlaybackOperations,
    private val queueManager: QueueOperations,
    private val preferenceManager: ArtistListPreferences,
    mediaImportObserver: SongImportStateProvider,
) : ViewModel() {

    private val selectionState = SelectionState<AlbumArtist>()

    private val _viewMode = MutableStateFlow(preferenceManager.artistListViewMode)

    val uiState: StateFlow<AlbumArtistListUiState> = combine(
        albumArtistRepository.getAlbumArtists(AlbumArtistQuery.All()),
        mediaImportObserver.songImportState,
        selectionState.selectedItems,
        _viewMode,
    ) { albumArtists, songImportState, selectedArtists, viewMode ->
        if (songImportState is SongImportState.ImportProgress) {
            AlbumArtistListUiState(
                loadingState = AlbumArtistListUiState.LoadingState.Scanning,
                scanProgress = songImportState.progress,
                viewMode = viewMode,
                selectedArtists = selectedArtists,
            )
        } else {
            AlbumArtistListUiState(
                albumArtists = albumArtists,
                selectedArtists = selectedArtists,
                viewMode = viewMode,
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
            if (queueManager.setQueue(songs)) {
                playbackManager.load { result ->
                    result.onSuccess { playbackManager.play() }
                    result.onFailure { error ->
                        viewModelScope.launch {
                            _events.emit(AlbumArtistListUiEvent.PlaybackFailed(error.message))
                        }
                    }
                }
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

    private suspend fun getSongsForArtist(albumArtist: AlbumArtist): List<Song> = songRepository
        .getSongs(SongQuery.ArtistGroupKeys(listOf(SongQuery.ArtistGroupKey(key = albumArtist.groupKey))))
        .firstOrNull()
        .orEmpty()

    private suspend fun getSongsForArtists(albumArtists: List<AlbumArtist>): List<Song> = songRepository
        .getSongs(SongQuery.ArtistGroupKeys(albumArtists.map { SongQuery.ArtistGroupKey(key = it.groupKey) }))
        .firstOrNull()
        .orEmpty()
}
