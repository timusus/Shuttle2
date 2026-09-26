package com.simplecityapps.shuttle.ui.screens.library.albumartists.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.mediaprovider.repository.albums.AlbumQuery
import com.simplecityapps.mediaprovider.repository.artists.AlbumArtistQuery
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtistGroupKey
import com.simplecityapps.shuttle.model.AlbumGroupKey
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import com.simplecityapps.shuttle.ui.actions.ObserveAlbumArtists
import com.simplecityapps.shuttle.ui.actions.ObserveAlbums
import com.simplecityapps.shuttle.ui.actions.ObservePlaylists
import com.simplecityapps.shuttle.ui.actions.ObserveSongs
import com.simplecityapps.shuttle.ui.actions.ShuffleAlbums
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One album artist's albums and songs, loaded by [groupKey], the key its route carries. */
@HiltViewModel(assistedFactory = AlbumArtistDetailViewModel.Factory::class)
class AlbumArtistDetailViewModel @AssistedInject constructor(
    @Assisted private val groupKey: AlbumArtistGroupKey,
    private val observeAlbumArtists: ObserveAlbumArtists,
    private val observeAlbums: ObserveAlbums,
    private val observeSongs: ObserveSongs,
    private val queueOperations: QueueOperations,
    private val shuffleAlbums: ShuffleAlbums,
    private val observePlaylists: ObservePlaylists,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(groupKey: AlbumArtistGroupKey): AlbumArtistDetailViewModel
    }

    private val currentSong: Flow<Song?> = queueOperations.queueStateFlow
        .map { queueState -> queueState.currentItem?.song }
        .distinctUntilChanged()

    private val expandedAlbums = MutableStateFlow<Set<AlbumGroupKey>>(emptySet())

    val uiState: StateFlow<AlbumArtistDetailUiState> = combine(
        observeAlbumArtists(AlbumArtistQuery.AlbumArtistGroupKey(key = groupKey)),
        observeAlbums(AlbumQuery.ArtistGroupKey(groupKey)),
        observeSongs(SongQuery.ArtistGroupKeys(listOf(SongQuery.ArtistGroupKey(key = groupKey)))),
        observePlaylists(),
        currentSong,
    ) { artists, albums, songs, playlists, currentSong ->
        val sortedAlbums = albums.sortedByDescending { it.year ?: 0 }
        val albumOrder = sortedAlbums.withIndex().associate { (index, album) -> album.groupKey to index }
        val sortedSongs = songs.sortedWith(compareBy({ albumOrder[it.albumGroupKey] ?: Int.MAX_VALUE }, { it.disc }, { it.track }))
        AlbumArtistDetailUiState(
            albumArtist = artists.firstOrNull(),
            albums = sortedAlbums,
            songs = sortedSongs,
            playlists = playlists,
            currentSong = currentSong,
            loadingState = if (albums.isEmpty() && songs.isEmpty()) {
                AlbumArtistDetailUiState.LoadingState.Empty
            } else {
                AlbumArtistDetailUiState.LoadingState.Ready
            },
        )
    }.combine(expandedAlbums) { state, expanded ->
        state.copy(expandedAlbums = expanded)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AlbumArtistDetailUiState(),
    )

    /** Unfolds (or folds) the album's track list in place, rather than navigating away. */
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
            shuffleAlbums(songs)
        }
    }
}
