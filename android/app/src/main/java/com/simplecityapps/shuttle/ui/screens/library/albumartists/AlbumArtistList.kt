package com.simplecityapps.shuttle.ui.screens.library.albumartists

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
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.ui.common.components.CircularLoadingState
import com.simplecityapps.shuttle.ui.common.components.FastScroller
import com.simplecityapps.shuttle.ui.common.components.rememberFastScrollableState
import com.simplecityapps.shuttle.ui.common.components.HorizontalLoadingView
import com.simplecityapps.shuttle.ui.common.components.LoadingStatusIndicator
import com.simplecityapps.shuttle.ui.common.utils.dp as dpToInt
import com.simplecityapps.shuttle.ui.screens.library.ViewMode
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet

@Composable
fun AlbumArtistList(
    uiState: AlbumArtistListUiState,
    playlists: ImmutableList<Playlist>,
    onArtistClick: (AlbumArtist) -> Unit,
    onArtistLongClick: (AlbumArtist) -> Unit,
    onPlay: (AlbumArtist) -> Unit,
    onAddToQueue: (AlbumArtist) -> Unit,
    onPlayNext: (AlbumArtist) -> Unit,
    onExclude: (AlbumArtist) -> Unit,
    onEditTags: (AlbumArtist) -> Unit,
    onAddToPlaylist: (playlist: Playlist, playlistData: PlaylistData) -> Unit,
    onShowCreatePlaylistDialog: (AlbumArtist) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (uiState.loadingState) {
        AlbumArtistListUiState.LoadingState.Scanning -> {
            HorizontalLoadingView(
                modifier = modifier
                    .fillMaxSize()
                    .wrapContentSize()
                    .padding(16.dp),
                message = stringResource(R.string.library_scan_in_progress),
                progress = uiState.scanProgress?.asFloat() ?: 0f,
            )
        }

        AlbumArtistListUiState.LoadingState.Loading -> {
            LoadingStatusIndicator(
                modifier = modifier
                    .fillMaxSize()
                    .wrapContentSize(),
                state = CircularLoadingState.Loading(stringResource(R.string.loading)),
            )
        }

        AlbumArtistListUiState.LoadingState.Empty -> {
            LoadingStatusIndicator(
                modifier = modifier
                    .fillMaxSize()
                    .wrapContentSize()
                    .padding(16.dp),
                state = CircularLoadingState.Empty(stringResource(R.string.artist_list_empty)),
            )
        }

        AlbumArtistListUiState.LoadingState.Ready -> {
            when (uiState.viewMode) {
                ViewMode.List -> AlbumArtistListView(
                    albumArtists = uiState.albumArtists.toImmutableList(),
                    selectedArtists = uiState.selectedArtists.toImmutableSet(),
                    playlists = playlists,
                    onArtistClick = onArtistClick,
                    onArtistLongClick = onArtistLongClick,
                    onPlay = onPlay,
                    onAddToQueue = onAddToQueue,
                    onPlayNext = onPlayNext,
                    onExclude = onExclude,
                    onEditTags = onEditTags,
                    onAddToPlaylist = onAddToPlaylist,
                    onShowCreatePlaylistDialog = onShowCreatePlaylistDialog,
                    modifier = modifier,
                )
                ViewMode.Grid -> AlbumArtistGridView(
                    albumArtists = uiState.albumArtists.toImmutableList(),
                    selectedArtists = uiState.selectedArtists.toImmutableSet(),
                    playlists = playlists,
                    onArtistClick = onArtistClick,
                    onArtistLongClick = onArtistLongClick,
                    onPlay = onPlay,
                    onAddToQueue = onAddToQueue,
                    onPlayNext = onPlayNext,
                    onExclude = onExclude,
                    onEditTags = onEditTags,
                    onAddToPlaylist = onAddToPlaylist,
                    onShowCreatePlaylistDialog = onShowCreatePlaylistDialog,
                    modifier = modifier,
                )
            }
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun AlbumArtistListView(
    albumArtists: ImmutableList<AlbumArtist>,
    selectedArtists: ImmutableSet<AlbumArtist>,
    playlists: ImmutableList<Playlist>,
    onArtistClick: (AlbumArtist) -> Unit,
    onArtistLongClick: (AlbumArtist) -> Unit,
    onPlay: (AlbumArtist) -> Unit,
    onAddToQueue: (AlbumArtist) -> Unit,
    onPlayNext: (AlbumArtist) -> Unit,
    onExclude: (AlbumArtist) -> Unit,
    onEditTags: (AlbumArtist) -> Unit,
    onAddToPlaylist: (playlist: Playlist, playlistData: PlaylistData) -> Unit,
    onShowCreatePlaylistDialog: (AlbumArtist) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberLazyListState()

    val preloadingData =
        rememberGlidePreloadingData(
            data = albumArtists,
            preloadImageSize = Size(40.dpToInt.toFloat(), 40.dpToInt.toFloat()),
        ) { item: AlbumArtist, requestBuilder: RequestBuilder<Drawable> ->
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
                .testTag("album-artists-list"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp, horizontal = 8.dp),
            state = state,
        ) {
            items(preloadingData.size) { index ->
                val (albumArtist, artworkPreloadRequestBuilder) = preloadingData[index]

                AlbumArtistListItem(
                    albumArtist = albumArtist,
                    isSelected = selectedArtists.contains(albumArtist),
                    playlists = playlists,
                    artworkPreloadRequestBuilder = artworkPreloadRequestBuilder,
                    onClick = onArtistClick,
                    onLongClick = onArtistLongClick,
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
            getPopupText = { index -> getArtistPopupText(albumArtists, index) },
        )
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun AlbumArtistGridView(
    albumArtists: ImmutableList<AlbumArtist>,
    selectedArtists: ImmutableSet<AlbumArtist>,
    playlists: ImmutableList<Playlist>,
    onArtistClick: (AlbumArtist) -> Unit,
    onArtistLongClick: (AlbumArtist) -> Unit,
    onPlay: (AlbumArtist) -> Unit,
    onAddToQueue: (AlbumArtist) -> Unit,
    onPlayNext: (AlbumArtist) -> Unit,
    onExclude: (AlbumArtist) -> Unit,
    onEditTags: (AlbumArtist) -> Unit,
    onAddToPlaylist: (playlist: Playlist, playlistData: PlaylistData) -> Unit,
    onShowCreatePlaylistDialog: (AlbumArtist) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyGridState()

    val preloadingData =
        rememberGlidePreloadingData(
            data = albumArtists,
            preloadImageSize = Size(120.dpToInt.toFloat(), 120.dpToInt.toFloat()),
        ) { item: AlbumArtist, requestBuilder: RequestBuilder<Drawable> ->
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
                .testTag("album-artists-grid"),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            state = gridState,
        ) {
            items(preloadingData.size) { index ->
                val (albumArtist, artworkPreloadRequestBuilder) = preloadingData[index]

                AlbumArtistGridItem(
                    albumArtist = albumArtist,
                    isSelected = selectedArtists.contains(albumArtist),
                    playlists = playlists,
                    artworkPreloadRequestBuilder = artworkPreloadRequestBuilder,
                    onClick = onArtistClick,
                    onLongClick = onArtistLongClick,
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
            getPopupText = { index -> getArtistPopupText(albumArtists, index) },
        )
    }
}

private fun getArtistPopupText(albumArtists: List<AlbumArtist>, index: Int): String {
    if (index !in albumArtists.indices) return ""
    return albumArtists[index].name?.firstOrNull()?.toString()
        ?: albumArtists[index].friendlyArtistName?.firstOrNull()?.toString()
        ?: ""
}
