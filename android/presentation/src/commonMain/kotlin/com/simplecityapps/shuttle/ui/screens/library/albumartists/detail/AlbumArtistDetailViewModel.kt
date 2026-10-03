package com.simplecityapps.shuttle.ui.screens.library.albumartists.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.mediaprovider.repository.artists.AlbumArtistQuery
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtistGroupKey
import com.simplecityapps.shuttle.model.AlbumGroupKey
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import com.simplecityapps.shuttle.sorting.ArtistSongComparator
import com.simplecityapps.shuttle.sorting.ArtistSongSortOrder
import com.simplecityapps.shuttle.ui.actions.ObserveArtistAlbums
import com.simplecityapps.shuttle.ui.actions.ObserveArtists
import com.simplecityapps.shuttle.ui.actions.ObserveCurrentSong
import com.simplecityapps.shuttle.ui.actions.ObserveSongs
import com.simplecityapps.shuttle.ui.actions.ShuffleAlbums
import com.simplecityapps.shuttle.ui.common.PendingEvents
import com.simplecityapps.shuttle.ui.screens.library.LibraryViewSetting
import com.simplecityapps.shuttle.ui.screens.library.ReadLibraryViewSetting
import com.simplecityapps.shuttle.ui.screens.library.SaveLibraryViewSetting
import com.simplecityapps.shuttle.ui.screens.library.albumartists.detail.AlbumArtistDetailUiState.SongSection
import com.simplecityapps.shuttle.ui.theme.ObserveArtworkSeed
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One artist's albums, the albums they appear on, and their songs (#637), loaded by [groupKey], the key its route carries. Song and album
 * actions go through the screen's MediaActionsHost; this derives state, sorts and sections the songs (the sort is
 * app-wide, in [LibraryViewSetting.ArtistDetailSort]), unfolds albums and shuffles by album.
 */
class AlbumArtistDetailViewModel @AssistedInject constructor(
    @Assisted private val groupKey: AlbumArtistGroupKey,
    observeArtists: ObserveArtists,
    observeArtistAlbums: ObserveArtistAlbums,
    observeSongs: ObserveSongs,
    observeCurrentSong: ObserveCurrentSong,
    observeArtworkSeed: ObserveArtworkSeed,
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

    /** Null until the first load applies the default expansion, so a later rescan never reapplies it. */
    private val expandedAlbums = MutableStateFlow<Set<AlbumGroupKey>?>(null)
    private val events = PendingEvents<AlbumArtistDetailEvent>()

    /** The artist's own albums, newest first, and their songs (theirs and those crediting them) in that order; the lead song's artwork seeds the tint. */
    private val artistAlbums = observeArtistAlbums(groupKey).shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    private val albumsAndSongs: Flow<Pair<List<Album>, List<Song>>> = combine(
        artistAlbums.map { it.albums },
        observeSongs(SongQuery.ArtistGroupKeys(listOf(SongQuery.ArtistGroupKey(key = groupKey)))),
    ) { albums, songs ->
        val albumOrder = albums.withIndex().associate { (index, album) -> album.groupKey to index }
        albums to songs.sortedWith(compareBy<Song> { albumOrder[it.albumGroupKey] ?: Int.MAX_VALUE }.then(ArtistSongComparator.trackOrder))
    }.onEach { (albums, songs) ->
        if (albums.isNotEmpty() || songs.isNotEmpty()) expandedAlbums.compareAndSet(null, defaultExpansion(albums))
    }.shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    private val songList: Flow<SongList> = combine(albumsAndSongs, sortOrder) { (albums, songs), order ->
        val sections = songSections(albums, songs, order)
        SongList(
            order = order,
            sections = sections,
            songs = sections.flatMap { it.songs },
        )
    }

    val uiState: StateFlow<AlbumArtistDetailUiState> = combine(
        observeArtists(AlbumArtistQuery.AlbumArtistGroupKey(key = groupKey)),
        combine(albumsAndSongs, songList, artistAlbums) { albumsAndSongs, songList, artistAlbums -> Triple(albumsAndSongs, songList, artistAlbums.appearsOn) },
        observeCurrentSong(),
        combine(expandedAlbums, events.flow, ::Pair),
        observeArtworkSeed(albumsAndSongs.map { (_, songs) -> songs.firstOrNull() }),
    ) { artists, (albumsAndSongs, songList, appearsOn), currentSong, (expanded, events), seed ->
        val (albums, songs) = albumsAndSongs
        AlbumArtistDetailUiState(
            albumArtist = artists.firstOrNull(),
            albums = albums,
            appearsOn = appearsOn,
            songs = songList.songs,
            sortOrder = songList.order,
            sections = songList.sections,
            currentSong = currentSong,
            expandedAlbums = expanded.orEmpty().intersect(albums.mapNotNullTo(HashSet()) { it.groupKey }),
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

    fun onToggleAlbum(album: Album) {
        val key = album.groupKey ?: return
        // Drop keys of albums a rescan removed, so they don't re-expand if the album comes back
        val present = uiState.value.albums.mapNotNullTo(HashSet()) { it.groupKey }
        expandedAlbums.update { expanded ->
            val kept = expanded.orEmpty().intersect(present)
            if (key in kept) kept - key else kept + key
        }
    }

    fun onExpandAll() {
        expandedAlbums.value = uiState.value.albums.mapNotNullTo(HashSet()) { it.groupKey }
    }

    fun onCollapseAll() {
        expandedAlbums.value = emptySet()
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

    private data class SongList(val order: ArtistSongSortOrder, val sections: List<SongSection>, val songs: List<Song>)

    private companion object {
        /** At most this many albums start expanded; more start collapsed so the list stays scannable. */
        const val MAX_ALBUMS_EXPANDED_BY_DEFAULT = 2

        fun defaultExpansion(albums: List<Album>): Set<AlbumGroupKey> = if (albums.size <= MAX_ALBUMS_EXPANDED_BY_DEFAULT) {
            albums.mapNotNullTo(HashSet()) { it.groupKey }
        } else {
            emptySet()
        }

        /**
         * One section per album in [order] with its songs in track order, then the songs without one of [albums]
         * by title; or a single flat section for the flat orders.
         */
        fun songSections(albums: List<Album>, songs: List<Song>, order: ArtistSongSortOrder): List<SongSection> {
            if (songs.isEmpty()) return emptyList()
            val albumComparator = order.albumComparator ?: return listOf(SongSection(album = null, songs = songs.sortedWith(order.songComparator)))
            val songsByAlbum = songs.groupBy { it.albumGroupKey }
            val albumSections = albums.sortedWith(albumComparator).mapNotNull { album ->
                songsByAlbum[album.groupKey]?.let { SongSection(album, it.sortedWith(order.songComparator)) }
            }
            val albumKeys = albums.mapNotNullTo(HashSet()) { it.groupKey }
            val otherSongs = songs.filter { it.albumGroupKey !in albumKeys }.sortedWith(ArtistSongSortOrder.SongTitle.songComparator)
            return if (otherSongs.isEmpty()) albumSections else albumSections + SongSection(album = null, songs = otherSongs)
        }
    }
}
