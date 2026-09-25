package com.simplecityapps.shuttle.ui.screens.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.mediaprovider.PlaylistExporter
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistQuery
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.PlaylistSong
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.sorting.PlaylistSongSortOrder
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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
    val songs: List<PlaylistSong> = emptyList(),
    /** [PlaylistSong.id]s of the entries in the current multi-selection. */
    val selectedIds: Set<Long> = emptySet(),
    val currentSong: Song? = null,
    val loading: Boolean = true,
) {
    /** Drag to reorder only makes sense while the list shows the playlist's own order. */
    val canReorder: Boolean get() = playlist != null && playlist.sortOrder == PlaylistSongSortOrder.Position && !playlist.sortDescending

    val selectedEntries: List<PlaylistSong> get() = songs.filter { it.id in selectedIds }
}

sealed interface PlaylistDetailEvent {
    data object Deleted : PlaylistDetailEvent

    data object ExportSucceeded : PlaylistDetailEvent

    data class ExportFailed(val error: String) : PlaylistDetailEvent

    /** Nothing to export: the export picker is not opened. */
    data object ExportEmpty : PlaylistDetailEvent

    /** The playlist has songs; the UI opens the file picker and hands the chosen Uri to [PlaylistDetailViewModel.exportTo]. */
    data class ExportReady(val suggestedName: String) : PlaylistDetailEvent
}

/**
 * One playlist, loaded by id: its songs in the playlist's sort, drag to reorder when sorted by position, a
 * multi-selection for batch removal, and the manage actions (sort, rename, clear, delete, export to m3u).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = PlaylistDetailViewModel.Factory::class)
class PlaylistDetailViewModel @AssistedInject constructor(
    @Assisted playlistId: Long,
    private val playlistRepository: PlaylistRepository,
    private val playlistExporter: PlaylistExporter,
    queueManager: QueueOperations,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(playlistId: Long): PlaylistDetailViewModel
    }

    private val _events = MutableSharedFlow<PlaylistDetailEvent>()
    val events: SharedFlow<PlaylistDetailEvent> = _events.asSharedFlow()

    private val selectedIds = MutableStateFlow(emptySet<Long>())

    /** The order the user is dragging into, shown until the repository emits the persisted order. */
    private val draggedOrder = MutableStateFlow<List<PlaylistSong>?>(null)

    private val playlist = playlistRepository.getPlaylists(PlaylistQuery.PlaylistId(playlistId)).map { it.firstOrNull() }

    private val songs = playlist
        .distinctUntilChanged { old, new -> old?.id == new?.id && old?.sortOrder == new?.sortOrder && old?.sortDescending == new?.sortDescending }
        .flatMapLatest { playlist -> playlist?.let { playlistRepository.getSongsForPlaylist(it) } ?: flowOf(emptyList()) }
        .onEach { draggedOrder.value = null }

    val uiState: StateFlow<PlaylistDetailUiState> = combine(
        playlist,
        combine(songs, draggedOrder) { persisted, dragged -> dragged ?: persisted },
        selectedIds,
        queueManager.queueStateFlow.map { it.currentItem?.song }.distinctUntilChanged(),
    ) { playlist, songs, selected, currentSong ->
        PlaylistDetailUiState(
            playlist = playlist,
            songs = songs,
            selectedIds = selected.filterTo(mutableSetOf()) { id -> songs.any { it.id == id } },
            currentSong = currentSong,
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlaylistDetailUiState())

    fun onToggleSelected(entry: PlaylistSong) {
        selectedIds.update { if (entry.id in it) it - entry.id else it + entry.id }
    }

    fun onClearSelection() {
        selectedIds.value = emptySet()
    }

    fun onSortOrderSelected(sortOrder: PlaylistSongSortOrder) {
        val playlist = uiState.value.playlist ?: return
        if (playlist.sortOrder == sortOrder) return
        viewModelScope.launch { playlistRepository.updatePlaylistSortOder(playlist, sortOrder, playlist.sortDescending) }
    }

    fun onSortDescendingChanged(descending: Boolean) {
        val playlist = uiState.value.playlist ?: return
        if (playlist.sortDescending == descending) return
        viewModelScope.launch { playlistRepository.updatePlaylistSortOder(playlist, playlist.sortOrder, descending) }
    }

    /** Moves the entry keyed [fromId] to where [toId] sits, on screen only; [onMoveFinished] persists the order. */
    fun onMove(fromId: Long, toId: Long) {
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
            _events.emit(PlaylistDetailEvent.Deleted)
        }
    }

    fun onExport() {
        val state = uiState.value
        val playlist = state.playlist ?: return
        viewModelScope.launch {
            _events.emit(if (state.songs.isEmpty()) PlaylistDetailEvent.ExportEmpty else PlaylistDetailEvent.ExportReady("${playlist.name}.m3u"))
        }
    }

    fun exportTo(uri: Uri) {
        val state = uiState.value
        val playlist = state.playlist ?: return
        viewModelScope.launch {
            val event = when (val result = playlistExporter.exportToUri(playlist.name, state.songs.map { it.song }, uri)) {
                is PlaylistExporter.ExportResult.Success -> PlaylistDetailEvent.ExportSucceeded
                is PlaylistExporter.ExportResult.Failure -> PlaylistDetailEvent.ExportFailed(result.error)
            }
            _events.emit(event)
        }
    }
}
