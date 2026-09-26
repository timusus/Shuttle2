package com.simplecityapps.shuttle.ui.screens.library.albumartists.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.mediaprovider.repository.albums.AlbumQuery
import com.simplecityapps.mediaprovider.repository.artists.AlbumArtistQuery
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtistGroupKey
import com.simplecityapps.shuttle.model.AlbumGroupKey
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import com.simplecityapps.shuttle.ui.actions.ObserveAlbumArtists
import com.simplecityapps.shuttle.ui.actions.ObserveAlbums
import com.simplecityapps.shuttle.ui.actions.ObserveCurrentSong
import com.simplecityapps.shuttle.ui.actions.ObserveSongs
import com.simplecityapps.shuttle.ui.actions.ShuffleAlbums
import com.simplecityapps.shuttle.ui.common.PendingEvents
import com.simplecityapps.shuttle.ui.theme.ObserveArtworkSeed
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
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
    observeArtworkSeed: ObserveArtworkSeed,
    private val shuffleAlbums: ShuffleAlbums,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(groupKey: AlbumArtistGroupKey): AlbumArtistDetailViewModel
    }

    private val expandedAlbums = MutableStateFlow<Set<AlbumGroupKey>>(emptySet())
    private val events = PendingEvents<AlbumArtistDetailEvent>()

    /** The artist's albums, newest first, and their songs in that order; the lead song's artwork seeds the tint. */
    private val albumsAndSongs: Flow<Pair<List<Album>, List<Song>>> = combine(
        observeAlbums(AlbumQuery.ArtistGroupKey(groupKey)),
        observeSongs(SongQuery.ArtistGroupKeys(listOf(SongQuery.ArtistGroupKey(key = groupKey)))),
    ) { albums, songs ->
        val sortedAlbums = albums.sortedByDescending { it.year ?: 0 }
        val albumOrder = sortedAlbums.withIndex().associate { (index, album) -> album.groupKey to index }
        sortedAlbums to songs.sortedWith(compareBy({ albumOrder[it.albumGroupKey] ?: Int.MAX_VALUE }, { it.disc }, { it.track }))
    }.shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    val uiState: StateFlow<AlbumArtistDetailUiState> = combine(
        observeAlbumArtists(AlbumArtistQuery.AlbumArtistGroupKey(key = groupKey)),
        albumsAndSongs,
        observeCurrentSong(),
        combine(expandedAlbums, events.flow, ::Pair),
        observeArtworkSeed(albumsAndSongs.map { (_, songs) -> songs.firstOrNull() }),
    ) { artists, (albums, songs), currentSong, (expanded, events), seed ->
        AlbumArtistDetailUiState(
            albumArtist = artists.firstOrNull(),
            albums = albums,
            songs = songs,
            currentSong = currentSong,
            expandedAlbums = expanded.intersect(albums.mapNotNullTo(HashSet()) { it.groupKey }),
            loadingState = if (albums.isEmpty() && songs.isEmpty()) {
                AlbumArtistDetailUiState.LoadingState.Empty
            } else {
                AlbumArtistDetailUiState.LoadingState.Ready
            },
            events = events,
            seed = seed,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AlbumArtistDetailUiState(),
    )

    fun onAlbumClick(album: Album) {
        val key = album.groupKey ?: return
        // Drop keys of albums a rescan removed, so they don't re-expand if the album comes back
        val present = uiState.value.albums.mapNotNullTo(HashSet()) { it.groupKey }
        expandedAlbums.update { expanded ->
            val kept = expanded.intersect(present)
            if (key in kept) kept - key else kept + key
        }
    }

    fun onShuffleAlbums() {
        viewModelScope.launch {
            val songs = uiState.value.songs
            if (songs.isEmpty()) return@launch
            val result = shuffleAlbums(songs)
            if (result is ShuffleAlbums.Result.Failure) events.post(AlbumArtistDetailEvent.ShuffleAlbumsFailed(result.message))
        }
    }

    fun onEventHandled(id: Long) = events.consume(id)
}
