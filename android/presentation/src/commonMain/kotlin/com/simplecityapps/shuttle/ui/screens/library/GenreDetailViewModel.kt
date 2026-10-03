package com.simplecityapps.shuttle.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.mediaprovider.repository.albums.AlbumQuery
import com.simplecityapps.mediaprovider.repository.genres.GenreQuery
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.Genre
import com.simplecityapps.shuttle.model.PlayContext
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.model.playContext
import com.simplecityapps.shuttle.query.SongQuery
import com.simplecityapps.shuttle.ui.actions.ObserveAlbums
import com.simplecityapps.shuttle.ui.actions.ObserveCurrentSong
import com.simplecityapps.shuttle.ui.actions.ObserveGenres
import com.simplecityapps.shuttle.ui.actions.ObserveSongsForGenre
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
) {
    /** What playing this screen's songs starts the queue from (#633). */
    val playContext: PlayContext get() = genre?.playContext ?: PlayContext.None
}

/** One genre's songs and the albums they come from, loaded by the genre's name. */
@OptIn(ExperimentalCoroutinesApi::class)
class GenreDetailViewModel @AssistedInject constructor(
    @Assisted genreName: String,
    observeGenres: ObserveGenres,
    observeSongsForGenre: ObserveSongsForGenre,
    observeAlbums: ObserveAlbums,
    observeCurrentSong: ObserveCurrentSong,
) : ViewModel() {
    @AssistedFactory
    @ManualViewModelAssistedFactoryKey(Factory::class)
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
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

    private val genre = observeGenres(GenreQuery.GenreName(genreName)).map { it.firstOrNull() }

    val uiState: StateFlow<GenreDetailUiState> = combine(
        genre,
        songsAndAlbums,
        observeCurrentSong(),
    ) { genre, (songs, albums), currentSong ->
        GenreDetailUiState(genre = genre, albums = albums, songs = songs, currentSong = currentSong, loading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GenreDetailUiState())
}
