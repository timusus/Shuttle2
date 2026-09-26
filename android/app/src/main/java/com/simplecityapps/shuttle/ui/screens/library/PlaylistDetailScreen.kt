package com.simplecityapps.shuttle.ui.screens.library

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistRemove
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.ArtworkPlaceholder
import com.simplecityapps.shuttle.designsystem.component.ArtworkSize
import com.simplecityapps.shuttle.designsystem.component.QueuePosition
import com.simplecityapps.shuttle.designsystem.component.QueueRow
import com.simplecityapps.shuttle.designsystem.component.S2Action
import com.simplecityapps.shuttle.designsystem.component.S2IconButton
import com.simplecityapps.shuttle.designsystem.component.S2Menu
import com.simplecityapps.shuttle.designsystem.component.S2SelectionToolbar
import com.simplecityapps.shuttle.designsystem.component.formatDuration
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.PlaylistSong
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.sorting.PlaylistSongSortOrder
import com.simplecityapps.shuttle.ui.actions.MediaAction
import com.simplecityapps.shuttle.ui.actions.MediaActionType
import com.simplecityapps.shuttle.ui.actions.MediaSelection
import com.simplecityapps.shuttle.ui.actions.NavigationTarget
import com.simplecityapps.shuttle.ui.common.ConsumeEvents
import com.simplecityapps.shuttle.ui.common.mediaactions.MediaActionsHost
import com.simplecityapps.shuttle.ui.common.mediaactions.MediaActionsTarget
import com.simplecityapps.shuttle.ui.shell.LocalShellSnackbarHostState
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/** The sorts a playlist offers, in menu order; Position is the playlist's own order, labelled Custom. */
private val PlaylistSorts = listOf(
    PlaylistSongSortOrder.Position to R.string.menu_title_sort_custom,
    PlaylistSongSortOrder.SongName to R.string.menu_title_sort_song_name,
    PlaylistSongSortOrder.ArtistGroupKey to R.string.menu_title_sort_artist_name,
    PlaylistSongSortOrder.AlbumGroupKey to R.string.menu_title_sort_album_name,
    PlaylistSongSortOrder.Year to R.string.menu_title_sort_year,
    PlaylistSongSortOrder.Duration to R.string.menu_title_sort_duration,
    PlaylistSongSortOrder.LastModified to R.string.menu_title_sort_date_modified,
)

/**
 * Playlist detail (inventory §1): the playlist's songs in its sort. Sorted by Custom, each row has a drag handle;
 * otherwise a hint says how to get back to reordering. Long press starts a multi-selection whose toolbar removes
 * the entries (with Undo) or hands them to the shared actions. Sort, export and rename / clear / delete sit in the
 * top bar.
 */
