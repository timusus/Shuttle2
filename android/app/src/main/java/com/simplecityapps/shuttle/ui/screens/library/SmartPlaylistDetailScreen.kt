package com.simplecityapps.shuttle.ui.screens.library

import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.HeartBroken
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.ArtworkPlaceholder
import com.simplecityapps.shuttle.designsystem.component.S2Action
import com.simplecityapps.shuttle.format.formatDuration
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.SmartPlaylistId
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.sorting.SongListSort
import com.simplecityapps.shuttle.ui.actions.MediaAction
import com.simplecityapps.shuttle.ui.actions.MediaSelection
import com.simplecityapps.shuttle.ui.actions.NavigationTarget
import com.simplecityapps.shuttle.ui.common.ConsumeEvents
import com.simplecityapps.shuttle.ui.common.mediaactions.MediaActionsHost
import com.simplecityapps.shuttle.ui.common.mediaactions.MediaActionsTarget
import com.simplecityapps.shuttle.ui.shell.LocalShellSnackbarHostState
import com.simplecityapps.shuttle.ui.text.stringResource as stringResourceKey
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel

/**
 * A built-in smart playlist (inventory §1): its songs in the chosen sort, folded under album headers when the sort
 * groups by album, Play / Shuffle, and their actions.
 */
@Composable
fun SmartPlaylistDetailScreen(
    uiState: SmartPlaylistDetailUiState,
    onNavigateUp: () -> Unit,
    onPlay: (songs: List<Song>, index: Int) -> Unit,
    onShuffle: () -> Unit,
    onPlaylistMore: () -> Unit,
    onSongMore: (Song) -> Unit,
    modifier: Modifier = Modifier,
    onSortSelected: (SongListSort) -> Unit = {},
    onToggleAlbum: (Album) -> Unit = {},
    onOpenAlbum: (Album) -> Unit = {},
    onAlbumMore: (Album) -> Unit = {},
    onExpandAll: () -> Unit = {},
    onCollapseAll: () -> Unit = {},
) {
    val playlist = uiState.smartPlaylist
    val state = when {
        uiState.loading -> DetailContentState.Loading
        playlist == null -> DetailContentState.NotFound
        else -> DetailContentState.Ready
    }
    val unknown = stringResource(com.simplecityapps.core.R.string.unknown)
    val listState = rememberLazyListState()
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
    LibraryDetailScaffold(
        state = state,
        title = playlist?.let { stringResourceKey(it.id.nameKey) } ?: unknown,
        subtitle = playlist?.let { listOf(pluralString(R.plurals.songsPlural, uiState.songs.size), formatDuration(uiState.songs.sumOf { it.duration }.toLong())).joinToString(" · ") },
        artwork = null,
        placeholder = playlist?.placeholder ?: ArtworkPlaceholder.SmartPlaylist,
        onNavigateUp = onNavigateUp,
        onPlay = { onPlay(uiState.songs, 0) },
        onShuffle = onShuffle,
        onMore = onPlaylistMore,
        modifier = modifier.testTag("smart-playlist-detail"),
        listState = listState,
        overlay = { topInset -> PinnedAlbumHeader(listState, topInset, unfoldedSections, "playlist-pinned-album", albumHeader) },
    ) {
        val kind = uiState.kind
        if (kind != null && uiState.songs.isNotEmpty() && kind.sorts.size > 1) {
            item(key = "songs-header", contentType = "header") {
                SongsHeader(
                    title = stringResource(if (grouped) R.string.artist_detail_albums else R.string.artist_detail_songs),
                    sort = uiState.sort,
                    choices = kind.sortChoices,
                    allExpanded = uiState.allAlbumsExpanded.takeIf { grouped },
                    onSortSelected = onSortSelected,
                    onExpandAll = onExpandAll,
                    onCollapseAll = onCollapseAll,
                    sortTag = "playlist-sort",
                )
            }
        }
        songSectionRows(
            sections = uiState.sections,
            expanded = uiState.expandedAlbums,
            currentSong = uiState.currentSong,
            unknown = unknown,
            header = albumHeader,
            onPlay = { onPlay(uiState.songs, it) },
            onSongMore = { onSongMore(uiState.songs[it]) },
        ) { songs, startIndex ->
            itemsIndexed(songs, key = { _, song -> "song-${song.id}" }, contentType = { _, _ -> "song" }) { index, song ->
                LibrarySongRow(
                    song = song,
                    onClick = { onPlay(uiState.songs, startIndex + index) },
                    playing = song.id == uiState.currentSong?.id,
                    onMore = { onSongMore(song) },
                )
            }
        }
    }
}

