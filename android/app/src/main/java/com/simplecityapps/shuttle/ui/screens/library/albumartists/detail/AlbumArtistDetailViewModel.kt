package com.simplecityapps.shuttle.ui.screens.library.albumartists.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.mediaprovider.repository.albums.AlbumQuery
import com.simplecityapps.mediaprovider.repository.artists.AlbumArtistQuery
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtistGroupKey
import com.simplecityapps.shuttle.model.AlbumGroupKey
import com.simplecityapps.shuttle.query.SongQuery
import com.simplecityapps.shuttle.ui.actions.ObserveAlbumArtists
import com.simplecityapps.shuttle.ui.actions.ObserveAlbums
import com.simplecityapps.shuttle.ui.actions.ObserveCurrentSong
import com.simplecityapps.shuttle.ui.actions.ObserveSongs
import com.simplecityapps.shuttle.ui.actions.ShuffleAlbums
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One album artist's albums and songs, loaded by [groupKey], the key its route carries. Song and album
 * actions go through the screen's MediaActionsHost; this derives state, unfolds albums and shuffles by album.
 */
@HiltViewModel(assistedFactory = AlbumArtistDetailViewModel.Factory::class)
class AlbumArtistDetailViewModel @AssistedInject constructor(
    @Assisted private val groupKey: AlbumArtistGroupKey,
    observeAlbumArtists: ObserveAlbumArtists,
    observeAlbums: ObserveAlbums,
    observeSongs: ObserveSongs,
    observeCurrentSong: ObserveCurrentSong,
    private val shuffleAlbums: ShuffleAlbums,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(groupKey: AlbumArtistGroupKey): AlbumArtistDetailViewModel
    }

    private val expandedAlbums = MutableStateFlow<Set<AlbumGroupKey>>(emptySet())

    val uiState: StateFlow<AlbumArtistDetailUiState> = combine(
        observeAlbumArtists(AlbumArtistQuery.AlbumArtistGroupKey(key = groupKey)),
        observeAlbums(AlbumQuery.ArtistGroupKey(groupKey)),
        observeSongs(SongQuery.ArtistGroupKeys(listOf(SongQuery.ArtistGroupKey(key = groupKey)))),
        observeCurrentSong(),
        expandedAlbums,
    ) { artists, albums, songs, currentSong, expanded ->
        val sortedAlbums = albums.sortedByDescending { it.year ?: 0 }
        val albumOrder = sortedAlbums.withIndex().associate { (index, album) -> album.groupKey to index }
        val sortedSongs = songs.sortedWith(compareBy({ albumOrder[it.albumGroupKey] ?: Int.MAX_VALUE }, { it.disc }, { it.track }))
        AlbumArtistDetailUiState(
            albumArtist = artists.firstOrNull(),
            albums = sortedAlbums,
            songs = sortedSongs,
            currentSong = currentSong,
            expandedAlbums = expanded,
            loadingState = if (albums.isEmpty() && songs.isEmpty()) {
                AlbumArtistDetailUiState.LoadingState.Empty
            } else {
                AlbumArtistDetailUiState.LoadingState.Ready
            },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AlbumArtistDetailUiState(),
    )

    fun onAlbumClick(album: Album) {
        val key = album.groupKey ?: return
        expandedAlbums.update { expanded ->
            if (key in expanded) expanded - key else expanded + key
        }
    }

    fun onShuffleAlbums() {
        viewModelScope.launch {
            val songs = uiState.value.songs
            if (songs.isEmpty()) return@launch
            // A failure isn't shown: nothing ever collected the event this used to emit.
            shuffleAlbums(songs)
        }
    }
}
