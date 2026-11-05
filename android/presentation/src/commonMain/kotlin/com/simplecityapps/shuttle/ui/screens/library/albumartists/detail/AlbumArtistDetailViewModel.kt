package com.simplecityapps.shuttle.ui.screens.library.albumartists.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.mediaprovider.repository.artists.AlbumArtistQuery
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.AlbumArtistGroupKey
import com.simplecityapps.shuttle.model.ArtistHeroArtwork
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import com.simplecityapps.shuttle.sorting.ArtistSongComparator
import com.simplecityapps.shuttle.sorting.ArtistSongSortOrder
import com.simplecityapps.shuttle.sorting.songSections
import com.simplecityapps.shuttle.ui.actions.ObserveArtistAlbums
import com.simplecityapps.shuttle.ui.actions.ObserveArtists
import com.simplecityapps.shuttle.ui.actions.ObserveCurrentSong
import com.simplecityapps.shuttle.ui.actions.ObserveSongs
import com.simplecityapps.shuttle.ui.actions.ShuffleAlbums
import com.simplecityapps.shuttle.ui.common.PendingEvents
import com.simplecityapps.shuttle.ui.screens.library.AlbumExpansion
import com.simplecityapps.shuttle.ui.screens.library.LibraryViewSetting
import com.simplecityapps.shuttle.ui.screens.library.ReadLibraryViewSetting
import com.simplecityapps.shuttle.ui.screens.library.SaveLibraryViewSetting
import com.simplecityapps.shuttle.ui.screens.library.albumartists.detail.AlbumArtistDetailUiState.SongSection
import com.simplecityapps.shuttle.ui.theme.ObserveArtistArtworkSeed
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Song and album actions go through the screen's MediaActionsHost; this class only derives state. */
class AlbumArtistDetailViewModel @AssistedInject constructor(
    @Assisted private val groupKey: AlbumArtistGroupKey,
    observeArtists: ObserveArtists,
    observeArtistAlbums: ObserveArtistAlbums,
    observeSongs: ObserveSongs,
    observeCurrentSong: ObserveCurrentSong,
    observeArtistArtworkSeed: ObserveArtistArtworkSeed,
    private val shuffleAlbums: ShuffleAlbums,
    readSetting: ReadLibraryViewSetting,
    private val saveSetting: SaveLibraryViewSetting,
) : ViewModel() {

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey(Factory::class)
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(groupKey: AlbumArtistGroupKey): AlbumArtistDetailViewModel
    }

    private val sortOrder = MutableStateFlow(readSetting(LibraryViewSetting.ArtistDetailSort))

    private val expandedAlbums = AlbumExpansion()
    private val events = PendingEvents<AlbumArtistDetailEvent>()

    /** The artist's own albums, newest first, and their songs (theirs and those crediting them) in that order. */
    private val artistAlbums = observeArtistAlbums(groupKey).shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    private val albumsAndSongs: Flow<Pair<List<Album>, List<Song>>> = combine(
        artistAlbums.map { it.albums },
        observeSongs(SongQuery.ArtistGroupKeys(listOf(SongQuery.ArtistGroupKey(key = groupKey)))),
    ) { albums, songs ->
        val albumOrder = albums.withIndex().associate { (index, album) -> album.groupKey to index }
        albums to songs.sortedWith(compareBy<Song> { albumOrder[it.albumGroupKey] ?: Int.MAX_VALUE }.then(ArtistSongComparator.trackOrder))
    }.onEach { (albums, songs) ->
        if (albums.isNotEmpty() || songs.isNotEmpty()) expandedAlbums.onLoaded(albums)
    }.shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    private val songList: Flow<SongList> = combine(albumsAndSongs, sortOrder) { (albums, songs), order ->
        val sections = songSections(songs, order.sort, albums).map { SongSection(it.album, it.songs) }
        SongList(
            order = order,
            sections = sections,
            songs = sections.flatMap { it.songs },
        )
    }

    private val artist: Flow<AlbumArtist?> = observeArtists(AlbumArtistQuery.AlbumArtistGroupKey(key = groupKey))
        .map { it.firstOrNull() }
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    private val hero: Flow<ArtistHeroArtwork?> = combine(artist, albumsAndSongs, artistAlbums) { artist, (albums, songs), artistAlbums ->
        artist?.let { ArtistHeroArtwork.of(it, albums, songs, artistAlbums.appearsOn) }
    }.shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    val uiState: StateFlow<AlbumArtistDetailUiState> = combine(
        combine(artist, hero, ::Pair),
        combine(albumsAndSongs, songList, artistAlbums) { albumsAndSongs, songList, artistAlbums -> Triple(albumsAndSongs, songList, artistAlbums.appearsOn) },
        observeCurrentSong(),
        combine(expandedAlbums.flow, events.flow, ::Pair),
        // Seeds the tint from the hero's image, not the artist's own.
        observeArtistArtworkSeed(hero),
    ) { (albumArtist, hero), (albumsAndSongs, songList, appearsOn), currentSong, (expanded, events), seed ->
        val (albums, songs) = albumsAndSongs
        AlbumArtistDetailUiState(
            albumArtist = albumArtist,
            hero = hero,
            albums = albums,
            appearsOn = appearsOn,
            songs = songList.songs,
            sortOrder = songList.order,
            sections = songList.sections,
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

    fun onSortOrderSelected(order: ArtistSongSortOrder) {
        sortOrder.value = order
        saveSetting(LibraryViewSetting.ArtistDetailSort, order)
    }

    fun onToggleAlbum(album: Album) = expandedAlbums.toggle(album, uiState.value.albums)

    fun onExpandAll() = expandedAlbums.expandAll(uiState.value.albums)

    fun onCollapseAll() = expandedAlbums.collapseAll()

    fun onShuffleAlbums() {
        viewModelScope.launch {
            val songs = uiState.value.songs
            if (songs.isEmpty()) return@launch
            val result = shuffleAlbums(songs)
            if (result is ShuffleAlbums.Result.Failure) events.post(AlbumArtistDetailEvent.ShuffleAlbumsFailed(result.message))
        }
    }

    fun onEventHandled(id: Long) = events.consume(id)

    private data class SongList(val order: ArtistSongSortOrder, val sections: List<SongSection>, val songs: List<Song>)
}
