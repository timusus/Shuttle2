package com.simplecityapps.shuttle.ui.screens.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.ArtistRow
import com.simplecityapps.shuttle.designsystem.component.ArtworkPlaceholder
import com.simplecityapps.shuttle.designsystem.component.ArtworkShape
import com.simplecityapps.shuttle.designsystem.component.ArtworkSize
import com.simplecityapps.shuttle.designsystem.component.GridTile
import com.simplecityapps.shuttle.designsystem.theme.S2Spacing
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.sorting.AlbumArtistSortOrder
import com.simplecityapps.shuttle.ui.common.components.FastScrollableState
import com.simplecityapps.shuttle.ui.common.components.rememberFastScrollableState
import com.simplecityapps.shuttle.ui.screens.library.albumartists.AlbumArtistListUiState
import com.simplecityapps.shuttle.ui.shell.ScrollToTopOnReselect
import com.simplecityapps.shuttle.ui.shell.ShellTab

/** Album artists: list or adaptive grid. */
@Composable
fun ArtistsPage(
    state: AlbumArtistListUiState,
    onArtistClick: (AlbumArtist) -> Unit,
    onArtistLongClick: (AlbumArtist) -> Unit,
    onArtistMore: (AlbumArtist) -> Unit,
    modifier: Modifier = Modifier,
    controls: LibraryTabControls? = null,
) {
    val content = when (state.loadingState) {
        AlbumArtistListUiState.LoadingState.Loading -> LibraryContentState.Loading
        AlbumArtistListUiState.LoadingState.Scanning -> LibraryContentState.Scanning
        AlbumArtistListUiState.LoadingState.Empty -> LibraryContentState.Empty
        AlbumArtistListUiState.LoadingState.Ready -> LibraryContentState.Ready
    }
    LibraryContent(content, stringResource(R.string.artist_list_empty), modifier, state.scanProgress, controls, hasItems = state.albumArtists.isNotEmpty()) {
        val artists = state.albumArtists
        val fastScroller: @Composable (FastScrollableState) -> Unit = { scrollableState ->
            LibraryFastScroller(artists, state.letterIndex, scrollableState, thumbLabel = artistThumbLabel(state.sortOrder), controls = controls)
        }
        Box(modifier.fillMaxSize()) {
            if (state.viewMode != ViewMode.List) {
                val compact = state.viewMode == ViewMode.CompactGrid
                val gridState = rememberLazyGridState()
                ScrollToTopOnReselect(ShellTab.Library, gridState)
                LazyVerticalGrid(
                    columns = libraryGridColumns(compact),
                    state = gridState,
                    contentPadding = gridPadding(controls, top = S2Spacing.medium),
                    horizontalArrangement = Arrangement.spacedBy(gridSpacing(compact)),
                    verticalArrangement = Arrangement.spacedBy(gridSpacing(compact)),
                    modifier = Modifier.fillMaxSize().testTag("library-artists"),
                ) {
                    controlsItem(controls, GridHorizontalPadding)
                    items(artists, key = { it.groupKey.toString() }) { artist ->
                        GridTile(
                            title = artist.name ?: artist.friendlyArtistName.orEmpty(),
                            subtitle = pluralString(R.plurals.albumsPlural, artist.albumCount),
                            onClick = { onArtistClick(artist) },
                            onLongClick = { onArtistLongClick(artist) },
                            selected = artist in state.selectedArtists,
                            titleMaxLines = if (compact) 1 else null,
                            artwork = { LibraryArtwork(artist, ArtworkPlaceholder.Artist, Modifier.fillMaxSize(), size = ArtworkSize.Grid, shape = ArtworkShape.Circle) },
                        )
                    }
                }
                fastScroller(rememberFastScrollableState(gridState))
            } else {
                val listState = rememberLazyListState()
                ScrollToTopOnReselect(ShellTab.Library, listState)
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize().testTag("library-artists")) {
                    controlsItem(controls)
                    items(artists, key = { it.groupKey.toString() }) { artist ->
                        ArtistRow(
                            name = artist.name ?: artist.friendlyArtistName.orEmpty(),
                            summary = "${pluralString(R.plurals.albumsPlural, artist.albumCount)} · ${pluralString(R.plurals.songsPlural, artist.songCount)}",
                            onClick = { onArtistClick(artist) },
                            onLongClick = { onArtistLongClick(artist) },
                            onMore = { onArtistMore(artist) },
                            selected = artist in state.selectedArtists,
                            artwork = { LibraryArtwork(artist, ArtworkPlaceholder.Artist, shape = ArtworkShape.Circle) },
                        )
                    }
                }
                fastScroller(rememberFastScrollableState(listState))
            }
        }
    }
}

/** The popup label of a plain-thumb artists scroll: the album count a count sort orders by; none under the name sort, which has letter sections. */
private fun artistThumbLabel(sortOrder: AlbumArtistSortOrder): ((AlbumArtist) -> String?)? = when (sortOrder) {
    AlbumArtistSortOrder.AlbumCount -> { artist -> artist.albumCount.toString() }
    AlbumArtistSortOrder.Default, AlbumArtistSortOrder.PlayCount -> null
}
