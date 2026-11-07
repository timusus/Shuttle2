package com.simplecityapps.shuttle.ui.screens.library

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistRemove
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.MaterialTheme
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.ArtworkPlaceholder
import com.simplecityapps.shuttle.designsystem.component.ArtworkSize
import com.simplecityapps.shuttle.designsystem.component.QueueRow
import com.simplecityapps.shuttle.designsystem.component.S2Action
import com.simplecityapps.shuttle.designsystem.component.S2IconButton
import com.simplecityapps.shuttle.designsystem.component.S2SelectionToolbar
import com.simplecityapps.shuttle.designsystem.component.S2Text
import com.simplecityapps.shuttle.designsystem.theme.S2Spacing
import com.simplecityapps.shuttle.format.formatDuration
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.PlaylistSong
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.sorting.PlaylistKind
import com.simplecityapps.shuttle.sorting.SongListSort
import com.simplecityapps.shuttle.ui.actions.MediaAction
import com.simplecityapps.shuttle.ui.actions.MediaActionType
import com.simplecityapps.shuttle.ui.actions.MediaSelection
import com.simplecityapps.shuttle.ui.actions.NavigationTarget
import com.simplecityapps.shuttle.ui.common.ConsumeEvents
import com.simplecityapps.shuttle.ui.common.downloads.DownloadStatusHeader
import com.simplecityapps.shuttle.ui.common.downloads.offlineState
import com.simplecityapps.shuttle.ui.common.mediaactions.MediaActionsHost
import com.simplecityapps.shuttle.ui.common.mediaactions.MediaActionsTarget
import com.simplecityapps.shuttle.ui.shell.LocalShellSnackbarHostState
import com.simplecityapps.shuttle.ui.shell.player.QueuePosition
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * Playlist detail (inventory §1): the playlist's songs in its view sort, folded under album headers when the sort
 * groups by album. Sorted by Custom, each row has a drag handle; otherwise a hint says how to get back to
 * reordering. In a flat list, long press starts a multi-selection whose toolbar removes the entries (with Undo) or
 * hands them to the shared actions. Export sits in the top bar, rename / clear / delete in the playlist's sheet.
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
    onSortSelected: (SongListSort) -> Unit,
    onExport: () -> Unit,
    onMove: (fromId: Long, toId: Long) -> Unit,
    onMoveFinished: () -> Unit,
    modifier: Modifier = Modifier,
    covers: List<Song> = emptyList(),
    onToggleAlbum: (Album) -> Unit = {},
    onOpenAlbum: (Album) -> Unit = {},
    onAlbumMore: (Album) -> Unit = {},
    onExpandAll: () -> Unit = {},
    onCollapseAll: () -> Unit = {},
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
    val songs = remember(uiState.songs) { uiState.songs.map { it.song } }
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fromId = from.key as? Long
        val toId = to.key as? Long
        if (fromId != null && toId != null) onMove(fromId, toId)
    }
    BackHandler(enabled = selecting, onBack = onClearSelection)
    val unknown = stringResource(com.simplecityapps.core.R.string.unknown)
    val albumSections = remember(uiState.sections) { uiState.sections.mapNotNull { section -> section.album?.let { AlbumSongs(it, section.songs) } } }
    val unfoldedSections = remember(albumSections, uiState.expandedAlbums) { albumSections.unfoldedByKey(uiState.expandedAlbums) }
    val albumHeader: @Composable (AlbumSongs, Modifier) -> Unit = { section, headerModifier ->
        AlbumSectionHeader(
            album = section.album,
            songCount = section.songs.size,
            expanded = section.album.groupKey in uiState.expandedAlbums,
            unknown = unknown,
            onToggle = { onToggleAlbum(section.album) },
            onOpen = { onOpenAlbum(section.album) },
            onMore = { onAlbumMore(section.album) },
            modifier = headerModifier,
        )
    }
    val grouped = albumSections.isNotEmpty()
    val sortChoices = PlaylistKind.User.sortChoices

    Box(modifier.testTag("playlist-detail")) {
        LibraryDetailScaffold(
            state = state,
            title = playlist?.name ?: stringResource(com.simplecityapps.core.R.string.unknown),
            subtitle = playlist?.let { listOf(pluralString(R.plurals.songsPlural, uiState.songs.size), formatDuration(songs.sumOf { it.duration }.toLong())).joinToString(" · ") },
            artwork = null,
            placeholder = ArtworkPlaceholder.Playlist,
            heroArtwork = { heroModifier -> CoverMosaic(covers, ArtworkPlaceholder.Playlist, heroModifier, size = ArtworkSize.Hero) },
            onNavigateUp = onNavigateUp,
            onPlay = { onPlay(songs, 0) },
            onShuffle = onShuffle,
            onMore = { playlist?.let(onPlaylistMore) },
            listState = listState,
            overlay = { topInset -> PinnedAlbumHeader(listState, topInset, unfoldedSections, "playlist-pinned-album", albumHeader) },
            actions = {
                if (playlist != null) {
                    S2IconButton(icon = Icons.Rounded.FileDownload, contentDescription = stringResource(R.string.playlist_menu_export), onClick = onExport)
                }
            },
            header = {
                DownloadStatusHeader(songs, Modifier.padding(top = S2Spacing.smallMedium))
                if (playlist != null && !uiState.canReorder && uiState.songs.isNotEmpty()) {
                    val sortLabel = stringResource((sortChoices.firstOrNull { it.value == uiState.sort } ?: sortChoices.first()).label)
                    S2Text(
                        text = stringResource(R.string.playlist_detail_reorder_hint, sortLabel),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = S2Spacing.smallMedium).testTag("playlist-reorder-hint"),
                    )
                }
            },
        ) {
            if (playlist != null && uiState.songs.isNotEmpty()) {
                item(key = "songs-header", contentType = "header") {
                    SongsHeader(
                        title = stringResource(if (grouped) R.string.artist_detail_albums else R.string.artist_detail_songs),
                        sort = uiState.sort,
                        choices = sortChoices,
                        allExpanded = uiState.allAlbumsExpanded.takeIf { grouped },
                        onSortSelected = onSortSelected,
                        onExpandAll = onExpandAll,
                        onCollapseAll = onCollapseAll,
                        sortTag = "playlist-sort",
                    )
                }
            }
            if (grouped) {
                // Album sections show plain song rows: drag and multi-selection belong to the flat list
                songSectionRows(
                    sections = uiState.sections,
                    expanded = uiState.expandedAlbums,
                    currentSong = uiState.currentSong,
                    unknown = unknown,
                    header = albumHeader,
                    onPlay = { onPlay(songs, it) },
                    onSongMore = { onSongMore(uiState.songs[it]) },
                ) { looseSongs, startIndex ->
                    itemsIndexed(looseSongs, key = { index, song -> "loose-${startIndex + index}-${song.id}" }, contentType = { _, _ -> "song" }) { index, song ->
                        LibrarySongRow(
                            song = song,
                            onClick = { onPlay(songs, startIndex + index) },
                            playing = song.id == uiState.currentSong?.id,
                            onMore = { onSongMore(uiState.songs[startIndex + index]) },
                        )
                    }
                }
            } else {
                uiState.songs.forEachIndexed { index, entry ->
                    item(key = entry.id, contentType = "song") {
                        ReorderableItem(reorderState, key = entry.id, enabled = reorderable) { dragging ->
                            PlaylistEntryRow(
                                entry = entry,
                                reorderable = reorderable,
                                selecting = selecting,
                                selected = entry.id in uiState.selectedIds,
                                playing = entry.song.id == uiState.currentSong?.id,
                                dragging = dragging,
                                dragHandleModifier = Modifier.draggableHandle(onDragStopped = onMoveFinished),
                                onPlay = { onPlay(songs, index) },
                                onToggleSelected = onToggleSelected,
                                onSongMore = onSongMore,
                            )
                        }
                    }
                }
            }
        }

        if (selecting) {
            PlaylistSelectionToolbar(
                uiState = uiState,
                onClearSelection = onClearSelection,
                onRemoveSelected = onRemoveSelected,
                onSelectionAction = onSelectionAction,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = S2Spacing.medium),
            )
        }
    }
}

