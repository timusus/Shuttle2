package com.simplecityapps.shuttle.ui.screens.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistSortOrder
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.S2Action
import com.simplecityapps.shuttle.designsystem.component.S2Button
import com.simplecityapps.shuttle.designsystem.component.S2ButtonStyle
import com.simplecityapps.shuttle.designsystem.component.S2IconButton
import com.simplecityapps.shuttle.designsystem.component.S2Menu
import com.simplecityapps.shuttle.designsystem.component.S2Text
import com.simplecityapps.shuttle.designsystem.theme.S2Spacing
import com.simplecityapps.shuttle.designsystem.theme.S2TouchTarget
import com.simplecityapps.shuttle.designsystem.theme.isLargeText
import com.simplecityapps.shuttle.persistence.LibraryTab
import com.simplecityapps.shuttle.sorting.AlbumArtistSortOrder
import com.simplecityapps.shuttle.sorting.AlbumSortOrder
import com.simplecityapps.shuttle.sorting.GenreSortOrder
import com.simplecityapps.shuttle.sorting.SongSortOrder
import com.simplecityapps.shuttle.ui.actions.MediaAction
import com.simplecityapps.shuttle.ui.actions.MediaSelection
import com.simplecityapps.shuttle.ui.common.mediaactions.MediaActionsState
import com.simplecityapps.shuttle.ui.screens.library.albumartists.AlbumArtistListUiState
import com.simplecityapps.shuttle.ui.screens.library.albumartists.AlbumArtistListViewModel
import com.simplecityapps.shuttle.ui.screens.library.albums.AlbumListUiState
import com.simplecityapps.shuttle.ui.screens.library.albums.AlbumListViewModel
import com.simplecityapps.shuttle.ui.screens.library.genres.GenreListViewModel
import com.simplecityapps.shuttle.ui.screens.library.playlists.PlaylistListViewModel
import com.simplecityapps.shuttle.ui.screens.library.songs.SongListUiState
import com.simplecityapps.shuttle.ui.screens.library.songs.SongListViewModel
import dev.zacsweers.metrox.viewmodel.metroViewModel

/** A tab's controls row: its count, sort, view and play actions, each only where the tab offers it. */
class LibraryTabControls(
    val count: String? = null,
    /** The tab's sorts, the current one selected; the sort button names it and opens them as a menu. None for a tab without sorts. */
    val sortOptions: List<S2Action> = emptyList(),
    /** The layout, on a tab that offers a grid and a list; the toggle switches it through [onViewModeChange]. */
    val viewMode: ViewMode? = null,
    val onViewModeChange: (ViewMode) -> Unit = {},
    /** Plays or shuffles the whole tab, on a tab that offers it and has something to play. */
    val onPlay: (() -> Unit)? = null,
    val onShuffle: (() -> Unit)? = null,
) {
    /** Whether the row has nothing to show (Folders), so the page leaves it out. */
    val isEmpty: Boolean get() = count == null && sortOptions.isEmpty() && viewMode == null && onPlay == null && onShuffle == null
}

private const val CONTROLS_KEY = "controls"

/** The controls row as the list's first item, so it scrolls away with the page (#669). Nothing when there are no controls. */
internal fun LazyListScope.controlsItem(controls: LibraryTabControls?) {
    if (controls == null || controls.isEmpty) return
    item(key = CONTROLS_KEY, contentType = CONTROLS_KEY) { LibraryControlsRow(controls) }
}

/**
 * The controls row as the grid's first, full-span item. It bleeds through the grid's [horizontalPadding] so it lines up
 * with the list pages' row.
 */
internal fun LazyGridScope.controlsItem(controls: LibraryTabControls?, horizontalPadding: Dp) {
    if (controls == null || controls.isEmpty) return
    item(key = CONTROLS_KEY, span = { GridItemSpan(maxLineSpan) }, contentType = CONTROLS_KEY) {
        LibraryControlsRow(controls, Modifier.bleed(horizontalPadding))
    }
}

/** Whether [controls] add a leading item to the page's list, which the fast scroller's indices must skip. */
internal fun controlsItemCount(controls: LibraryTabControls?): Int = if (controls == null || controls.isEmpty) 0 else 1

/** The controls row's height when the page shows one, so the fast scroller's track can start below it. */
internal fun controlsRowHeight(controls: LibraryTabControls?): Dp = if (controlsItemCount(controls) == 0) 0.dp else ControlsRowHeight

/** The row's least height; it grows with the text, but its buttons keep the scroller's track clear of them at this much. */
private val ControlsRowHeight = S2TouchTarget.minimum

