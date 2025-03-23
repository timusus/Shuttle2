package com.simplecityapps.shuttle.ui.screens.library.songs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.ui.common.components.CircularLoadingState
import com.simplecityapps.shuttle.ui.common.components.FastScroller
import com.simplecityapps.shuttle.ui.common.components.HorizontalLoadingView
import com.simplecityapps.shuttle.ui.common.components.LoadingStatusIndicator

@Composable
fun SongList(
    viewState: SongListViewModel.ViewState,
    modifier: Modifier = Modifier,
    onAddToQueue: (Song) -> Unit = {},
    onPlayNext: (Song) -> Unit = {},
    onSongInfo: (Song) -> Unit = {},
    onExclude: (Song) -> Unit = {},
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
                    onAddToQueue = onAddToQueue,
                    onPlayNext = onPlayNext,
                    onSongInfo = onSongInfo,
                    onExclude = onExclude,
                )
            }
        }
    }
}

@Composable
private fun SongList(
    songs: List<Song>,
    modifier: Modifier = Modifier,
    onAddToQueue: (Song) -> Unit = {},
    onPlayNext: (Song) -> Unit = {},
    onSongInfo: (Song) -> Unit = {},
    onExclude: (Song) -> Unit = {},
) {
    val state = rememberLazyListState()

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("genres-list-lazy-column"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp, horizontal = 8.dp),
            state = state,
        ) {
            items(songs) { song ->
                SongListItem(
                    song = song,
                    onAddToQueue = onAddToQueue,
                    onPlayNext = onPlayNext,
                    onSongInfo = onSongInfo,
                    onExclude = onExclude,
                )
            }
        }
        FastScroller(
            modifier = Modifier.fillMaxSize().padding(vertical = 8.dp),
            state = state,
            getPopupText = { index ->
                (songs)[index].name?.firstOrNull()?.toString() ?: "" // FIXME
            },
        )
    }
}
