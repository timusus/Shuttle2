package com.simplecityapps.shuttle.ui.screens.library.genres

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.shuttle.model.Genre
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.sorting.GenreSortOrder
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData

@Composable
fun GenreList(
    viewState: GenreListViewModel.ViewState,
    playlists: List<Playlist>,
    setToolbarMenu: (sortOrder: GenreSortOrder) -> Unit,
    setLoadingState: (GenreListFragment.LoadingState) -> Unit,
    setLoadingProgress: (progress: Progress?) -> Unit,
    onSelectGenre: (genre: Genre) -> Unit,
    onPlayGenre: (Genre) -> Unit,
    onAddToQueue: (Genre) -> Unit,
    onPlayNext: (Genre) -> Unit,
    onExclude: (Genre) -> Unit,
    onEditTags: (Genre) -> Unit,
    onAddToPlaylist: (playlist: Playlist, playlistData: PlaylistData) -> Unit,
    onShowCreatePlaylistDialog: (genre: Genre) -> Unit
) {
    when (viewState) {
        is GenreListViewModel.ViewState.Scanning -> {
            setLoadingState(GenreListFragment.LoadingState.Scanning)
            setLoadingProgress(viewState.progress)
        }

        is GenreListViewModel.ViewState.Loading -> {
            setLoadingState(GenreListFragment.LoadingState.Loading)
        }

        is GenreListViewModel.ViewState.Ready -> {
            if (viewState.genres.isEmpty()) {
                setLoadingState(GenreListFragment.LoadingState.Empty)
            } else {
                setLoadingState(GenreListFragment.LoadingState.None)
            }

            setToolbarMenu(viewState.sortOrder)

            GenreList(
                genres = viewState.genres,
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
    genres: List<Genre>,
    playlists: List<Playlist>,
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
            modifier = Modifier.fillMaxSize().padding(vertical = 8.dp),
            state = state,
            getPopupText = { index ->
                (genres)[index].name.firstOrNull()?.toString()
            }
        )
    }
}
