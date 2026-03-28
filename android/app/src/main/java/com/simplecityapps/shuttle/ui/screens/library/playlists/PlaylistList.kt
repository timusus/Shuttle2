package com.simplecityapps.shuttle.ui.screens.library.playlists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.simplecityapps.shuttle.ui.common.components.HorizontalLoadingView
import com.simplecityapps.shuttle.ui.common.components.LoadingStatusIndicator

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
            LazyColumn(
                modifier = modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                if (uiState.smartPlaylists.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.playlists_title_smart_playlists),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                        )
                    }
                    items(uiState.smartPlaylists) { smartPlaylist ->
                        SmartPlaylistListItem(
                            smartPlaylist = smartPlaylist,
                            onSmartPlaylistClick = onSmartPlaylistClick,
                        )
                    }
                }
                if (uiState.playlists.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.playlists_title_playlists),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                        )
                    }
                    items(uiState.playlists) { playlist ->
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
        }
    }
}
