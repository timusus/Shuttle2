package com.simplecityapps.shuttle.ui.screens.library.genres

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.mediaprovider.SongImportState
import com.simplecityapps.mediaprovider.SongImportStateProvider
import com.simplecityapps.mediaprovider.repository.genres.GenreQuery
import com.simplecityapps.mediaprovider.repository.genres.GenreRepository
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.playback.PlaybackOperations
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.shuttle.model.Genre
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class GenreListUiState(
    val genres: List<Genre> = emptyList(),
    val loadingState: LoadingState = LoadingState.Loading,
    val scanProgress: Progress? = null,
) {
    enum class LoadingState { Loading, Scanning, Ready, Empty }
}

sealed interface GenreListUiEvent {
    data class AddedToQueue(val genreName: String) : GenreListUiEvent
    data class Error(val message: String) : GenreListUiEvent
    data class EditTags(val songs: List<Song>) : GenreListUiEvent
}

@HiltViewModel
class GenreListViewModel @Inject constructor(
    private val genreRepository: GenreRepository,
    private val songRepository: SongRepository,
    private val playbackManager: PlaybackOperations,
    private val queueManager: QueueOperations,
    mediaImportObserver: SongImportStateProvider
) : ViewModel() {

    val uiState: StateFlow<GenreListUiState> = combine(
        genreRepository.getGenres(GenreQuery.All()),
        mediaImportObserver.songImportState
    ) { genres, songImportState ->
        if (songImportState is SongImportState.ImportProgress) {
            GenreListUiState(
                loadingState = GenreListUiState.LoadingState.Scanning,
                scanProgress = songImportState.progress,
            )
        } else {
            GenreListUiState(
                genres = genres,
                loadingState = if (genres.isEmpty()) {
                    GenreListUiState.LoadingState.Empty
                } else {
                    GenreListUiState.LoadingState.Ready
                },
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = GenreListUiState(),
    )

    private val _events = MutableSharedFlow<GenreListUiEvent>()
    val events: SharedFlow<GenreListUiEvent> = _events.asSharedFlow()

    fun onPlay(genre: Genre) {
        viewModelScope.launch {
            val songs = getSongsForGenreOrEmpty(genre)
            if (queueManager.setQueue(songs)) {
                playbackManager.load { result ->
                    result.onSuccess { playbackManager.play() }
                    result.onFailure { error ->
                        viewModelScope.launch {
                            _events.emit(GenreListUiEvent.Error(error.message ?: "Unknown error"))
                        }
                    }
                }
            }
        }
    }

    fun onAddToQueue(genre: Genre) {
        viewModelScope.launch {
            val songs = getSongsForGenreOrEmpty(genre)
            playbackManager.addToQueue(songs)
            _events.emit(GenreListUiEvent.AddedToQueue(genre.name))
        }
    }

    fun onPlayNext(genre: Genre) {
        viewModelScope.launch {
            val songs = getSongsForGenreOrEmpty(genre)
            playbackManager.playNext(songs)
            _events.emit(GenreListUiEvent.AddedToQueue(genre.name))
        }
    }

    fun onExclude(genre: Genre) {
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

    fun onEditTags(genre: Genre) {
        viewModelScope.launch {
            val songs = getSongsForGenreOrEmpty(genre)
            _events.emit(GenreListUiEvent.EditTags(songs))
        }
    }

    private suspend fun getSongsForGenreOrEmpty(genre: Genre) = genreRepository.getSongsForGenre(genre.name, SongQuery.All())
        .firstOrNull()
        .orEmpty()
}
