package com.simplecityapps.shuttle.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import com.simplecityapps.shuttle.ui.actions.ObserveCurrentSong
import com.simplecityapps.shuttle.ui.actions.ObserveSmartPlaylistSongs
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
    observeSmartPlaylistSongs: ObserveSmartPlaylistSongs,
    observeCurrentSong: ObserveCurrentSong,
    shuffleAlbums: ShuffleAlbums,
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

    private val events = PendingEvents<SmartPlaylistDetailEvent>()

    private val collection = smartPlaylist?.let { playlist ->
        SongCollection(
            items = observeSmartPlaylistSongs(playlist.id),
            songOf = { it },
            kind = PlaylistKind.of(playlist.id),
            key = PlaylistSortKey.of(playlist.id),
            readSetting = readSetting,
            saveSetting = saveSetting,
            shuffleAlbums = shuffleAlbums,
        )
    }

    private val view = collection?.view
        ?: flowOf(SongCollection.View(emptyList(), emptyList<Song>(), SongListSort.PlaylistOrder, emptySet()))

    val uiState: StateFlow<SmartPlaylistDetailUiState> = combine(
        view,
        observeCurrentSong(),
        events.flow,
    ) { view, currentSong, events ->
        SmartPlaylistDetailUiState(
            smartPlaylist = smartPlaylist,
            songs = view.items,
            currentSong = currentSong,
            loading = false,
            sort = view.sort,
            sections = view.sections,
            expandedAlbums = view.expandedAlbums,
            events = events,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SmartPlaylistDetailUiState())

    fun onSortSelected(sort: SongListSort) {
        collection?.select(sort)
    }

    fun onToggleAlbum(album: Album) {
        collection?.toggleAlbum(album, uiState.value.sections)
    }

    fun onExpandAll() {
        collection?.expandAll(uiState.value.sections)
    }

    fun onCollapseAll() {
        collection?.collapseAll()
    }

    fun onShuffleAlbums() {
        val collection = collection ?: return
        viewModelScope.launch {
            val failure = collection.shuffle(uiState.value.songs) ?: return@launch
            events.post(SmartPlaylistDetailEvent.ShuffleAlbumsFailed(failure.message))
        }
    }

    fun onEventHandled(id: Long) = events.consume(id)
}