/** A queue-style row with a drag handle while the playlist is reorderable, else a selectable song row. */
@Composable
private fun PlaylistEntryRow(
    entry: PlaylistSong,
    reorderable: Boolean,
    selecting: Boolean,
    selected: Boolean,
    playing: Boolean,
    dragging: Boolean,
    dragHandleModifier: Modifier,
    onPlay: () -> Unit,
    onToggleSelected: (PlaylistSong) -> Unit,
    onSongMore: (PlaylistSong) -> Unit,
) {
    val song = entry.song
    if (reorderable) {
        QueueRow(
            title = song.name.orEmpty(),
            subtitle = song.rowSubtitle,
            onClick = onPlay,
            position = if (playing) QueuePosition.Current else QueuePosition.Upcoming,
            artwork = { LibraryArtwork(song, ArtworkPlaceholder.Song, size = ArtworkSize.Small) },
            duration = formatDuration(song.duration.toLong()),
            dragging = dragging,
            offlineState = song.offlineState(),
            dragHandleModifier = dragHandleModifier,
            onLongClick = { onToggleSelected(entry) },
        )
    } else {
        LibrarySongRow(
            song = song,
            onClick = { if (selecting) onToggleSelected(entry) else onPlay() },
            selected = selected,
            playing = playing,
            onLongClick = { onToggleSelected(entry) },
            onMore = if (selecting) null else ({ onSongMore(entry) }),
        )
    }
}