/** Widens the content by [amount] on each side, past the padding its parent gives it. */
private fun Modifier.bleed(amount: Dp) = layout { measurable, constraints ->
    val extra = amount.roundToPx() * 2
    val placeable = measurable.measure(constraints.copy(minWidth = constraints.minWidth + extra, maxWidth = constraints.maxWidth + extra))
    layout(placeable.width - extra, placeable.height) { placeable.place(-extra / 2, 0) }
}

/**
 * A tab's controls: its count and sort button on the start side; the grid/list toggle, Shuffle and Play on the end side,
 * each only where the tab offers it.
 */
@Composable
internal fun LibraryControlsRow(controls: LibraryTabControls, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = ControlsRowHeight)
            .padding(start = S2Spacing.medium, end = S2Spacing.small)
            .testTag("library-controls"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(S2Spacing.small),
    ) {
        // The start group takes what the end actions leave. Its count keeps its width until the sort, which drops its label at
        // large text, no longer fits; then the count is what gives way.
        val largeText = isLargeText()
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(S2Spacing.small)) {
            controls.count?.let { count ->
                S2Text(
                    count,
                    modifier = if (largeText) Modifier.weight(1f, fill = false) else Modifier,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (controls.sortOptions.isNotEmpty()) {
                Box(if (largeText) Modifier else Modifier.weight(1f, fill = false)) {
                    var sorting by remember { mutableStateOf(false) }
                    val current = controls.sortOptions.firstOrNull { it.selected == true }?.label
                    val description = current?.let { stringResource(R.string.library_sort_current, it) } ?: stringResource(R.string.library_sort)
                    if (largeText) {
                        S2IconButton(
                            icon = Icons.Rounded.SwapVert,
                            contentDescription = description,
                            onClick = { sorting = true },
                            modifier = Modifier.testTag("library-sort"),
                        )
                    } else {
                        S2Button(
                            text = current ?: stringResource(R.string.library_sort),
                            onClick = { sorting = true },
                            style = S2ButtonStyle.Text,
                            icon = Icons.Rounded.SwapVert,
                            modifier = Modifier
                                .heightIn(min = S2TouchTarget.minimum)
                                .semantics { contentDescription = description }
                                .testTag("library-sort"),
                        )
                    }
                    S2Menu(expanded = sorting, onDismissRequest = { sorting = false }, groups = listOf(controls.sortOptions))
                }
            }
        }
        controls.viewMode?.let { mode -> ViewModeButton(mode, controls.onViewModeChange) }
        controls.onShuffle?.let { S2IconButton(icon = Icons.Rounded.Shuffle, contentDescription = stringResource(R.string.menu_title_shuffle), onClick = it) }
        controls.onPlay?.let { S2IconButton(icon = Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.menu_title_play), onClick = it) }
    }
}

/** The layout button: shows the current [mode]'s icon and opens the three layouts as a menu, the current one checked. */
@Composable
private fun ViewModeButton(mode: ViewMode, onViewModeChange: (ViewMode) -> Unit) {
    Box {
        var choosing by remember { mutableStateOf(false) }
        S2IconButton(
            icon = mode.icon,
            contentDescription = stringResource(R.string.library_view_mode_current, stringResource(mode.label)),
            onClick = { choosing = true },
            modifier = Modifier.testTag("library-view-mode"),
        )
        val options = ViewMode.entries.map { option ->
            S2Action(stringResource(option.label), { onViewModeChange(option) }, option.icon, selected = option == mode)
        }
        S2Menu(expanded = choosing, onDismissRequest = { choosing = false }, groups = listOf(options))
    }
}

private val ViewMode.icon: ImageVector
    get() = when (this) {
        ViewMode.List -> Icons.AutoMirrored.Rounded.ViewList
        ViewMode.Grid -> Icons.Rounded.GridView
        ViewMode.CompactGrid -> Icons.Rounded.Apps
    }

private val ViewMode.label: Int
    get() = when (this) {
        ViewMode.List -> R.string.library_view_mode_list
        ViewMode.Grid -> R.string.library_view_mode_grid
        ViewMode.CompactGrid -> R.string.library_view_mode_compact_grid
    }

