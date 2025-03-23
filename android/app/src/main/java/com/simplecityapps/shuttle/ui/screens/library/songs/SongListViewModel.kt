package com.simplecityapps.shuttle.ui.screens.library.songs

import androidx.annotation.OpenForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.mediaprovider.MediaImportObserver
import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.mediaprovider.SongImportState
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import com.simplecityapps.shuttle.ui.screens.library.SortPreferenceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

@OpenForTesting
@HiltViewModel
class SongListViewModel @Inject constructor(
    private val songRepository: SongRepository,
    private val playbackManager: PlaybackManager,
    private val sortPreferenceManager: SortPreferenceManager,
    mediaImportObserver: MediaImportObserver
) : ViewModel() {
    private val _viewState = MutableStateFlow<ViewState>(ViewState.Loading)
    val viewState = _viewState.asStateFlow()

    init {
        combine(
            songRepository
                .getSongs(SongQuery.All(sortOrder = sortPreferenceManager.sortOrderSongList))
                .filterNotNull(),
            mediaImportObserver.songImportState,
        ) { songs, songImportState ->
            if (songImportState is SongImportState.ImportProgress) {
                _viewState.emit(ViewState.Scanning(songImportState.progress))
            } else {
                _viewState.emit(ViewState.Ready(songs))
            }
        }
            .onStart {
                _viewState.emit(ViewState.Loading)
            }
            .launchIn(viewModelScope)
    }

    fun addToQueue(song: Song, completion: (Result<Song>) -> Unit) {
        viewModelScope.launch {
            playbackManager.addToQueue(listOf(song))
            completion(Result.success(song))
        }
    }

    fun playNext(song: Song, completion: (Result<Song>) -> Unit) {
        viewModelScope.launch {
            playbackManager.playNext(listOf(song))
            completion(Result.success(song))
        }
    }

    sealed class ViewState {
        data class Scanning(val progress: Progress?) : ViewState()
        data object Loading : ViewState()
        data class Ready(val songs: List<Song>) : ViewState()
    }
}
