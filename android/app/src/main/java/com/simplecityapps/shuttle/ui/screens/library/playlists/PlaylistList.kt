package com.simplecityapps.shuttle.ui.screens.library.playlists

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.SmartPlaylist
import com.simplecityapps.shuttle.ui.common.components.CircularLoadingState
import com.simplecityapps.shuttle.ui.common.components.FastScroller
import com.simplecityapps.shuttle.ui.common.components.HorizontalLoadingView
import com.simplecityapps.shuttle.ui.common.components.LoadingStatusIndicator
import com.simplecityapps.shuttle.ui.common.components.NoPopup
import com.simplecityapps.shuttle.ui.common.components.noPopupText
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

@Composable
fun PlaylistList(
    uiState: PlaylistListUiState,
    modifier: Modifier = Modifier,
    onPlaylistClick: (Playlist) -> Unit = {},
    onSmartPlaylistClick: (SmartPlaylist) -> Unit = {},
    onPlay: (Playlist) -> Unit = {},
    onAddToQueue: (Playlist) -> Unit = {},
    onPlayNext: (Playlist) -> Unit = {},
    onDelete: (Playlist) -> Unit = {},
    onClear: (Playlist) -> Unit = {},
    onRename: (Playlist) -> Unit = {},
) {
    when (uiState.loadingState) {
        PlaylistListUiState.LoadingState.Scanning -> {
            HorizontalLoadingView(
                modifier = modifier
                    .fillMaxSize()
                    .wrapContentSize()
                    .padding(16.dp),
                message = stringResource(R.string.library_scan_in_progress),
                progress = uiState.scanProgress?.asFloat() ?: 0f
            )
        }

        PlaylistListUiState.LoadingState.Loading -> {
            LoadingStatusIndicator(
                modifier = modifier
                    .fillMaxSize()
                    .wrapContentSize(),
                state = CircularLoadingState.Loading(stringResource(R.string.loading))
            )
        }

        PlaylistListUiState.LoadingState.Empty -> {
            LoadingStatusIndicator(
                modifier = modifier
                    .fillMaxSize()
                    .wrapContentSize()
                    .padding(16.dp),
                state = CircularLoadingState.Empty(stringResource(R.string.playlist_list_empty))
            )
        }

        PlaylistListUiState.LoadingState.Ready -> {
            PlaylistList(
                smartPlaylists = uiState.smartPlaylists.toImmutableList(),
                playlists = uiState.playlists.toImmutableList(),
                onSmartPlaylistClick = onSmartPlaylistClick,
                onPlaylistClick = onPlaylistClick,
                onPlay = onPlay,
                onAddToQueue = onAddToQueue,
                onPlayNext = onPlayNext,
                onDelete = onDelete,
                onClear = onClear,
                onRename = onRename,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun PlaylistList(
    smartPlaylists: ImmutableList<SmartPlaylist>,
    playlists: ImmutableList<Playlist>,
    onSmartPlaylistClick: (SmartPlaylist) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onPlay: (Playlist) -> Unit,
    onAddToQueue: (Playlist) -> Unit,
    onPlayNext: (Playlist) -> Unit,
    onDelete: (Playlist) -> Unit,
    onClear: (Playlist) -> Unit,
    onRename: (Playlist) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberLazyListState()

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 8.dp),
            state = state,
        ) {
            if (smartPlaylists.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.playlists_title_smart_playlists),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                    )
                }
                items(smartPlaylists) { smartPlaylist ->
                    SmartPlaylistListItem(
                        smartPlaylist = smartPlaylist,
                        onSmartPlaylistClick = onSmartPlaylistClick,
                    )
                }
            }
            if (playlists.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.playlists_title_playlists),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                    )
                }
                items(playlists) { playlist ->
                    PlaylistListItem(
                        playlist = playlist,
                        onPlaylistClick = onPlaylistClick,
                        onPlay = onPlay,
                        onAddToQueue = onAddToQueue,
                        onPlayNext = onPlayNext,
                        onDelete = onDelete,
                        onClear = onClear,
                        onRename = onRename,
                    )
                }
            }
        }
        FastScroller(
            modifier = Modifier.fillMaxSize().padding(vertical = 8.dp),
            state = state,
            popup = ::NoPopup,
            getPopupText = ::noPopupText,
        )
    }
}