@Composable
fun SmartPlaylistDetailDestination(
    route: SmartPlaylistRoute,
    onNavigateUp: () -> Unit,
    onOpen: (NavKey) -> Unit,
    onNavigate: (NavigationTarget) -> Unit,
) {
    val viewModel = assistedMetroViewModel<SmartPlaylistDetailViewModel, SmartPlaylistDetailViewModel.Factory> { create(route.smartPlaylistId) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    val snackbarHostState = LocalShellSnackbarHostState.current
    ConsumeEvents(uiState.events, viewModel::onEventHandled) { event ->
        when (event) {
            is SmartPlaylistDetailEvent.ShuffleAlbumsFailed -> snackbarHostState.showSnackbar(
                resources.getString(R.string.shuffle_albums_failed, event.reason ?: resources.getString(R.string.error_unknown)),
            )
        }
    }
    val title = uiState.smartPlaylist?.let { stringResourceKey(it.id.nameKey) }.orEmpty()
    val placeholder = uiState.smartPlaylist?.placeholder ?: ArtworkPlaceholder.SmartPlaylist
    val isFavourites = route.smartPlaylistId == SmartPlaylistId.Favourites.id
    val removeFromFavourites = stringResource(R.string.menu_title_remove_from_favorites)
    MediaActionsHost(onNavigate = onNavigate) { actions ->
        SmartPlaylistDetailScreen(
            uiState = uiState,
            onNavigateUp = onNavigateUp,
            onPlay = { songs, index -> actions.dispatch(MediaAction.Play(MediaSelection.Songs(songs), index, uiState.playContext)) },
            onShuffle = { actions.dispatch(MediaAction.Shuffle(MediaSelection.Songs(uiState.songs), uiState.playContext)) },
            // A smart playlist is a query, not a stored playlist, so its sheet acts on the songs it currently holds.
            onPlaylistMore = {
                val extraActions = shuffleAlbumsActions(uiState.sort, resources.getString(R.string.detail_shuffle_albums), viewModel::onShuffleAlbums)
                actions.showActions(MediaActionsTarget(title, null, MediaSelection.Songs(uiState.songs), placeholder, extraActions))
            },
            onSongMore = { song ->
                val selection = MediaSelection.Songs(song)
                // Favourites are a flag on the song, so taking one out of the list stops it being a favourite
                val extraActions = if (isFavourites) {
                    listOf(S2Action(removeFromFavourites, { actions.dispatch(MediaAction.Favourite(selection, favourite = false)) }, Icons.Rounded.HeartBroken))
                } else {
                    emptyList()
                }
                actions.showActions(MediaActionsTarget(song.name.orEmpty(), song.rowSubtitle, selection, ArtworkPlaceholder.Song, extraActions))
            },
            onSortSelected = viewModel::onSortSelected,
            onToggleAlbum = viewModel::onToggleAlbum,
            onOpenAlbum = { album -> onOpen(album.route) },
            onAlbumMore = { album -> actions.showActions(albumMoreTarget(album, resources.getString(R.string.menu_title_view_album)) { onOpen(album.route) }) },
            onExpandAll = viewModel::onExpandAll,
            onCollapseAll = viewModel::onCollapseAll,
        )
    }
}

/** Shuffle albums, offered only while [sort] shows the songs under their albums. */
internal fun shuffleAlbumsActions(sort: SongListSort, label: String, onShuffleAlbums: () -> Unit): List<S2Action> = if (sort.groupsByAlbum) listOf(S2Action(label, onShuffleAlbums, Icons.Rounded.Shuffle)) else emptyList()
