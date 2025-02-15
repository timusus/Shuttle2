package com.simplecityapps.shuttle.ui.screens.library.genres

import androidx.annotation.OpenForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.mediaprovider.MediaImportObserver
import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.mediaprovider.SongImportState
import com.simplecityapps.mediaprovider.repository.genres.GenreQuery
import com.simplecityapps.mediaprovider.repository.genres.GenreRepository
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.shuttle.model.Genre
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.query.SongQuery
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

@OpenForTesting
@HiltViewModel
class GenreListViewModel @Inject constructor(
    private val genreRepository: GenreRepository,
    private val songRepository: SongRepository,
    private val playbackManager: PlaybackManager,
    private val queueManager: QueueManager,
    preferenceManager: GeneralPreferenceManager,
    mediaImportObserver: MediaImportObserver
) : ViewModel() {
    private val _viewState = MutableStateFlow<ViewState>(ViewState.Loading)
    val viewState = _viewState.asStateFlow()

    init {
        combine(
            genreRepository.getGenres(GenreQuery.All()),
            mediaImportObserver.songImportState,
            mediaImportObserver.playlistImportState
        ) { genres, songImportState, playlistImportState ->
            if (songImportState is SongImportState.ImportProgress) {
                _viewState.emit(ViewState.Scanning(songImportState.progress))
            } else {
                _viewState.emit(ViewState.Ready(genres))
            }
        }
            .onStart {
                _viewState.emit(ViewState.Loading)
            }
            .launchIn(viewModelScope)
    }

    val theme = preferenceManager.theme(viewModelScope)
    val accent = preferenceManager.accent(viewModelScope)
    val extraDark = preferenceManager.extraDark(viewModelScope)

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

    private suspend fun getSongsForGenreOrEmpty(genre: Genre) = genreRepository.getSongsForGenre(genre.name, SongQuery.All())
        .firstOrNull()
        .orEmpty()

    sealed class ViewState {
        data class Scanning(val progress: Progress?) : ViewState()
        data object Loading : ViewState()
        data class Ready(val genres: List<Genre>) : ViewState()
    }
}
