package com.simplecityapps.shuttle.ui.screens.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistSortOrder
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.persistence.LibraryTab
import com.simplecityapps.shuttle.sorting.AlbumArtistSortOrder
import com.simplecityapps.shuttle.sorting.AlbumSortOrder
import com.simplecityapps.shuttle.sorting.GenreSortOrder
import com.simplecityapps.shuttle.sorting.SongSortOrder
import com.simplecityapps.shuttle.ui.actions.MediaAction
import com.simplecityapps.shuttle.ui.actions.MediaSelection
import com.simplecityapps.shuttle.ui.common.mediaactions.MediaActionsState
import com.simplecityapps.shuttle.ui.screens.library.albumartists.AlbumArtistListUiState
import com.simplecityapps.shuttle.ui.screens.library.albumartists.AlbumArtistListViewModel
import com.simplecityapps.shuttle.ui.screens.library.albums.AlbumListUiState
import com.simplecityapps.shuttle.ui.screens.library.albums.AlbumListViewModel
import com.simplecityapps.shuttle.ui.screens.library.genres.GenreListViewModel
import com.simplecityapps.shuttle.ui.screens.library.playlists.PlaylistListViewModel
import com.simplecityapps.shuttle.ui.screens.library.songs.SongListUiState
import com.simplecityapps.shuttle.ui.screens.library.songs.SongListViewModel
import dev.zacsweers.metrox.viewmodel.metroViewModel

/** A tab's controls row, read from the same ViewModel instance its page uses. */
@Composable
internal fun tabControls(tab: LibraryTab, actions: MediaActionsState): LibraryTabControls = when (tab) {
    LibraryTab.Songs -> {
        val viewModel: SongListViewModel = metroViewModel()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        val songs = state.songs.takeIf { state.loadingState == SongListUiState.LoadingState.Ready && it.isNotEmpty() }
        LibraryTabControls(
            count = pluralString(R.plurals.songsPlural, state.songs.size),
            sortOptions = sortOptions(
                state.sortOrder,
                listOf(
                    SongSortOrder.SongName to R.string.menu_title_sort_song_name,
                    SongSortOrder.ArtistGroupKey to R.string.menu_title_sort_artist_name,
                    SongSortOrder.AlbumGroupKey to R.string.menu_title_sort_album_name,
                    SongSortOrder.Year to R.string.menu_title_sort_year,
                    SongSortOrder.Duration to R.string.menu_title_sort_duration,
                    SongSortOrder.LastModified to R.string.menu_title_sort_date_modified,
                ),
                viewModel::setSortOrder,
            ),
            onPlay = songs?.let { { actions.dispatch(MediaAction.Play(MediaSelection.Songs(it))) } },
            onShuffle = songs?.let { { actions.dispatch(MediaAction.Shuffle(MediaSelection.Songs(it))) } },
        )
    }

    LibraryTab.Albums -> {
        val viewModel: AlbumListViewModel = metroViewModel()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        val albums = state.albums.takeIf { state.loadingState == AlbumListUiState.LoadingState.Ready && it.isNotEmpty() }
        LibraryTabControls(
            count = pluralString(R.plurals.albumsPlural, state.albums.size),
            sortOptions = sortOptions(
                state.sortOrder,
                listOf(
                    AlbumSortOrder.AlbumName to R.string.menu_title_sort_album_name,
                    AlbumSortOrder.ArtistGroupKey to R.string.menu_title_sort_artist_name,
                    AlbumSortOrder.Year to R.string.menu_title_sort_year,
                    AlbumSortOrder.Random to R.string.menu_title_sort_random,
                ),
                viewModel::setSortOrder,
            ),
            viewMode = state.viewMode,
            onViewModeChange = viewModel::setViewMode,
            onPlay = albums?.let { { actions.dispatch(MediaAction.Play(MediaSelection.Albums(it))) } },
            // Album-grouped shuffle has no MediaAction yet; the tab ViewModel keeps it.
            onShuffle = albums?.let { viewModel::onShuffle },
        )
    }

    LibraryTab.Artists -> {
        val viewModel: AlbumArtistListViewModel = metroViewModel()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        // Play and Shuffle take every listed artist's songs, artist by artist in the current sort.
        val artists = state.albumArtists.takeIf { state.loadingState == AlbumArtistListUiState.LoadingState.Ready && it.isNotEmpty() }
        LibraryTabControls(
            count = pluralString(R.plurals.library_count_artists, state.albumArtists.size),
            sortOptions = sortOptions(
                state.sortOrder,
                listOf(
                    AlbumArtistSortOrder.Default to R.string.menu_title_sort_artist_name,
                    AlbumArtistSortOrder.AlbumCount to R.string.menu_title_sort_album_count,
                ),
                viewModel::setSortOrder,
            ),
            viewMode = state.viewMode,
            onViewModeChange = viewModel::setViewMode,
            onPlay = artists?.let { { actions.dispatch(MediaAction.Play(MediaSelection.AlbumArtists(it))) } },
            onShuffle = artists?.let { { actions.dispatch(MediaAction.Shuffle(MediaSelection.AlbumArtists(it))) } },
        )
    }

    LibraryTab.Genres -> {
        val viewModel: GenreListViewModel = metroViewModel()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        LibraryTabControls(
            count = pluralString(R.plurals.library_count_genres, state.genres.size),
            sortOptions = sortOptions(
                state.sortOrder,
                listOf(GenreSortOrder.Default to R.string.menu_title_sort_genre_name, GenreSortOrder.SongCount to R.string.menu_title_sort_song_count),
                viewModel::setSortOrder,
            ),
        )
    }

    LibraryTab.Playlists -> {
        val viewModel: PlaylistListViewModel = metroViewModel()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        LibraryTabControls(
            count = pluralString(R.plurals.library_count_playlists, state.playlists.size),
            sortOptions = sortOptions(
                state.sortOrder,
                listOf(PlaylistSortOrder.Name to R.string.menu_title_sort_playlist_name, PlaylistSortOrder.Default to R.string.menu_title_sort_date_created),
                viewModel::setSortOrder,
            ),
        )
    }

    LibraryTab.Folders -> LibraryTabControls()
}
