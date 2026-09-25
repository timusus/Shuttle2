package com.simplecityapps.shuttle.ui.screens.library.folders

import android.graphics.drawable.Drawable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.rememberGlidePreloadingData
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade
import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.fixtures.SampleLibrary
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.ui.common.components.CircularLoadingState
import com.simplecityapps.shuttle.ui.common.components.FastScroller
import com.simplecityapps.shuttle.ui.common.components.HorizontalLoadingView
import com.simplecityapps.shuttle.ui.common.components.LoadingStatusIndicator
import com.simplecityapps.shuttle.ui.common.utils.dp as dpToInt
import com.simplecityapps.shuttle.ui.screens.library.songs.SongListItem
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData
import com.simplecityapps.shuttle.ui.theme.AppTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

@Composable
fun FolderList(
    uiState: FolderListUiState,
    playlists: ImmutableList<Playlist>,
    modifier: Modifier = Modifier,
    onFolderClick: (Folder) -> Unit = {},
    onNavigateUp: () -> Unit = {},
    onPlayFolder: (Folder) -> Unit = {},
    onShuffleFolder: (Folder) -> Unit = {},
    onAddFolderToQueue: (Folder) -> Unit = {},
    onPlayFolderNext: (Folder) -> Unit = {},
    onSongClick: (Song) -> Unit = {},
    onAddSongToQueue: (Song) -> Unit = {},
    onPlaySongNext: (Song) -> Unit = {},
    onSongInfo: (Song) -> Unit = {},
    onExcludeSong: (Song) -> Unit = {},
    onEditSongTags: (Song) -> Unit = {},
    onDeleteSong: (Song) -> Unit = {},
    onAddToPlaylist: (playlist: Playlist, playlistData: PlaylistData) -> Unit = { _, _ -> },
    onShowCreatePlaylistDialog: (playlistData: PlaylistData) -> Unit = {}
) {
    when (uiState.loadingState) {
        FolderListUiState.LoadingState.Scanning -> {
            HorizontalLoadingView(
                modifier = modifier
                    .fillMaxSize()
                    .wrapContentSize()
                    .padding(16.dp),
                message = stringResource(R.string.library_scan_in_progress),
                progress = uiState.scanProgress?.asFloat() ?: 0f
            )
        }

        FolderListUiState.LoadingState.Loading -> {
            LoadingStatusIndicator(
                modifier = modifier
                    .fillMaxSize()
                    .wrapContentSize(),
                state = CircularLoadingState.Loading(stringResource(R.string.loading))
            )
        }

        FolderListUiState.LoadingState.Empty -> {
            LoadingStatusIndicator(
                modifier = modifier
                    .fillMaxSize()
                    .wrapContentSize()
                    .padding(16.dp),
                state = CircularLoadingState.Empty(stringResource(R.string.folder_list_empty))
            )
        }

        FolderListUiState.LoadingState.Ready -> {
            FolderList(
                modifier = modifier,
                currentFolder = uiState.currentFolder,
                folders = uiState.folders.toImmutableList(),
                songs = uiState.songs.toImmutableList(),
                playlists = playlists,
                onFolderClick = onFolderClick,
                onNavigateUp = onNavigateUp,
                onPlayFolder = onPlayFolder,
                onShuffleFolder = onShuffleFolder,
                onAddFolderToQueue = onAddFolderToQueue,
                onPlayFolderNext = onPlayFolderNext,
                onSongClick = onSongClick,
                onAddSongToQueue = onAddSongToQueue,
                onPlaySongNext = onPlaySongNext,
                onSongInfo = onSongInfo,
                onExcludeSong = onExcludeSong,
                onEditSongTags = onEditSongTags,
                onDeleteSong = onDeleteSong,
                onAddToPlaylist = onAddToPlaylist,
                onShowCreatePlaylistDialog = onShowCreatePlaylistDialog
            )
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun FolderList(
    currentFolder: Folder?,
    folders: ImmutableList<Folder>,
    songs: ImmutableList<Song>,
    playlists: ImmutableList<Playlist>,
    onFolderClick: (Folder) -> Unit,
    onNavigateUp: () -> Unit,
    onPlayFolder: (Folder) -> Unit,
    onShuffleFolder: (Folder) -> Unit,
    onAddFolderToQueue: (Folder) -> Unit,
    onPlayFolderNext: (Folder) -> Unit,
    onSongClick: (Song) -> Unit,
    onAddSongToQueue: (Song) -> Unit,
    onPlaySongNext: (Song) -> Unit,
    onSongInfo: (Song) -> Unit,
    onExcludeSong: (Song) -> Unit,
    onEditSongTags: (Song) -> Unit,
    onDeleteSong: (Song) -> Unit,
    onAddToPlaylist: (playlist: Playlist, playlistData: PlaylistData) -> Unit,
    onShowCreatePlaylistDialog: (playlistData: PlaylistData) -> Unit,
    modifier: Modifier = Modifier
) {
    // Each folder starts at the top of its list
    val state = key(currentFolder?.path?.joinToString("/")) { rememberLazyListState() }
    val resources = LocalContext.current.resources

    val preloadingData =
        rememberGlidePreloadingData(
            data = songs,
            preloadImageSize = Size(40.dpToInt.toFloat(), 40.dpToInt.toFloat()),
        ) { item: Song, requestBuilder: RequestBuilder<Drawable> ->
            requestBuilder
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .transform(CenterCrop(), RoundedCorners(8.dpToInt))
                .transition(withCrossFade(200))
                .load(item)
        }

    val headerCount = if (currentFolder != null) 1 else 0

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("folders-list-lazy-column"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp, horizontal = 8.dp),
            state = state
        ) {
            if (currentFolder != null) {
                item(key = "header") {
                    FolderHeader(folder = currentFolder, onNavigateUp = onNavigateUp)
                }
            }
            items(folders, key = { folder -> "folder:" + folder.path.joinToString("/") }) { folder ->
                FolderListItem(
                    folder = folder,
                    playlists = playlists,
                    onFolderClick = onFolderClick,
                    onPlayFolder = onPlayFolder,
                    onShuffleFolder = onShuffleFolder,
                    onAddToQueue = onAddFolderToQueue,
                    onPlayNext = onPlayFolderNext,
                    onAddToPlaylist = onAddToPlaylist,
                    onShowCreatePlaylistDialog = { onShowCreatePlaylistDialog(PlaylistData.Folders(listOf(it.path))) }
                )
            }
            items(preloadingData.size) { index ->
                val (song, artworkPreloadRequestBuilder) = preloadingData[index]

                SongListItem(
                    song = song,
                    isSelected = false,
                    playlists = playlists,
                    artworkPreloadRequestBuilder = artworkPreloadRequestBuilder,
                    onClick = onSongClick,
                    onAddToQueue = onAddSongToQueue,
                    onAddToPlaylist = onAddToPlaylist,
                    onShowCreatePlaylistDialog = { onShowCreatePlaylistDialog(PlaylistData.Songs(it)) },
                    onPlayNext = onPlaySongNext,
                    onSongInfo = onSongInfo,
                    onExclude = onExcludeSong,
                    onEditTags = onEditSongTags,
                    onDelete = onDeleteSong,
                )
            }
        }
        FastScroller(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 8.dp),
            state = state,
            getPopupText = { index ->
                val folderIndex = index - headerCount
                when {
                    folderIndex < 0 -> null
                    folderIndex < folders.size -> folders[folderIndex].displayName(resources).firstOrNull()?.toString()
                    else -> songs.getOrNull(folderIndex - folders.size)?.name?.firstOrNull()?.toString()
                }
            }
        )
    }
}

@Composable
private fun FolderHeader(
    folder: Folder,
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onNavigateUp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            modifier = Modifier
                .padding(8.dp)
                .size(24.dp),
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.folders_navigate_up),
            tint = MaterialTheme.colorScheme.onBackground
        )
        Text(
            modifier = Modifier
                .padding(start = 8.dp)
                .weight(1f),
            text = folder.displayPath(),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@PreviewLightDark
@Composable
private fun FolderListPreview() {
    AppTheme {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            FolderList(
                uiState = FolderListUiState(
                    currentFolder = Folder(path = listOf("primary", "Music"), songCount = SampleLibrary.songs.size),
                    folders = SampleLibrary.artists.map { Folder(path = listOf("primary", "Music", it.name), songCount = it.songCount) },
                    loadingState = FolderListUiState.LoadingState.Ready
                ),
                playlists = persistentListOf()
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun FolderListScanningPreview() {
    AppTheme {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            FolderList(
                uiState = FolderListUiState(
                    loadingState = FolderListUiState.LoadingState.Scanning,
                    scanProgress = Progress(20, 205)
                ),
                playlists = persistentListOf()
            )
        }
    }
}
