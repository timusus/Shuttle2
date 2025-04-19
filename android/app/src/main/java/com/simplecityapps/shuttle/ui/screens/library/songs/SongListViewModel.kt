package com.simplecityapps.shuttle.ui.screens.library.songs

import android.app.Application
import androidx.annotation.OpenForTesting
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.mediaprovider.MediaImportObserver
import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.mediaprovider.SongImportState
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import com.simplecityapps.shuttle.ui.common.error.UserFriendlyError
import com.simplecityapps.shuttle.ui.screens.library.SortPreferenceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch


@OpenForTesting
@HiltViewModel
class SongListViewModel @Inject constructor(
    private val songRepository: SongRepository,
    private val playbackManager: PlaybackManager,
    private val queueManager: QueueManager,
    private val sortPreferenceManager: SortPreferenceManager,
    mediaImportObserver: MediaImportObserver,
    application: Application,
) : AndroidViewModel(application) {
    private val _viewState = MutableStateFlow<ViewState>(ViewState.Loading)
    val viewState = _viewState.asStateFlow()

    private val selectedSongsState = MutableStateFlow(emptySet<Song>())
    val selectedSongCountState = selectedSongsState.asStateFlow()
        .map { selectedSongs -> selectedSongs.size }

    init {
        combine(
            songRepository
                .getSongs(SongQuery.All(sortOrder = sortPreferenceManager.sortOrderSongList))
                .filterNotNull(),
            mediaImportObserver.songImportState,
            selectedSongsState,
        ) { songs, songImportState, selectedSongs ->
            if (songImportState is SongImportState.ImportProgress) {
                _viewState.emit(ViewState.Scanning(songImportState.progress))
            } else {
                _viewState.emit(ViewState.Ready(songs, selectedSongs))
            }
        }
            .onStart {
                _viewState.emit(ViewState.Loading)
            }
            .launchIn(viewModelScope)
    }

    fun onSongClick(song: Song, completion: (Result<Boolean>) -> Unit) {
        if (isSelecting()) {
            toggleSongSelection(song)
            completion(Result.success(true))
        } else {
            play(song, completion)
        }
    }

    fun onSongLongClick(song: Song) {
        toggleSongSelection(song)
    }

    private fun toggleSongSelection(song: Song) {
        selectedSongsState.value = if (selectedSongsState.value.contains(song)) {
            selectedSongsState.value - song
        } else {
            selectedSongsState.value + song
        }
    }

    private fun play(song: Song, completion: (Result<Boolean>) -> Unit) {
        viewModelScope.launch {
            val songs = viewState.value.let {
                if (it is ViewState.Ready) it.songs else listOf(song)
            }

            if (queueManager.setQueue(songs = songs, position = songs.indexOf(song))) {
                playbackManager.load { result ->
                    result.onSuccess { playbackManager.play() }
                    completion(result)
                }
            }
        }
    }

    fun addToQueue(song: Song, completion: (Result<Song>) -> Unit) {
        viewModelScope.launch {
            playbackManager.addToQueue(listOf(song))
            completion(Result.success(song))
        }
    }

    fun addSelectedToQueue() {
        viewModelScope.launch {
            playbackManager.addToQueue(selectedSongsState.value.toList())
            selectedSongsState.value = emptySet()
        }
    }

    fun playNext(song: Song, completion: (Result<Song>) -> Unit) {
        viewModelScope.launch {
            playbackManager.playNext(listOf(song))
            completion(Result.success(song))
        }
    }

    fun exclude(song: Song) {
        viewModelScope.launch {
            songRepository.setExcluded(listOf(song), true)
            queueManager.remove(
                queueManager
                    .getQueue()
                    .filter { queueItem -> song == queueItem.song },
            )
        }
    }

    fun delete(song: Song) {
        val context = getApplication<Application>().applicationContext
        val documentFile = DocumentFile.fromSingleUri(context, song.path.toUri())

        if (documentFile?.delete() == false) {
            throw UserFriendlyError(context.getString(R.string.delete_song_failed))
        }

        viewModelScope.launch {
            songRepository.remove(song)
            val songQueueItem = queueManager.getQueue().filter { it.song.id == song.id }
            queueManager.remove(songQueueItem)
        }
    }

    fun shuffle(completion: (Result<Any?>) -> Unit) {
        val songs = getSongs()

        if (songs.isEmpty()) {
            completion(Result.failure(UserFriendlyError("Your library is empty")))
            return
        }

        viewModelScope.launch {
            playbackManager.shuffle(songs) { result ->
                result.onSuccess { playbackManager.play() }
                completion(result)
            }
        }
    }

    private fun getSongs(): List<Song> = viewState.value.let {
        if (it is ViewState.Ready) it.songs else emptyList()
    }

    private fun isSelecting() = selectedSongsState.value.isNotEmpty()

    sealed class ViewState {
        data class Scanning(val progress: Progress?) : ViewState()
        data object Loading : ViewState()
        data class Ready(
            val songs: List<Song>,
            val selectedSongs: Set<Song>,
        ) : ViewState()
    }
}
