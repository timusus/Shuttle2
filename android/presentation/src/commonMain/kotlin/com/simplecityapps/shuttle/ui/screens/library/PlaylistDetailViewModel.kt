package com.simplecityapps.shuttle.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistQuery
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumGroupKey
import com.simplecityapps.shuttle.model.PlayContext
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.PlaylistSong
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.model.playContext
import com.simplecityapps.shuttle.sorting.PlaylistKind
import com.simplecityapps.shuttle.sorting.PlaylistSortKey
import com.simplecityapps.shuttle.sorting.SongListSort
import com.simplecityapps.shuttle.sorting.SongSection
import com.simplecityapps.shuttle.ui.actions.ExportPlaylist
import com.simplecityapps.shuttle.ui.actions.ObserveCurrentSong
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlaylistDetailUiState(
    val playlist: Playlist? = null,
    /** Every entry in [sort]'s order across all [sections], collapsed ones included: the play order. */
    val songs: List<PlaylistSong> = emptyList(),
    /** [PlaylistSong.id]s of the entries in the current multi-selection. */
    val selectedIds: Set<Long> = emptySet(),
    val currentSong: Song? = null,
    val loading: Boolean = true,
    val events: List<PendingEvent<PlaylistDetailEvent>> = emptyList(),
    /** A view-only sort; [SongListSort.PlaylistOrder] is the stored order, the only one that can be reordered. */
    val sort: SongListSort = SongListSort.PlaylistOrder,
    /** The song list as shown: one section per album for the album sorts, else a single section with no album. */
    val sections: List<SongSection> = emptyList(),
    val expandedAlbums: Set<AlbumGroupKey> = emptySet(),
) {
    /** Swift sees no default arguments, so the iOS screen and its tests keep building a stored-order state with this. */
    constructor(
        playlist: Playlist?,
        songs: List<PlaylistSong>,
        selectedIds: Set<Long>,
        currentSong: Song?,
        loading: Boolean,
        events: List<PendingEvent<PlaylistDetailEvent>>,
    ) : this(
        playlist = playlist,
        songs = songs,
        selectedIds = selectedIds,
        currentSong = currentSong,
        loading = loading,
        events = events,
        sections = if (songs.isEmpty()) emptyList() else listOf(SongSection(album = null, songs = songs.map { it.song })),
    )

    /** Drag to reorder only makes sense while the list shows the playlist's own order. */
    val canReorder: Boolean get() = playlist != null && sort == SongListSort.PlaylistOrder

    val allAlbumsExpanded: Boolean get() = sections.allAlbumsExpanded(expandedAlbums)

    val selectedEntries: List<PlaylistSong> get() = songs.filter { it.id in selectedIds }

    val playContext: PlayContext get() = playlist?.playContext ?: PlayContext.None
}

sealed interface PlaylistDetailEvent {
    data object Deleted : PlaylistDetailEvent

    data object ExportSucceeded : PlaylistDetailEvent

    data class ExportFailed(val error: String) : PlaylistDetailEvent

    /** Nothing to export: the export picker is not opened. */
    data object ExportEmpty : PlaylistDetailEvent

    /** The playlist has songs; the UI opens the file picker and hands the chosen Uri to [PlaylistDetailViewModel.exportTo]. */
    data class ExportReady(val suggestedName: String) : PlaylistDetailEvent

    /** Shuffle albums couldn't start playback; [reason] is the player's error, if it gave one. */
    data class ShuffleAlbumsFailed(val reason: String?) : PlaylistDetailEvent
}