@Composable
fun PlaylistDetailScreen(
    uiState: PlaylistDetailUiState,
    onNavigateUp: () -> Unit,
    onPlay: (songs: List<Song>, index: Int) -> Unit,
    onShuffle: () -> Unit,
    onPlaylistMore: (Playlist) -> Unit,
    onSongMore: (PlaylistSong) -> Unit,
    onToggleSelected: (PlaylistSong) -> Unit,
    onClearSelection: () -> Unit,
    onRemoveSelected: () -> Unit,
    onSelectionAction: (MediaActionType) -> Unit,
    onSortOrderSelected: (PlaylistSongSortOrder) -> Unit,
    onSortDescendingChanged: (Boolean) -> Unit,
    onExport: () -> Unit,
    onMove: (fromId: Long, toId: Long) -> Unit,
    onMoveFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playlist = uiState.playlist
    val state = when {
        uiState.loading -> DetailContentState.Loading
        playlist == null -> DetailContentState.NotFound
        else -> DetailContentState.Ready
    }
    val selecting = uiState.selectedIds.isNotEmpty()
    // Reordering needs the playlist's own order, and pauses while a selection is open so rows can show it.
    val reorderable = uiState.canReorder && !selecting
    val songs = uiState.songs.map { it.song }
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fromId = from.key as? Long
        val toId = to.key as? Long
        if (fromId != null && toId != null) onMove(fromId, toId)
    }
    BackHandler(enabled = selecting, onBack = onClearSelection)

    Box(modifier.testTag("playlist-detail")) {
        LibraryDetailScaffold(
            state = state,
            title = playlist?.name ?: stringResource(com.simplecityapps.core.R.string.unknown),
            subtitle = playlist?.let { listOf(pluralString(R.plurals.songsPlural, uiState.songs.size), formatDuration(songs.sumOf { it.duration }.toLong())).joinToString(" · ") },
            artwork = null,
            placeholder = ArtworkPlaceholder.Playlist,
            onNavigateUp = onNavigateUp,
            onPlay = { onPlay(songs, 0) },
            onShuffle = onShuffle,
            onMore = { playlist?.let(onPlaylistMore) },
            listState = listState,
            actions = {
                if (playlist != null) {
                    PlaylistSortMenu(playlist, onSortOrderSelected, onSortDescendingChanged, onExport)
                }
            },
            header = {
                if (playlist != null && !uiState.canReorder && uiState.songs.isNotEmpty()) {
                    val sortLabel = stringResource(PlaylistSorts.firstOrNull { it.first == playlist.sortOrder }?.second ?: R.string.menu_title_sort_custom)
                    Text(
                        text = stringResource(R.string.playlist_detail_reorder_hint, sortLabel),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp).testTag("playlist-reorder-hint"),
                    )
                }
            },
        ) {
            uiState.songs.forEachIndexed { index, entry ->
                item(key = entry.id, contentType = "song") {
                    ReorderableItem(reorderState, key = entry.id, enabled = reorderable) { dragging ->
                        val song = entry.song
                        val playing = song.id == uiState.currentSong?.id
                        if (reorderable) {
                            QueueRow(
                                title = song.name.orEmpty(),
                                subtitle = song.rowSubtitle,
                                onClick = { onPlay(songs, index) },
                                position = if (playing) QueuePosition.Current else QueuePosition.Upcoming,
                                artwork = { LibraryArtwork(song, ArtworkPlaceholder.Song, size = ArtworkSize.Small) },
                                duration = formatDuration(song.duration.toLong()),
                                dragging = dragging,
                                dragHandleModifier = Modifier.draggableHandle(onDragStopped = onMoveFinished),
                                onLongClick = { onToggleSelected(entry) },
                            )
                        } else {
                            LibrarySongRow(
                                song = song,
                                onClick = { if (selecting) onToggleSelected(entry) else onPlay(songs, index) },
                                selected = entry.id in uiState.selectedIds,
                                playing = playing,
                                onLongClick = { onToggleSelected(entry) },
                                onMore = if (selecting) null else ({ onSongMore(entry) }),
                            )
                        }
                    }
                }
            }
        }

        if (selecting) {
            S2SelectionToolbar(
                selectedCount = uiState.selectedIds.size,
                onClearSelection = onClearSelection,
                actions = listOf(
                    S2Action(stringResource(R.string.playlist_detail_remove), onRemoveSelected, Icons.Rounded.PlaylistRemove),
                    selectionAction(MediaActionType.Play, Icons.Rounded.PlayArrow, onSelectionAction),
                    selectionAction(MediaActionType.AddToQueue, Icons.AutoMirrored.Rounded.QueueMusic, onSelectionAction),
                ),
                overflowActions = listOf(
                    listOf(
                        selectionAction(MediaActionType.PlayNext, Icons.Rounded.SkipNext, onSelectionAction),
                        selectionAction(MediaActionType.Shuffle, Icons.Rounded.Shuffle, onSelectionAction),
                        selectionAction(MediaActionType.AddToPlaylist, Icons.AutoMirrored.Rounded.PlaylistAdd, onSelectionAction),
                    ),
                    // Batch tag editing, when every selected song's provider can write tags.
                    listOfNotNull(
                        selectionAction(MediaActionType.EditTags, Icons.Rounded.Edit, onSelectionAction)
                            .takeIf { uiState.selectedEntries.let { entries -> entries.isNotEmpty() && entries.all { it.song.mediaProvider.supportsTagEditing } } },
                    ),
                ).filter { it.isNotEmpty() },
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 16.dp),
            )
        }
    }
}

/** The top bar's sort button: the seven sorts, Descending, and Export as m3u. */
@Composable
private fun PlaylistSortMenu(
    playlist: Playlist,
    onSortOrderSelected: (PlaylistSongSortOrder) -> Unit,
    onSortDescendingChanged: (Boolean) -> Unit,
    onExport: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        S2IconButton(icon = Icons.AutoMirrored.Rounded.Sort, contentDescription = stringResource(R.string.menu_title_sort_by), onClick = { open = true })
        S2Menu(
            expanded = open,
            onDismissRequest = { open = false },
            groups = listOf(
                sortOptions(playlist.sortOrder, PlaylistSorts) { onSortOrderSelected(it) },
                listOf(S2Action(stringResource(R.string.menu_title_sort_descending), { onSortDescendingChanged(!playlist.sortDescending) }, selected = playlist.sortDescending)),
                listOf(S2Action(stringResource(R.string.playlist_menu_export), onExport, Icons.Rounded.FileDownload)),
            ),
        )
    }
}

