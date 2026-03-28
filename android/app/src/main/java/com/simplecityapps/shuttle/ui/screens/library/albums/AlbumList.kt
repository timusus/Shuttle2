package com.simplecityapps.shuttle.ui.screens.library.albums

import android.graphics.drawable.Drawable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.rememberGlidePreloadingData
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.sorting.AlbumSortOrder
import com.simplecityapps.shuttle.ui.common.components.CircularLoadingState
import com.simplecityapps.shuttle.ui.common.components.FastScroller
import com.simplecityapps.shuttle.ui.common.components.HorizontalLoadingView
import com.simplecityapps.shuttle.ui.common.components.LoadingStatusIndicator
import com.simplecityapps.shuttle.ui.common.components.NoPopup
import com.simplecityapps.shuttle.ui.common.components.rememberFastScrollableState
import com.simplecityapps.shuttle.ui.common.utils.dp as dpToInt
import com.simplecityapps.shuttle.ui.screens.library.ViewMode
import com.simplecityapps.shuttle.ui.screens.library.songs.ShuffleListItem
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData
import java.util.Locale
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet

@Composable
fun AlbumList(
    uiState: AlbumListUiState,
    playlists: ImmutableList<Playlist>,
    onAlbumClick: (Album) -> Unit,
    onAlbumLongClick: (Album) -> Unit,
    onPlay: (Album) -> Unit,
    onAddToQueue: (Album) -> Unit,
    onPlayNext: (Album) -> Unit,
    onExclude: (Album) -> Unit,
    onEditTags: (Album) -> Unit,
    onAddToPlaylist: (playlist: Playlist, playlistData: PlaylistData) -> Unit,
    onShowCreatePlaylistDialog: (Album) -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (uiState.loadingState) {
        AlbumListUiState.LoadingState.Scanning -> {
            HorizontalLoadingView(
                modifier = modifier
                    .fillMaxSize()
                    .wrapContentSize()
                    .padding(16.dp),
                message = stringResource(R.string.library_scan_in_progress),
                progress = uiState.scanProgress?.asFloat() ?: 0f,
            )
        }

        AlbumListUiState.LoadingState.Loading -> {
            LoadingStatusIndicator(
                modifier = modifier
                    .fillMaxSize()
                    .wrapContentSize(),
                state = CircularLoadingState.Loading(stringResource(R.string.loading)),
            )
        }

        AlbumListUiState.LoadingState.Empty -> {
            LoadingStatusIndicator(
                modifier = modifier
                    .fillMaxSize()
                    .wrapContentSize()
                    .padding(16.dp),
                state = CircularLoadingState.Empty(stringResource(R.string.album_list_empty)),
            )
        }

        AlbumListUiState.LoadingState.Ready -> {
            when (uiState.viewMode) {
                ViewMode.List -> AlbumListView(
                    albums = uiState.albums.toImmutableList(),
                    selectedAlbums = uiState.selectedAlbums.toImmutableSet(),
                    sortOrder = uiState.sortOrder,
                    playlists = playlists,
                    onAlbumClick = onAlbumClick,
                    onAlbumLongClick = onAlbumLongClick,
                    onPlay = onPlay,
                    onAddToQueue = onAddToQueue,
                    onPlayNext = onPlayNext,
                    onExclude = onExclude,
                    onEditTags = onEditTags,
                    onAddToPlaylist = onAddToPlaylist,
                    onShowCreatePlaylistDialog = onShowCreatePlaylistDialog,
                    onShuffle = onShuffle,
                    modifier = modifier,
                )
                ViewMode.Grid -> AlbumGridView(
                    albums = uiState.albums.toImmutableList(),
                    selectedAlbums = uiState.selectedAlbums.toImmutableSet(),
                    sortOrder = uiState.sortOrder,
                    playlists = playlists,
                    onAlbumClick = onAlbumClick,
                    onAlbumLongClick = onAlbumLongClick,
                    onPlay = onPlay,
                    onAddToQueue = onAddToQueue,
                    onPlayNext = onPlayNext,
                    onExclude = onExclude,
                    onEditTags = onEditTags,
                    onAddToPlaylist = onAddToPlaylist,
                    onShowCreatePlaylistDialog = onShowCreatePlaylistDialog,
                    onShuffle = onShuffle,
                    modifier = modifier,
                )
            }
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun AlbumListView(
    albums: ImmutableList<Album>,
    selectedAlbums: ImmutableSet<Album>,
    sortOrder: AlbumSortOrder,
    playlists: ImmutableList<Playlist>,
    onAlbumClick: (Album) -> Unit,
    onAlbumLongClick: (Album) -> Unit,
    onPlay: (Album) -> Unit,
    onAddToQueue: (Album) -> Unit,
    onPlayNext: (Album) -> Unit,
    onExclude: (Album) -> Unit,
    onEditTags: (Album) -> Unit,
    onAddToPlaylist: (playlist: Playlist, playlistData: PlaylistData) -> Unit,
    onShowCreatePlaylistDialog: (Album) -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberLazyListState()

    val preloadingData =
        rememberGlidePreloadingData(
            data = albums,
            preloadImageSize = Size(40.dpToInt.toFloat(), 40.dpToInt.toFloat()),
        ) { item: Album, requestBuilder: RequestBuilder<Drawable> ->
            requestBuilder
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .transform(CenterCrop(), RoundedCorners(8.dpToInt))
                .transition(withCrossFade(200))
                .load(item)
        }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("albums-list"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp, horizontal = 8.dp),
            state = state,
        ) {
            item {
                ShuffleListItem(onClick = onShuffle)
            }
            items(preloadingData.size) { index ->
                val (album, artworkPreloadRequestBuilder) = preloadingData[index]

                AlbumListItem(
                    album = album,
                    isSelected = selectedAlbums.contains(album),
                    playlists = playlists,
                    artworkPreloadRequestBuilder = artworkPreloadRequestBuilder,
                    onClick = onAlbumClick,
                    onLongClick = onAlbumLongClick,
                    onPlay = onPlay,
                    onAddToQueue = onAddToQueue,
                    onPlayNext = onPlayNext,
                    onExclude = onExclude,
                    onEditTags = onEditTags,
                    onAddToPlaylist = onAddToPlaylist,
                    onShowCreatePlaylistDialog = onShowCreatePlaylistDialog,
                )
            }
        }
        FastScroller(
            modifier = Modifier.fillMaxSize().padding(vertical = 8.dp),
            state = state,
            getPopupText = { index ->
                // Offset by 1 for the shuffle item
                val albumIndex = index - 1
                if (albumIndex in albums.indices) {
                    getAlbumPopupText(albums[albumIndex], sortOrder)
                } else {
                    ""
                }
            },
            popup = getAlbumFastscrollPopup(sortOrder),
        )
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun AlbumGridView(
    albums: ImmutableList<Album>,
    selectedAlbums: ImmutableSet<Album>,
    sortOrder: AlbumSortOrder,
    playlists: ImmutableList<Playlist>,
    onAlbumClick: (Album) -> Unit,
    onAlbumLongClick: (Album) -> Unit,
    onPlay: (Album) -> Unit,
    onAddToQueue: (Album) -> Unit,
    onPlayNext: (Album) -> Unit,
    onExclude: (Album) -> Unit,
    onEditTags: (Album) -> Unit,
    onAddToPlaylist: (playlist: Playlist, playlistData: PlaylistData) -> Unit,
    onShowCreatePlaylistDialog: (Album) -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyGridState()

    val preloadingData =
        rememberGlidePreloadingData(
            data = albums,
            preloadImageSize = Size(120.dpToInt.toFloat(), 120.dpToInt.toFloat()),
        ) { item: Album, requestBuilder: RequestBuilder<Drawable> ->
            requestBuilder
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .transform(CenterCrop())
                .transition(withCrossFade(200))
                .load(item)
        }

    Box(modifier = modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxSize()
                .testTag("albums-grid"),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            state = gridState,
        ) {
            items(preloadingData.size) { index ->
                val (album, artworkPreloadRequestBuilder) = preloadingData[index]

                AlbumGridItem(
                    album = album,
                    isSelected = selectedAlbums.contains(album),
                    playlists = playlists,
                    artworkPreloadRequestBuilder = artworkPreloadRequestBuilder,
                    onClick = onAlbumClick,
                    onLongClick = onAlbumLongClick,
                    onPlay = onPlay,
                    onAddToQueue = onAddToQueue,
                    onPlayNext = onPlayNext,
                    onExclude = onExclude,
                    onEditTags = onEditTags,
                    onAddToPlaylist = onAddToPlaylist,
                    onShowCreatePlaylistDialog = onShowCreatePlaylistDialog,
                )
            }
        }
        FastScroller(
            modifier = Modifier.fillMaxSize().padding(vertical = 8.dp),
            scrollableState = rememberFastScrollableState(gridState),
            getPopupText = { index ->
                if (index in albums.indices) {
                    getAlbumPopupText(albums[index], sortOrder)
                } else {
                    ""
                }
            },
            popup = getAlbumFastscrollPopup(sortOrder),
        )
    }
}

fun getAlbumPopupText(album: Album, sortOrder: AlbumSortOrder): String = when (sortOrder) {
    AlbumSortOrder.AlbumName,
    AlbumSortOrder.Default -> album.groupKey?.key?.firstOrNull()?.toString()?.uppercase(Locale.getDefault())
    AlbumSortOrder.ArtistGroupKey -> album.groupKey?.albumArtistGroupKey?.key?.firstOrNull()?.toString()?.uppercase(Locale.getDefault())
    AlbumSortOrder.Year -> album.year?.toString()
    else -> null
} ?: ""

fun getAlbumFastscrollPopup(sortOrder: AlbumSortOrder): @Composable ((Int) -> Unit)? = when (sortOrder) {
    AlbumSortOrder.AlbumName,
    AlbumSortOrder.ArtistGroupKey,
    AlbumSortOrder.Year,
    AlbumSortOrder.Default -> null
    else -> ::NoPopup
}
