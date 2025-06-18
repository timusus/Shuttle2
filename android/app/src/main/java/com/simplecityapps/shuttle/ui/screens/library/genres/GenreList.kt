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
import com.simplecityapps.shuttle.model.Genre
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.sorting.PlaylistSongSortOrder
import com.simplecityapps.shuttle.ui.common.components.CircularLoadingState
import com.simplecityapps.shuttle.ui.common.components.FastScroller
import com.simplecityapps.shuttle.ui.common.components.HorizontalLoadingView
import com.simplecityapps.shuttle.ui.common.components.LoadingStatusIndicator
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData
import com.simplecityapps.shuttle.ui.theme.AppTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

@Composable
fun GenreList(
    viewState: GenreListViewModel.ViewState,
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
    when (viewState) {
        is GenreListViewModel.ViewState.Scanning -> {
            HorizontalLoadingView(
                modifier = modifier
                    .fillMaxSize()
                    .wrapContentSize()
                    .padding(16.dp),
                message = stringResource(R.string.library_scan_in_progress),
                progress = viewState.progress?.asFloat() ?: 0f
            )
        }

        is GenreListViewModel.ViewState.Loading -> {
            LoadingStatusIndicator(
                modifier = modifier
                    .fillMaxSize()
                    .wrapContentSize(),
                state = CircularLoadingState.Loading(stringResource(R.string.loading))
            )
        }

        is GenreListViewModel.ViewState.Ready -> {
            if (viewState.genres.isEmpty()) {
                LoadingStatusIndicator(
                    modifier = modifier
                        .fillMaxSize()
                        .wrapContentSize()
                        .padding(16.dp),
                    state = CircularLoadingState.Empty(stringResource(R.string.genre_list_empty))
                )
            } else {
                GenreList(
                    modifier = modifier,
                    genres = viewState.genres.toImmutableList(),
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
                viewState = GenreListViewModel.ViewState.Loading,
                playlists = samplePlaylists
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
                viewState = GenreListViewModel.ViewState.Scanning(progress = Progress(20, 205)),
                playlists = samplePlaylists
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
                viewState = GenreListViewModel.ViewState.Ready(genres = emptyList()),
                playlists = samplePlaylists
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
                viewState = GenreListViewModel.ViewState.Ready(
                    genres = sampleGenres
                ),
                playlists = samplePlaylists
            )
        }
    }
}

private val sampleGenres = listOf(
    Genre(
        name = "Rock",
        songCount = 245,
        duration = 14730,
        mediaProviders = listOf(MediaProviderType.Shuttle, MediaProviderType.Jellyfin)
    ),
    Genre(
        name = "Electronic",
        songCount = 156,
        duration = 9480,
        mediaProviders = listOf(MediaProviderType.Shuttle)
    ),
    Genre(
        name = "Jazz",
        songCount = 89,
        duration = 5340,
        mediaProviders = listOf(MediaProviderType.Jellyfin)
    ),
    Genre(
        name = "Hip-Hop",
        songCount = 198,
        duration = 11880,
        mediaProviders = listOf(MediaProviderType.Shuttle, MediaProviderType.Plex)
    ),
    Genre(
        name = "Classical",
        songCount = 67,
        duration = 8020,
        mediaProviders = listOf(MediaProviderType.Shuttle)
    ),
    Genre(
        name = "Pop",
        songCount = 312,
        duration = 18720,
        mediaProviders = listOf(MediaProviderType.Shuttle, MediaProviderType.Jellyfin, MediaProviderType.Plex)
    ),
    Genre(
        name = "Alternative",
        songCount = 134,
        duration = 8040,
        mediaProviders = listOf(MediaProviderType.Shuttle)
    ),
    Genre(
        name = "Blues",
        songCount = 45,
        duration = 2700,
        mediaProviders = listOf(MediaProviderType.Jellyfin)
    ),
    Genre(
        name = "Country",
        songCount = 78,
        duration = 4680,
        mediaProviders = listOf(MediaProviderType.Shuttle, MediaProviderType.Plex)
    ),
    Genre(
        name = "Reggae",
        songCount = 32,
        duration = 1920,
        mediaProviders = listOf(MediaProviderType.Shuttle)
    ),
    Genre(
        name = "Progressive Rock",
        songCount = 56,
        duration = 4480,
        mediaProviders = listOf(MediaProviderType.Shuttle, MediaProviderType.Jellyfin)
    ),
    Genre(
        name = "Ambient",
        songCount = 23,
        duration = 2760,
        mediaProviders = listOf(MediaProviderType.Shuttle)
    )
)

private val samplePlaylists = listOf(
    Playlist(
        id = 1L,
        name = "My Favorites",
        songCount = 25,
        duration = 1500,
        sortOrder = PlaylistSongSortOrder.Duration,
        mediaProvider = MediaProviderType.Shuttle,
        externalId = null
    ),
    Playlist(
        id = 2L,
        name = "Workout Mix",
        songCount = 32,
        duration = 1920,
        sortOrder = PlaylistSongSortOrder.Duration,
        mediaProvider = MediaProviderType.Shuttle,
        externalId = null
    ),
    Playlist(
        id = 3L,
        name = "Chill Vibes",
        songCount = 18,
        duration = 1080,
        sortOrder = PlaylistSongSortOrder.Duration,
        mediaProvider = MediaProviderType.Jellyfin,
        externalId = "playlist_123"
    )
).toImmutableList()
