package com.simplecityapps.shuttle.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.mediaprovider.repository.albums.AlbumQuery
import com.simplecityapps.mediaprovider.repository.genres.GenreQuery
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.Genre
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import com.simplecityapps.shuttle.ui.actions.ObserveAlbums
import com.simplecityapps.shuttle.ui.actions.ObserveGenres
import com.simplecityapps.shuttle.ui.actions.ObserveSongsForGenre
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class GenreDetailUiState(
    val genre: Genre? = null,
    /** The albums the genre's songs come from, by name. */
    val albums: List<Album> = emptyList(),
    val songs: List<Song> = emptyList(),
    val currentSong: Song? = null,
    val loading: Boolean = true,
)

/** One genre's songs and the albums they come from, loaded by the genre's name. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = GenreDetailViewModel.Factory::class)
class GenreDetailViewModel @AssistedInject constructor(
    @Assisted genreName: String,
    observeGenres: ObserveGenres,
    observeSongsForGenre: ObserveSongsForGenre,
    observeAlbums: ObserveAlbums,
    queueManager: QueueOperations,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(genreName: String): GenreDetailViewModel
    }

    private val songsAndAlbums = observeSongsForGenre(genreName, SongQuery.All())
        .flatMapLatest { songs ->
            val keys = songs.map { it.albumGroupKey }.distinct()
            if (keys.isEmpty()) {
                flowOf(songs to emptyList())
            } else {
                observeAlbums(AlbumQuery.AlbumGroupKeys(keys.map { AlbumQuery.AlbumGroupKey(it) }))
                    .map { albums -> songs to albums.sortedBy { it.name?.lowercase() } }
            }
        }

    val uiState: StateFlow<GenreDetailUiState> = combine(
        observeGenres(GenreQuery.GenreName(genreName)).map { it.firstOrNull() },
        songsAndAlbums,
        queueManager.queueStateFlow.map { it.currentItem?.song }.distinctUntilChanged(),
    ) { genre, (songs, albums), currentSong ->
        GenreDetailUiState(genre = genre, albums = albums, songs = songs, currentSong = currentSong, loading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GenreDetailUiState())
}
