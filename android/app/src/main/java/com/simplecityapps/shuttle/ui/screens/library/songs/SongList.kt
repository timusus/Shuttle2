package com.simplecityapps.shuttle.ui.screens.library.songs

import android.graphics.drawable.Drawable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.simplecityapps.shuttle.R
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.rememberGlidePreloadingData
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.ui.common.components.CircularLoadingState
import com.simplecityapps.shuttle.sorting.SongSortOrder
import com.simplecityapps.shuttle.ui.common.components.FastScroller
import com.simplecityapps.shuttle.ui.common.components.HorizontalLoadingView
import com.simplecityapps.shuttle.ui.common.components.LoadingStatusIndicator
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData
import java.util.Locale
import com.simplecityapps.shuttle.ui.common.utils.dp as dpToInt

@Composable
fun SongList(
    viewState: SongListViewModel.ViewState,
    playlists: List<Playlist>,
    onSongClick: (Song) -> Unit,
    onSongLongClick: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onAddToPlaylist: (playlist: Playlist, playlistData: PlaylistData) -> Unit,
    onShowCreatePlaylistDialog: (song: Song) -> Unit,
    onPlayNext: (Song) -> Unit,
    onSongInfo: (Song) -> Unit,
    onExclude: (Song) -> Unit,
    onEditTags: (Song) -> Unit,
    onDelete: (Song) -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (viewState) {
        is SongListViewModel.ViewState.Scanning -> {
            HorizontalLoadingView(
                modifier = modifier
                    .fillMaxSize()
                    .wrapContentSize()
                    .padding(16.dp),
                message = stringResource(R.string.library_scan_in_progress),
                progress = viewState.progress?.asFloat() ?: 0f
            )
        }

        is SongListViewModel.ViewState.Loading -> {
            LoadingStatusIndicator(
                modifier = modifier
                    .fillMaxSize()
                    .wrapContentSize(),
                state = CircularLoadingState.Loading(stringResource(R.string.loading))
            )
        }

        is SongListViewModel.ViewState.Ready -> {
            if (viewState.songs.isEmpty()) {
                LoadingStatusIndicator(
                    modifier = modifier
                        .fillMaxSize()
                        .wrapContentSize()
                        .padding(16.dp),
                    state = CircularLoadingState.Empty(stringResource(R.string.song_list_empty))
                )
            } else {
                SongList(
                    songs = viewState.songs,
                    selectedSongs = viewState.selectedSongs,
                    sortOrder = viewState.sortOrder,
                    playlists = playlists,
                    onSongClick = onSongClick,
                    onSongLongClick = onSongLongClick,
                    onAddToQueue = onAddToQueue,
                    onAddToPlaylist = onAddToPlaylist,
                    onShowCreatePlaylistDialog = onShowCreatePlaylistDialog,
                    onPlayNext = onPlayNext,
                    onSongInfo = onSongInfo,
                    onExclude = onExclude,
                    onEditTags = onEditTags,
                    onDelete = onDelete,
                    onShuffle = onShuffle,
                )
            }
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun SongList(
    songs: List<Song>,
    selectedSongs: Set<Song>,
    sortOrder: SongSortOrder,
    playlists: List<Playlist>,
    onSongClick: (Song) -> Unit,
    onSongLongClick: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onAddToPlaylist: (playlist: Playlist, playlistData: PlaylistData) -> Unit,
    onShowCreatePlaylistDialog: (song: Song) -> Unit,
    onPlayNext: (Song) -> Unit,
    onSongInfo: (Song) -> Unit,
    onExclude: (Song) -> Unit,
    onEditTags: (Song) -> Unit,
    onDelete: (Song) -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberLazyListState()

    val preloadingData =
        rememberGlidePreloadingData(
            data = songs,
            preloadImageSize = Size(40.dpToInt.toFloat(), 40.dpToInt.toFloat()),
        ) { item: Song, requestBuilder: RequestBuilder<Drawable> ->
            requestBuilder
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .transform(CenterCrop())
                .transform(RoundedCorners(8.dpToInt))
                // Glide ignores this in Compose for now, but not a big deal
                .transition(withCrossFade(200))
                .load(item)
        }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("genres-list-lazy-column"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp, horizontal = 8.dp),
            state = state,
        ) {
            item {
                ShuffleListItem(onClick = onShuffle)
            }
            items(preloadingData.size) { index ->
                val (song, artworkPreloadRequestBuilder) = preloadingData[index]

                SongListItem(
                    song = song,
                    isSelected = selectedSongs.contains(song),
                    playlists = playlists,
                    artworkPreloadRequestBuilder = artworkPreloadRequestBuilder,
                    onClick = onSongClick,
                    onLongClick = onSongLongClick,
                    onAddToQueue = onAddToQueue,
                    onAddToPlaylist = onAddToPlaylist,
                    onShowCreatePlaylistDialog = onShowCreatePlaylistDialog,
                    onPlayNext = onPlayNext,
                    onSongInfo = onSongInfo,
                    onExclude = onExclude,
                    onEditTags = onEditTags,
                    onDelete = onDelete,
                )
            }
        }
        FastScroller(
            modifier = Modifier.fillMaxSize().padding(vertical = 8.dp),
            state = state,
            getPopupText = { index ->
                getFastscrollPopupText(songs[index], sortOrder)
            },
        )
    }
}

fun getFastscrollPopupText(song: Song, sortOrder: SongSortOrder): String = when (sortOrder) {
    SongSortOrder.SongName -> song.name?.firstOrNull()?.toString()
    SongSortOrder.ArtistGroupKey -> song.albumArtistGroupKey.key?.firstOrNull()?.toString()?.uppercase(Locale.getDefault())
    SongSortOrder.AlbumGroupKey -> song.albumGroupKey.key?.firstOrNull()?.toString()?.uppercase(Locale.getDefault())
    SongSortOrder.Year -> song.date?.year?.toString()
    else -> null
} ?: ""