/**
 * One playlist, loaded by id: its songs in a view sort saved per playlist ([PlaylistKind.User]), drag to
 * reorder in the stored order, a multi-selection for batch removal, and the manage actions (rename, clear,
 * delete, export to m3u).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistDetailViewModel @AssistedInject constructor(
    @Assisted playlistId: Long,
    private val playlistRepository: PlaylistRepository,
    private val exportPlaylist: ExportPlaylist,
    observeCurrentSong: ObserveCurrentSong,
    shuffleAlbums: ShuffleAlbums,
    readSetting: ReadLibraryViewSetting,
    saveSetting: SaveLibraryViewSetting,
) : ViewModel() {
    @AssistedFactory
    @ManualViewModelAssistedFactoryKey(Factory::class)
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(playlistId: Long): PlaylistDetailViewModel
    }

    private val events = PendingEvents<PlaylistDetailEvent>()

    private val selectedIds = MutableStateFlow(emptySet<Long>())

    /** The order the user is dragging into, shown until the repository emits the persisted order. */
    private val draggedOrder = MutableStateFlow<List<PlaylistSong>?>(null)

    private val playlist = playlistRepository.getPlaylists(PlaylistQuery.PlaylistId(playlistId)).map { it.firstOrNull() }

    /** The entries in their stored order, whatever sort the playlist row itself records. */
    private val persisted = playlist
        .distinctUntilChanged { old, new -> old?.id == new?.id }
        .flatMapLatest { playlist -> playlist?.let { playlistRepository.getSongsForPlaylist(it) } ?: flowOf(emptyList()) }
        .map { entries -> entries.sortedBy { it.sortOrder } }
        .onEach { draggedOrder.value = null }

    private val collection = SongCollection(
        items = combine(persisted, draggedOrder) { persisted, dragged -> dragged ?: persisted },
        songOf = { it.song },
        kind = PlaylistKind.User,
        key = PlaylistSortKey.user(playlistId),
        readSetting = readSetting,
        saveSetting = saveSetting,
        shuffleAlbums = shuffleAlbums,
    )

    val uiState: StateFlow<PlaylistDetailUiState> = combine(
        playlist,
        collection.view,
        selectedIds,
        observeCurrentSong(),
        events.flow,
    ) { playlist, view, selected, currentSong, events ->
        PlaylistDetailUiState(
            playlist = playlist,
            songs = view.items,
            selectedIds = selected.filterTo(mutableSetOf()) { id -> view.items.any { it.id == id } },
            currentSong = currentSong,
            loading = false,
            events = events,
            sort = view.sort,
            sections = view.sections,
            expandedAlbums = view.expandedAlbums,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlaylistDetailUiState())

    fun onSortSelected(sort: SongListSort) {
        draggedOrder.value = null
        collection.select(sort)
    }

    fun onToggleAlbum(album: Album) {
        collection.toggleAlbum(album, uiState.value.sections)
    }

    fun onExpandAll() {
        collection.expandAll(uiState.value.sections)
    }

    fun onCollapseAll() {
        collection.collapseAll()
    }

    fun onShuffleAlbums() {
        viewModelScope.launch {
            val failure = collection.shuffle(uiState.value.songs.map { it.song }) ?: return@launch
            events.post(PlaylistDetailEvent.ShuffleAlbumsFailed(failure.message))
        }
    }

    fun onToggleSelected(entry: PlaylistSong) {
        selectedIds.update { if (entry.id in it) it - entry.id else it + entry.id }
    }

    fun onClearSelection() {
        selectedIds.value = emptySet()
    }

    /** Moves the entry keyed [fromId] to where [toId] sits, on screen only; [onMoveFinished] persists the order. */
    fun onMove(fromId: Long, toId: Long) {
        if (!uiState.value.canReorder) return
        val current = draggedOrder.value ?: uiState.value.songs
        val from = current.indexOfFirst { it.id == fromId }
        val to = current.indexOfFirst { it.id == toId }
        if (from < 0 || to < 0 || from == to) return
        draggedOrder.value = current.toMutableList().apply { add(to, removeAt(from)) }
    }

    fun onMoveFinished() {
        val playlist = uiState.value.playlist ?: return
        val order = draggedOrder.value ?: return
        val renumbered = order.mapIndexed { index, entry -> PlaylistSong(entry.id, index.toLong(), entry.song) }
        draggedOrder.value = renumbered
        viewModelScope.launch { playlistRepository.updatePlaylistSongsSortOder(playlist, renumbered) }
    }

    fun onRename(name: String) {
        val playlist = uiState.value.playlist ?: return
        viewModelScope.launch { playlistRepository.renamePlaylist(playlist, name) }
    }

    fun onClear() {
        val playlist = uiState.value.playlist ?: return
        viewModelScope.launch { playlistRepository.clearPlaylist(playlist) }
    }

    fun onDelete() {
        val playlist = uiState.value.playlist ?: return
        viewModelScope.launch {
            playlistRepository.deletePlaylist(playlist)
            events.post(PlaylistDetailEvent.Deleted)
        }
    }

    fun onExport() {
        val state = uiState.value
        val playlist = state.playlist ?: return
        events.post(if (state.songs.isEmpty()) PlaylistDetailEvent.ExportEmpty else PlaylistDetailEvent.ExportReady("${playlist.name}.m3u"))
    }

    fun exportTo(destination: String) {
        val state = uiState.value
        val playlist = state.playlist ?: return
        viewModelScope.launch {
            val event = when (val result = exportPlaylist(playlist.name, state.songs.map { it.song }, destination)) {
                is ExportPlaylist.Result.Success -> PlaylistDetailEvent.ExportSucceeded
                is ExportPlaylist.Result.Failure -> PlaylistDetailEvent.ExportFailed(result.message)
            }
            events.post(event)
        }
    }

    fun onEventHandled(id: Long) = events.consume(id)
}
