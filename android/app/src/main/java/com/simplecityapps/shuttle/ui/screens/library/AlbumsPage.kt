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
import com.simplecityapps.shuttle.designsystem.component.AlbumRow
import com.simplecityapps.shuttle.designsystem.component.ArtworkPlaceholder
import com.simplecityapps.shuttle.designsystem.component.ArtworkSize
import com.simplecityapps.shuttle.designsystem.component.GridTile
import com.simplecityapps.shuttle.designsystem.theme.S2Spacing
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.ui.common.components.FastScrollableState
import com.simplecityapps.shuttle.ui.common.components.rememberFastScrollableState
import com.simplecityapps.shuttle.ui.screens.library.albums.AlbumListUiState
import com.simplecityapps.shuttle.ui.screens.library.albums.albumThumbLabel
import com.simplecityapps.shuttle.ui.shell.ScrollToTopOnReselect
import com.simplecityapps.shuttle.ui.shell.ShellTab
import com.simplecityapps.shuttle.ui.shell.albumArtworkKey
import com.simplecityapps.shuttle.ui.shell.sharedArtwork

/** Albums: a grid by default or a list. */
@Composable
fun AlbumsPage(
    state: AlbumListUiState,
    onAlbumClick: (Album) -> Unit,
    onAlbumLongClick: (Album) -> Unit,
    onAlbumMore: (Album) -> Unit,
    modifier: Modifier = Modifier,
    controls: LibraryTabControls? = null,
) {
    val content = when (state.loadingState) {
        AlbumListUiState.LoadingState.Loading -> LibraryContentState.Loading
        AlbumListUiState.LoadingState.Scanning -> LibraryContentState.Scanning
        AlbumListUiState.LoadingState.Empty -> LibraryContentState.Empty
        AlbumListUiState.LoadingState.Ready -> LibraryContentState.Ready
    }
    LibraryContent(content, stringResource(R.string.album_list_empty), modifier, state.scanProgress, controls, hasItems = state.albums.isNotEmpty()) {
        val fastScroller: @Composable (FastScrollableState) -> Unit = { scrollableState ->
            LibraryFastScroller(state.albums, state.letterIndex, scrollableState, thumbLabel = albumThumbLabel(state.sortOrder), controls = controls)
        }
        Box(modifier.fillMaxSize()) {
            if (state.viewMode != ViewMode.List) {
                val compact = state.viewMode == ViewMode.CompactGrid
                val gridState = rememberLazyGridState()
                ScrollToTopOnReselect(ShellTab.Library, gridState)
                LazyVerticalGrid(
                    columns = libraryGridColumns(compact),
                    state = gridState,
                    contentPadding = gridPadding(controls, top = S2Spacing.small),
                    horizontalArrangement = Arrangement.spacedBy(gridSpacing(compact)),
                    verticalArrangement = Arrangement.spacedBy(gridSpacing(compact)),
                    modifier = Modifier.fillMaxSize().testTag("library-albums"),
                ) {
                    controlsItem(controls, GridHorizontalPadding)
                    items(state.albums, key = { it.groupKey.toString() }) { album ->
                        GridTile(
                            title = album.name.orEmpty(),
                            subtitle = album.friendlyArtistName,
                            onClick = { onAlbumClick(album) },
                            onLongClick = { onAlbumLongClick(album) },
                            selected = album in state.selectedAlbums,
                            titleMaxLines = if (compact) 1 else null,
                            artwork = { LibraryArtwork(album, ArtworkPlaceholder.Album, Modifier.sharedArtwork(albumArtworkKey(album.route)).fillMaxSize(), size = ArtworkSize.Grid) },
                        )
                    }
                }
                fastScroller(rememberFastScrollableState(gridState))
            } else {
                val listState = rememberLazyListState()
                ScrollToTopOnReselect(ShellTab.Library, listState)
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize().testTag("library-albums")) {
                    controlsItem(controls)
                    items(state.albums, key = { it.groupKey.toString() }) { album ->
                        AlbumRow(
                            title = album.name.orEmpty(),
                            artist = album.friendlyArtistName.orEmpty(),
                            meta = album.year?.toString(),
                            onClick = { onAlbumClick(album) },
                            onLongClick = { onAlbumLongClick(album) },
                            onMore = { onAlbumMore(album) },
                            selected = album in state.selectedAlbums,
                            artwork = { LibraryArtwork(album, ArtworkPlaceholder.Album, Modifier.sharedArtwork(albumArtworkKey(album.route))) },
                        )
                    }
                }
                fastScroller(rememberFastScrollableState(listState))
            }
        }
    }
}
