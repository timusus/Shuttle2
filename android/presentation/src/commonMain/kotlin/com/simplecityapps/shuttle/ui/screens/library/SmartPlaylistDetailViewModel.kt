package com.simplecityapps.shuttle.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.mediaprovider.repository.songs.comparator
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumGroupKey
import com.simplecityapps.shuttle.model.PlayContext
import com.simplecityapps.shuttle.model.SmartPlaylist
import com.simplecityapps.shuttle.model.SmartPlaylistId
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.model.playContext
import com.simplecityapps.shuttle.sorting.PlaylistKind
import com.simplecityapps.shuttle.sorting.PlaylistSortKey
import com.simplecityapps.shuttle.sorting.SongListSort
import com.simplecityapps.shuttle.sorting.SongSection
import com.simplecityapps.shuttle.sorting.albumsOf
import com.simplecityapps.shuttle.sorting.songSections
import com.simplecityapps.shuttle.ui.actions.ObserveCurrentSong
import com.simplecityapps.shuttle.ui.actions.ObserveDownloadedSongs
import com.simplecityapps.shuttle.ui.actions.ObserveSongs
import com.simplecityapps.shuttle.ui.actions.ShuffleAlbums
import com.simplecityapps.shuttle.ui.common.PendingEvent
import com.simplecityapps.shuttle.ui.common.PendingEvents
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SmartPlaylistDetailUiState(
    val smartPlaylist: SmartPlaylist? = null,
    /** Every song in [sort]'s order across all [sections], collapsed ones included: the play order. */
    val songs: List<Song> = emptyList(),
    val currentSong: Song? = null,
    val loading: Boolean = true,
    val sort: SongListSort = SongListSort.PlaylistOrder,
    /** The song list as shown: one section per album for the album sorts, else a single section with no album. */
    val sections: List<SongSection> = emptyList(),
    val expandedAlbums: Set<AlbumGroupKey> = emptySet(),
    val events: List<PendingEvent<SmartPlaylistDetailEvent>> = emptyList(),
) {
    /** Swift sees no default arguments, so the iOS screen and its tests keep building a flat state with this. */
    constructor(smartPlaylist: SmartPlaylist?, songs: List<Song>, currentSong: Song?, loading: Boolean) : this(
        smartPlaylist = smartPlaylist,
        songs = songs,
        currentSong = currentSong,
        loading = loading,
        sections = if (songs.isEmpty()) emptyList() else listOf(SongSection(album = null, songs = songs)),
    )

    val kind: PlaylistKind? get() = smartPlaylist?.id?.let(PlaylistKind::of)

    val playContext: PlayContext get() = smartPlaylist?.playContext ?: PlayContext.None

    val allAlbumsExpanded: Boolean get() = sections.allAlbumsExpanded(expandedAlbums)
}

sealed interface SmartPlaylistDetailEvent {
    /** Shuffle albums couldn't start playback; [reason] is the player's error, if it gave one. */
    data class ShuffleAlbumsFailed(val reason: String?) : SmartPlaylistDetailEvent
}

/**
 * One of the built-in smart playlists, resolved from its [SmartPlaylistId] slug: its songs in the view sort
 * chosen from its [PlaylistKind], where [SongListSort.PlaylistOrder] is the query's own sort.
 */
class SmartPlaylistDetailViewModel @AssistedInject constructor(
    @Assisted smartPlaylistId: String,
    observeSongs: ObserveSongs,
    observeDownloadedSongs: ObserveDownloadedSongs,
    observeCurrentSong: ObserveCurrentSong,
    private val shuffleAlbums: ShuffleAlbums,
    readSetting: ReadLibraryViewSetting,
    saveSetting: SaveLibraryViewSetting,
) : ViewModel() {
    @AssistedFactory
    @ManualViewModelAssistedFactoryKey(Factory::class)
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(smartPlaylistId: String): SmartPlaylistDetailViewModel
    }

    private val smartPlaylist = SmartPlaylistId.fromId(smartPlaylistId)?.smartPlaylist

    private val options = smartPlaylist?.let { PlaylistViewOptions(PlaylistKind.of(it.id), PlaylistSortKey.of(it.id), readSetting, saveSetting) }

    private val events = PendingEvents<SmartPlaylistDetailEvent>()

    private val songs = smartPlaylist
        ?.let { playlist ->
            val songs = if (playlist.id == SmartPlaylistId.Downloaded) observeDownloadedSongs() else observeSongs(playlist.songQuery)
            songs.map { it.sortedWith(playlist.songQuery.sortOrder.comparator) }
                .onEach { if (it.isNotEmpty()) options?.expansion?.onLoaded(albumsOf(it)) }
        }
        ?: flowOf(emptyList())

    val uiState: StateFlow<SmartPlaylistDetailUiState> = combine(
        songs,
        options?.sort ?: flowOf(SongListSort.PlaylistOrder),
        options?.expansion?.flow ?: flowOf(emptySet()),
        observeCurrentSong(),
        events.flow,
    ) { songs, sort, expanded, currentSong, events ->
        val sections = songSections(songs, sort)
        SmartPlaylistDetailUiState(
            smartPlaylist = smartPlaylist,
            songs = sections.flatMap { it.songs },
            currentSong = currentSong,
            loading = false,
            sort = sort,
            sections = sections,
            expandedAlbums = sections.expandedAmong(expanded),
            events = events,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SmartPlaylistDetailUiState())

    fun onSortSelected(sort: SongListSort) {
        options?.select(sort)
    }

    fun onToggleAlbum(album: Album) {
        options?.expansion?.toggle(album, uiState.value.sections.mapNotNull { it.album })
    }

    fun onExpandAll() {
        options?.expansion?.expandAll(uiState.value.sections.mapNotNull { it.album })
    }

    fun onCollapseAll() {
        options?.expansion?.collapseAll()
    }

    fun onShuffleAlbums() {
        viewModelScope.launch {
            val songs = uiState.value.songs
            if (songs.isEmpty()) return@launch
            val result = shuffleAlbums(songs)
            if (result is ShuffleAlbums.Result.Failure) events.post(SmartPlaylistDetailEvent.ShuffleAlbumsFailed(result.message))
        }
    }

    fun onEventHandled(id: Long) = events.consume(id)
}