/** A tab's controls row, read from the same ViewModel instance its page uses. */
@Composable
internal fun tabControls(tab: LibraryTab, actions: MediaActionsState): LibraryTabControls = when (tab) {
    LibraryTab.Songs -> {
        val viewModel: SongListViewModel = metroViewModel()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        val songs = state.songs.takeIf { state.loadingState == SongListUiState.LoadingState.Ready && it.isNotEmpty() }
        LibraryTabControls(
            count = pluralString(R.plurals.songsPlural, state.songs.size),
            sortOptions = sortOptions(
                state.sortOrder,
                listOf(
                    SongSortOrder.SongName to R.string.menu_title_sort_song_name,
                    SongSortOrder.ArtistGroupKey to R.string.menu_title_sort_artist_name,
                    SongSortOrder.AlbumGroupKey to R.string.menu_title_sort_album_name,
                    SongSortOrder.Year to R.string.menu_title_sort_year,
                    SongSortOrder.Duration to R.string.menu_title_sort_duration,
                    SongSortOrder.LastModified to R.string.menu_title_sort_date_modified,
                ),
                viewModel::setSortOrder,
            ),
            onPlay = songs?.let { { actions.dispatch(MediaAction.Play(MediaSelection.Songs(it))) } },
            onShuffle = songs?.let { { actions.dispatch(MediaAction.Shuffle(MediaSelection.Songs(it))) } },
        )
    }

    LibraryTab.Albums -> {
        val viewModel: AlbumListViewModel = metroViewModel()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        val albums = state.albums.takeIf { state.loadingState == AlbumListUiState.LoadingState.Ready && it.isNotEmpty() }
        LibraryTabControls(
            count = pluralString(R.plurals.albumsPlural, state.albums.size),
            sortOptions = sortOptions(
                state.sortOrder,
                listOf(
                    AlbumSortOrder.AlbumName to R.string.menu_title_sort_album_name,
                    AlbumSortOrder.ArtistGroupKey to R.string.menu_title_sort_artist_name,
                    AlbumSortOrder.Year to R.string.menu_title_sort_year,
                    AlbumSortOrder.Random to R.string.menu_title_sort_random,
                ),
                viewModel::setSortOrder,
            ),
            viewMode = state.viewMode,
            onViewModeChange = viewModel::setViewMode,
            onPlay = albums?.let { { actions.dispatch(MediaAction.Play(MediaSelection.Albums(it))) } },
            // Album-grouped shuffle has no MediaAction yet; the tab ViewModel keeps it.
            onShuffle = albums?.let { viewModel::onShuffle },
        )
    }

    LibraryTab.Artists -> {
        val viewModel: AlbumArtistListViewModel = metroViewModel()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        // Play and Shuffle take every listed artist's songs, artist by artist in the current sort.
        val artists = state.albumArtists.takeIf { state.loadingState == AlbumArtistListUiState.LoadingState.Ready && it.isNotEmpty() }
        LibraryTabControls(
            count = pluralString(R.plurals.library_count_artists, state.albumArtists.size),
            sortOptions = sortOptions(
                state.sortOrder,
                listOf(
                    AlbumArtistSortOrder.Default to R.string.menu_title_sort_artist_name,
                    AlbumArtistSortOrder.AlbumCount to R.string.menu_title_sort_album_count,
                ),
                viewModel::setSortOrder,
            ),
            viewMode = state.viewMode,
            onViewModeChange = viewModel::setViewMode,
            onPlay = artists?.let { { actions.dispatch(MediaAction.Play(MediaSelection.AlbumArtists(it))) } },
            onShuffle = artists?.let { { actions.dispatch(MediaAction.Shuffle(MediaSelection.AlbumArtists(it))) } },
        )
    }

    LibraryTab.Genres -> {
        val viewModel: GenreListViewModel = metroViewModel()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        LibraryTabControls(
            count = pluralString(R.plurals.library_count_genres, state.genres.size),
            sortOptions = sortOptions(
                state.sortOrder,
                listOf(GenreSortOrder.Default to R.string.menu_title_sort_genre_name, GenreSortOrder.SongCount to R.string.menu_title_sort_song_count),
                viewModel::setSortOrder,
            ),
        )
    }

    LibraryTab.Playlists -> {
        val viewModel: PlaylistListViewModel = metroViewModel()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        LibraryTabControls(
            count = pluralString(R.plurals.library_count_playlists, state.playlists.size),
            sortOptions = sortOptions(
                state.sortOrder,
                listOf(PlaylistSortOrder.Name to R.string.menu_title_sort_playlist_name, PlaylistSortOrder.Default to R.string.menu_title_sort_date_created),
                viewModel::setSortOrder,
            ),
        )
    }

    LibraryTab.Folders -> LibraryTabControls()
}

@Composable
internal fun <T> sortOptions(current: T, options: List<Pair<T, Int>>, onSelect: (T) -> Unit): List<S2Action> = options.map { (order, label) ->
    S2Action(stringResource(label), { onSelect(order) }, selected = order == current)
}