@Composable
fun PlaylistDetailDestination(
    route: PlaylistRoute,
    onNavigateUp: () -> Unit,
    onNavigate: (NavigationTarget) -> Unit,
) {
    val viewModel = hiltViewModel<PlaylistDetailViewModel, PlaylistDetailViewModel.Factory> { it.create(route.playlistId) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    val snackbarHostState = LocalShellSnackbarHostState.current
    var dialog by remember { mutableStateOf<PlaylistDialog?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/x-mpegurl")) { uri: Uri? ->
        uri?.let { viewModel.exportTo(it.toString()) }
    }

    ConsumeEvents(uiState.events, viewModel::onEventHandled) { event ->
        when (event) {
            PlaylistDetailEvent.Deleted -> onNavigateUp()

            is PlaylistDetailEvent.ExportReady -> exportLauncher.launch(event.suggestedName)

            PlaylistDetailEvent.ExportEmpty -> snackbarHostState.showSnackbar(resources.getString(R.string.playlist_export_empty))

            PlaylistDetailEvent.ExportSucceeded -> snackbarHostState.showSnackbar(resources.getString(R.string.playlist_export_success))

            is PlaylistDetailEvent.ExportFailed -> snackbarHostState.showSnackbar(
                resources.getString(R.string.playlist_export_failed, event.error),
            )
        }
    }

    MediaActionsHost(onNavigate = onNavigate) { actions ->
        PlaylistDetailScreen(
            uiState = uiState,
            onNavigateUp = onNavigateUp,
            onPlay = { songs, index -> actions.dispatch(MediaAction.Play(MediaSelection.Songs(songs), index)) },
            onShuffle = { actions.dispatch(MediaAction.Shuffle(MediaSelection.Songs(uiState.songs.map { it.song }))) },
            onPlaylistMore = { playlist ->
                actions.showActions(
                    MediaActionsTarget(
                        title = playlist.name,
                        subtitle = null,
                        selection = MediaSelection.Playlists(playlist),
                        placeholder = ArtworkPlaceholder.Playlist,
                        extraActions = playlistManageActions(resources, playlist) { dialog = it },
                    ),
                )
            },
            onSongMore = { entry ->
                val playlist = uiState.playlist
                actions.showActions(
                    MediaActionsTarget(
                        title = entry.song.name.orEmpty(),
                        subtitle = entry.song.rowSubtitle,
                        selection = MediaSelection.Songs(entry.song),
                        placeholder = ArtworkPlaceholder.Song,
                        extraActions = listOfNotNull(
                            playlist?.let {
                                S2Action(resources.getString(R.string.playlist_detail_remove), { actions.dispatch(MediaAction.RemoveFromPlaylist(it, listOf(entry), uiState.songs)) }, Icons.Rounded.PlaylistRemove)
                            },
                        ),
                    ),
                )
            },
            onToggleSelected = viewModel::onToggleSelected,
            onClearSelection = viewModel::onClearSelection,
            onRemoveSelected = {
                uiState.playlist?.let { actions.dispatch(MediaAction.RemoveFromPlaylist(it, uiState.selectedEntries, uiState.songs)) }
                viewModel.onClearSelection()
            },
            onSelectionAction = { type ->
                actions.perform(type, MediaSelection.Songs(uiState.selectedEntries.map { it.song }))
                viewModel.onClearSelection()
            },
            onSortOrderSelected = viewModel::onSortOrderSelected,
            onSortDescendingChanged = viewModel::onSortDescendingChanged,
            onExport = viewModel::onExport,
            onMove = viewModel::onMove,
            onMoveFinished = viewModel::onMoveFinished,
        )
        PlaylistDialogHost(
            dialog = dialog,
            onRename = { _, name -> viewModel.onRename(name) },
            onClear = { viewModel.onClear() },
            onDelete = { viewModel.onDelete() },
            onDismissRequest = { dialog = null },
        )
    }
}
