package com.simplecityapps.shuttle.ui.screens.library

import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.simplecityapps.shuttle.format.formatDuration
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.Genre
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.sorting.SongListSort
import com.simplecityapps.shuttle.ui.actions.MediaAction
import com.simplecityapps.shuttle.ui.actions.MediaSelection
import com.simplecityapps.shuttle.ui.actions.NavigationTarget
import com.simplecityapps.shuttle.ui.common.ConsumeEvents
import com.simplecityapps.shuttle.ui.common.mediaactions.MediaActionsHost
import com.simplecityapps.shuttle.ui.common.mediaactions.MediaActionsTarget
import com.simplecityapps.shuttle.ui.shell.LocalShellSnackbarHostState
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel

/** Genre detail (inventory §1): its songs in the chosen sort, folded under album headers when the sort groups by album. */
@Composable
fun GenreDetailScreen(
    uiState: GenreDetailUiState,
    onNavigateUp: () -> Unit,
    onPlay: (songs: List<Song>, index: Int) -> Unit,
    onShuffle: () -> Unit,
    onGenreMore: (Genre) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onAlbumMore: (Album) -> Unit,
    onSongMore: (Song) -> Unit,
    modifier: Modifier = Modifier,
    onSortSelected: (SongListSort) -> Unit = {},
    onToggleAlbum: (Album) -> Unit = {},
    onExpandAll: () -> Unit = {},
    onCollapseAll: () -> Unit = {},
) {
    val genre = uiState.genre
    val state = when {
        uiState.loading -> DetailContentState.Loading
        genre == null -> DetailContentState.NotFound
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
            onOpen = { onAlbumClick(section.album) },
            onMore = { onAlbumMore(section.album) },
            modifier = headerModifier,
        )
    }
    val grouped = albumSections.isNotEmpty()
    LibraryDetailScaffold(
        state = state,
        title = genre?.name ?: unknown,
        subtitle = genre?.let { listOf(pluralString(R.plurals.songsPlural, uiState.songs.size), formatDuration(uiState.songs.sumOf { it.duration }.toLong())).joinToString(" · ") },
        artwork = null,
        placeholder = ArtworkPlaceholder.Genre,
        onNavigateUp = onNavigateUp,
        onPlay = { onPlay(uiState.songs, 0) },
        onShuffle = onShuffle,
        onMore = { genre?.let(onGenreMore) },
        modifier = modifier.testTag("genre-detail"),
        listState = listState,
        overlay = { topInset -> PinnedAlbumHeader(listState, topInset, unfoldedSections, "genre-pinned-album", albumHeader) },
    ) {
        if (uiState.songs.isNotEmpty()) {
            item(key = "songs-header", contentType = "header") {
                SongsHeader(
                    title = stringResource(if (grouped) R.string.artist_detail_albums else R.string.artist_detail_songs),
                    sort = uiState.sort,
                    choices = uiState.kind.sortChoices,
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
fun GenreDetailDestination(
    route: GenreRoute,
    onNavigateUp: () -> Unit,
    onOpen: (NavKey) -> Unit,
    onNavigate: (NavigationTarget) -> Unit,
) {
    val viewModel = assistedMetroViewModel<GenreDetailViewModel, GenreDetailViewModel.Factory> { create(route.genreName) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    val snackbarHostState = LocalShellSnackbarHostState.current
    ConsumeEvents(uiState.events, viewModel::onEventHandled) { event ->
        when (event) {
            is GenreDetailEvent.ShuffleAlbumsFailed -> snackbarHostState.showSnackbar(
                resources.getString(R.string.shuffle_albums_failed, event.reason ?: resources.getString(R.string.error_unknown)),
            )
        }
    }
    MediaActionsHost(onNavigate = onNavigate) { actions ->
        GenreDetailScreen(
            uiState = uiState,
            onNavigateUp = onNavigateUp,
            onPlay = { songs, index -> actions.dispatch(MediaAction.Play(MediaSelection.Songs(songs), index, uiState.playContext)) },
            onShuffle = { actions.dispatch(MediaAction.Shuffle(MediaSelection.Songs(uiState.songs), uiState.playContext)) },
            onGenreMore = { genre ->
                val extraActions = shuffleAlbumsActions(uiState.sort, resources.getString(R.string.detail_shuffle_albums), viewModel::onShuffleAlbums)
                actions.showActions(MediaActionsTarget(genre.name, null, MediaSelection.Genres(genre), ArtworkPlaceholder.Genre, extraActions))
            },
            onAlbumClick = { album -> onOpen(album.route) },
            onAlbumMore = { album -> actions.showActions(albumMoreTarget(album, resources.getString(R.string.menu_title_view_album)) { onOpen(album.route) }) },
            onSongMore = { song -> actions.showActions(MediaActionsTarget(song.name.orEmpty(), song.rowSubtitle, MediaSelection.Songs(song), ArtworkPlaceholder.Song)) },
            onSortSelected = viewModel::onSortSelected,
            onToggleAlbum = viewModel::onToggleAlbum,
            onExpandAll = viewModel::onExpandAll,
            onCollapseAll = viewModel::onCollapseAll,
        )
    }
}