@Composable
private fun PlaylistSelectionToolbar(
    uiState: PlaylistDetailUiState,
    onClearSelection: () -> Unit,
    onRemoveSelected: () -> Unit,
    onSelectionAction: (MediaActionType) -> Unit,
    modifier: Modifier,
) {
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
        modifier = modifier,
    )
}

@Composable
fun PlaylistDetailDestination(
    route: PlaylistRoute,
    onNavigateUp: () -> Unit,
    onOpen: (NavKey) -> Unit,
    onNavigate: (NavigationTarget) -> Unit,
) {
    val viewModel = assistedMetroViewModel<PlaylistDetailViewModel, PlaylistDetailViewModel.Factory> { create(route.playlistId) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val coversViewModel = assistedMetroViewModel<PlaylistDetailCoversViewModel, PlaylistDetailCoversViewModel.Factory> { create(route.playlistId) }
    val covers by coversViewModel.uiState.collectAsStateWithLifecycle()
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

            is PlaylistDetailEvent.ShuffleAlbumsFailed -> snackbarHostState.showSnackbar(
                resources.getString(R.string.shuffle_albums_failed, event.reason ?: resources.getString(R.string.error_unknown)),
            )
        }
    }

    MediaActionsHost(onNavigate = onNavigate) { actions ->
        PlaylistDetailScreen(
            uiState = uiState,
            onNavigateUp = onNavigateUp,
            onPlay = { songs, index -> actions.dispatch(MediaAction.Play(MediaSelection.Songs(songs), index, uiState.playContext)) },
            onShuffle = { actions.dispatch(MediaAction.Shuffle(MediaSelection.Songs(uiState.songs.map { it.song }), uiState.playContext)) },
            onPlaylistMore = { playlist ->
                actions.showActions(
                    MediaActionsTarget(
                        title = playlist.name,
                        subtitle = null,
                        selection = MediaSelection.Playlists(playlist),
                        placeholder = ArtworkPlaceholder.Playlist,
                        extraActions = shuffleAlbumsActions(uiState.sort, resources.getString(R.string.detail_shuffle_albums), viewModel::onShuffleAlbums) +
                            playlistManageActions(resources, playlist) { dialog = it },
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
            onSortSelected = viewModel::onSortSelected,
            onExport = viewModel::onExport,
            onMove = viewModel::onMove,
            onMoveFinished = viewModel::onMoveFinished,
            covers = covers,
            onToggleAlbum = viewModel::onToggleAlbum,
            onOpenAlbum = { album -> onOpen(album.route) },
            onAlbumMore = { album -> actions.showActions(albumMoreTarget(album, resources.getString(R.string.menu_title_view_album)) { onOpen(album.route) }) },
            onExpandAll = viewModel::onExpandAll,
            onCollapseAll = viewModel::onCollapseAll,
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
