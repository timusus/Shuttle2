package com.simplecityapps.shuttle.ui.screens.library.genres

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.mediaprovider.MediaImporter
import com.simplecityapps.mediaprovider.repository.genres.GenreQuery
import com.simplecityapps.mediaprovider.repository.genres.GenreRepository
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.shuttle.model.Genre
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GenreListViewModel @Inject constructor(
    private val genreRepository: GenreRepository,
    private val songRepository: SongRepository,
    private val playbackManager: PlaybackManager,
    private val mediaImporter: MediaImporter,
    private val queueManager: QueueManager,
) : ViewModel() {
    private val _viewState = MutableStateFlow<ViewState>(ViewState.Loading)
    val viewState = _viewState.asStateFlow()

    init {
        genreRepository.getGenres(GenreQuery.All())
            .onStart {
                if (isImportingMedia()) {
                    _viewState.emit(ViewState.Scanning)
                } else {
                    _viewState.emit(ViewState.Loading)
                }
            }
            .onEach { genres ->
                _viewState.emit(ViewState.Ready(genres))
            }
            .launchIn(viewModelScope)
    }

    fun play(genre: Genre, completion: (Result<Boolean>) -> Unit) {
        viewModelScope.launch {
            val songs = getSongsForGenreOrEmpty(genre)
            if (queueManager.setQueue(songs)) {
                playbackManager.load { result ->
                    result.onSuccess { playbackManager.play() }
                    completion(result)
                }
            }
        }
    }

    fun addToQueue(genre: Genre, completion: (Result<Genre>) -> Unit) {
        viewModelScope.launch {
            val songs = getSongsForGenreOrEmpty(genre)
            playbackManager.addToQueue(songs)
            completion(Result.success(genre))
        }
    }

    fun playNext(genre: Genre, completion: (Result<Genre>) -> Unit) {
        viewModelScope.launch {
            val songs = getSongsForGenreOrEmpty(genre)
            playbackManager.playNext(songs)
            completion(Result.success(genre))
        }
    }

    fun exclude(genre: Genre) {
        viewModelScope.launch {
            val songs = getSongsForGenreOrEmpty(genre)
            songRepository.setExcluded(songs, true)
            queueManager.remove(
                queueManager
                    .getQueue()
                    .filter { queueItem -> songs.contains(queueItem.song) }
            )
        }
    }

    fun editTags(genre: Genre, completion: (Result<List<Song>>) -> Unit) {
        viewModelScope.launch {
            val songs = getSongsForGenreOrEmpty(genre)
            completion(Result.success(songs))
        }
    }

    private suspend fun getSongsForGenreOrEmpty(genre: Genre) =
        genreRepository.getSongsForGenre(genre.name, SongQuery.All())
            .firstOrNull()
            .orEmpty()

    fun isImportingMedia() = mediaImporter.isImporting

    fun addMediaImporterListener(listener: MediaImporter.Listener) =
        mediaImporter.listeners.add(listener)

    fun removeMediaImporterListener(listener: MediaImporter.Listener) =
        mediaImporter.listeners.remove(listener)

    sealed class ViewState {
        data object Scanning : ViewState()
        data object Loading : ViewState()
        data class Ready(val genres: List<Genre>) : ViewState()
    }
}
