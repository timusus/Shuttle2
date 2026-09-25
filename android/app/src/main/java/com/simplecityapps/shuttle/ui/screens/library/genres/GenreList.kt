package com.simplecityapps.shuttle.ui.screens.library.genres

import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.fixtures.SampleLibrary
import com.simplecityapps.shuttle.model.Genre
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.ui.common.components.CircularLoadingState
import com.simplecityapps.shuttle.ui.common.components.FastScroller
import com.simplecityapps.shuttle.ui.common.components.HorizontalLoadingView
import com.simplecityapps.shuttle.ui.common.components.LoadingStatusIndicator
import com.simplecityapps.shuttle.ui.preview.samplePlaylists
import com.simplecityapps.shuttle.ui.preview.toGenre
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData
import com.simplecityapps.shuttle.ui.theme.AppTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

@Composable
fun GenreList(
    uiState: GenreListUiState,
    playlists: ImmutableList<Playlist>,
    modifier: Modifier = Modifier,
    onSelectGenre: (genre: Genre) -> Unit = {},
    onPlayGenre: (Genre) -> Unit = {},
    onAddToQueue: (Genre) -> Unit = {},
    onPlayNext: (Genre) -> Unit = {},
    onExclude: (Genre) -> Unit = {},
    onEditTags: (Genre) -> Unit = {},
    onAddToPlaylist: (playlist: Playlist, playlistData: PlaylistData) -> Unit = { _, _ -> },
    onShowCreatePlaylistDialog: (genre: Genre) -> Unit = {}
) {
    when (uiState.loadingState) {
        GenreListUiState.LoadingState.Scanning -> {
            HorizontalLoadingView(
                modifier = modifier
                    .fillMaxSize()
                    .wrapContentSize()
                    .padding(16.dp),
                message = stringResource(R.string.library_scan_in_progress),
                progress = uiState.scanProgress?.asFloat() ?: 0f
            )
        }

        GenreListUiState.LoadingState.Loading -> {
            LoadingStatusIndicator(
                modifier = modifier
                    .fillMaxSize()
                    .wrapContentSize(),
                state = CircularLoadingState.Loading(stringResource(R.string.loading))
            )
        }

        GenreListUiState.LoadingState.Empty -> {
            LoadingStatusIndicator(
                modifier = modifier
                    .fillMaxSize()
                    .wrapContentSize()
                    .padding(16.dp),
                state = CircularLoadingState.Empty(stringResource(R.string.genre_list_empty))
            )
        }

        GenreListUiState.LoadingState.Ready -> {
            GenreList(
                modifier = modifier,
                genres = uiState.genres.toImmutableList(),
                playlists = playlists,
                onSelectGenre = onSelectGenre,
                onPlayGenre = onPlayGenre,
                onAddToQueue = onAddToQueue,
                onPlayNext = onPlayNext,
                onExclude = onExclude,
                onEditTags = onEditTags,
                onAddToPlaylist = onAddToPlaylist,
                onShowCreatePlaylistDialog = onShowCreatePlaylistDialog
            )
        }
    }
}

@Composable
private fun GenreList(
    genres: ImmutableList<Genre>,
    playlists: ImmutableList<Playlist>,
    onSelectGenre: (genre: Genre) -> Unit,
    onPlayGenre: (Genre) -> Unit,
    onAddToQueue: (Genre) -> Unit,
    onPlayNext: (Genre) -> Unit,
    onExclude: (Genre) -> Unit,
    onEditTags: (Genre) -> Unit,
    onAddToPlaylist: (playlist: Playlist, playlistData: PlaylistData) -> Unit,
    modifier: Modifier = Modifier,
    onShowCreatePlaylistDialog: (genre: Genre) -> Unit
) {
    val state = rememberLazyListState()

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("genres-list-lazy-column"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp, horizontal = 8.dp),
            state = state
        ) {
            items(genres) { genre ->
                GenreListItem(
                    genre = genre,
                    playlists = playlists,
                    onSelectGenre = onSelectGenre,
                    onPlayGenre = onPlayGenre,
                    onAddToQueue = onAddToQueue,
                    onPlayNext = onPlayNext,
                    onExclude = onExclude,
                    onEditTags = onEditTags,
                    onAddToPlaylist = onAddToPlaylist,
                    onShowCreatePlaylistDialog = onShowCreatePlaylistDialog
                )
            }
        }
        FastScroller(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 8.dp),
            state = state,
            getPopupText = { index ->
                (genres)[index].name.firstOrNull()?.toString()
            }
        )
    }
}

@PreviewLightDark
@Composable
private fun GenreListLoadingPreview() {
    AppTheme {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            GenreList(
                uiState = GenreListUiState(loadingState = GenreListUiState.LoadingState.Loading),
                playlists = previewPlaylists
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun GenreListScanningPreview() {
    AppTheme {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            GenreList(
                uiState = GenreListUiState(
                    loadingState = GenreListUiState.LoadingState.Scanning,
                    scanProgress = Progress(20, 205)
                ),
                playlists = previewPlaylists
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun GenreListEmptyPreview() {
    AppTheme {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            GenreList(
                uiState = GenreListUiState(loadingState = GenreListUiState.LoadingState.Empty),
                playlists = previewPlaylists
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun GenreListPreview() {
    AppTheme {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            GenreList(
                uiState = GenreListUiState(
                    genres = previewGenres,
                    loadingState = GenreListUiState.LoadingState.Ready
                ),
                playlists = previewPlaylists
            )
        }
    }
}

// Getters, not fields: a field would initialise with this file's production code, and release builds have no fixtures.
private val previewGenres get() = SampleLibrary.genres.map { it.toGenre() }

private val previewPlaylists get() = samplePlaylists().toImmutableList()
