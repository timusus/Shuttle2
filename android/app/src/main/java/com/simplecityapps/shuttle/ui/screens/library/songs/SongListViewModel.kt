package com.simplecityapps.shuttle.ui.screens.library.songs

import android.app.Application
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.mediaprovider.SongImportState
import com.simplecityapps.mediaprovider.SongImportStateProvider
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.mediaprovider.repository.songs.comparator
import com.simplecityapps.playback.PlaybackOperations
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.di.IoDispatcher
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import com.simplecityapps.shuttle.sorting.SongSortOrder
import com.simplecityapps.shuttle.ui.common.SelectionState
import com.simplecityapps.shuttle.ui.screens.library.SortPreferences
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
    data class Error(val message: String) : SongListUiEvent
}

@HiltViewModel
class SongListViewModel @Inject constructor(
    private val songRepository: SongRepository,
    private val playbackManager: PlaybackOperations,
    private val queueManager: QueueOperations,
    private val sortPreferenceManager: SortPreferences,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    mediaImportObserver: SongImportStateProvider,
    application: Application,
) : AndroidViewModel(application) {

    private val selectionState = SelectionState<Song>()

    private val _sortOrder = MutableStateFlow(sortPreferenceManager.sortOrderSongList)

    val uiState: StateFlow<SongListUiState> = combine(
        songRepository
            .getSongs(SongQuery.All(sortOrder = sortPreferenceManager.sortOrderSongList))
            .filterNotNull(),
        mediaImportObserver.songImportState,
        selectionState.selectedItems,
        _sortOrder,
    ) { songs, songImportState, selectedSongs, sortOrder ->
        if (songImportState is SongImportState.ImportProgress) {
            SongListUiState(
                loadingState = SongListUiState.LoadingState.Scanning,
                scanProgress = songImportState.progress,
                sortOrder = sortOrder,
                selectedSongs = selectedSongs,
            )
        } else {
            val sortedSongs = songs.sortedWith(sortOrder.comparator)
            SongListUiState(
                songs = sortedSongs,
                selectedSongs = selectedSongs,
                sortOrder = sortOrder,
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
            selectionState.toggle(song)
        } else {
            play(song)
        }
    }

    fun onSongLongClick(song: Song) {
        selectionState.toggle(song)
    }

    private fun play(song: Song) {
        viewModelScope.launch {
            val songs = uiState.value.songs.ifEmpty { listOf(song) }

            if (queueManager.setQueue(songs = songs, position = songs.indexOf(song))) {
                playbackManager.load { result ->
                    result.onSuccess { playbackManager.play() }
                    result.onFailure { error ->
                        viewModelScope.launch {
                            _events.emit(
                                SongListUiEvent.Error(
                                    error.message ?: getApplication<Application>().getString(R.string.error_unknown)
                                )
                            )
                        }
                    }
                }
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
            val selected = selectionState.selectedItems.value.toList()
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

    fun onDelete(song: Song) {
        val context = getApplication<Application>().applicationContext
        val documentFile = DocumentFile.fromSingleUri(context, song.path.toUri())

        if (documentFile?.delete() == false) {
            viewModelScope.launch {
                _events.emit(
                    SongListUiEvent.Error(context.getString(R.string.delete_song_failed))
                )
            }
            return
        }

        viewModelScope.launch {
            songRepository.remove(song)
            queueManager.remove(song)
        }
    }

    fun onShuffle() {
        val songs = uiState.value.songs

        if (songs.isEmpty()) {
            viewModelScope.launch {
                _events.emit(SongListUiEvent.Error("Your library is empty"))
            }
            return
        }

        viewModelScope.launch {
            playbackManager.shuffle(songs) { result ->
                result.onSuccess { playbackManager.play() }
                result.onFailure { error ->
                    viewModelScope.launch {
                        _events.emit(
                            SongListUiEvent.Error(
                                error.message ?: getApplication<Application>().getString(R.string.error_unknown)
                            )
                        )
                    }
                }
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

    fun selectedSongs(): List<Song> = selectionState.selectedItems.value.toList()
}
