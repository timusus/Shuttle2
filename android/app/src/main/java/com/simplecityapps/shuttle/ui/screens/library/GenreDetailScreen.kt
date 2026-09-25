package com.simplecityapps.shuttle.ui.screens.library

import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.AlbumRow
import com.simplecityapps.shuttle.designsystem.component.ArtworkPlaceholder
import com.simplecityapps.shuttle.designsystem.component.ArtworkSize
import com.simplecityapps.shuttle.designsystem.component.SectionHeader
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.Genre
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.ui.actions.MediaAction
import com.simplecityapps.shuttle.ui.actions.MediaSelection
import com.simplecityapps.shuttle.ui.actions.NavigationTarget
import com.simplecityapps.shuttle.ui.common.mediaactions.MediaActionsHost
import com.simplecityapps.shuttle.ui.common.mediaactions.MediaActionsTarget
import com.simplecityapps.shuttle.ui.common.utils.toHms

/** Genre detail (inventory §1): the albums its songs come from, then every song. */
@Composable
fun GenreDetailScreen(
    uiState: GenreDetailUiState,
    onNavigateUp: () -> Unit,
    onPlay: (songs: List<Song>, index: Int) -> Unit,
    onShuffle: () -> Unit,
    onGenreMore: (Genre) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onAlbumMore: (Album) -> Unit,
    onSongMore: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    val genre = uiState.genre
    val state = when {
        uiState.loading -> DetailContentState.Loading
        genre == null -> DetailContentState.NotFound
        else -> DetailContentState.Ready
    }
    val unknown = stringResource(com.simplecityapps.core.R.string.unknown)
    LibraryDetailScaffold(
        state = state,
        title = genre?.name ?: unknown,
        subtitle = genre?.let { listOf(pluralString(R.plurals.songsPlural, uiState.songs.size), uiState.songs.sumOf { it.duration }.toHms().trim()).joinToString(" · ") },
        artwork = null,
        placeholder = ArtworkPlaceholder.Genre,
        onNavigateUp = onNavigateUp,
        onPlay = { onPlay(uiState.songs, 0) },
        onShuffle = onShuffle,
        onMore = { genre?.let(onGenreMore) },
        modifier = modifier.testTag("genre-detail"),
    ) {
        if (uiState.albums.isNotEmpty()) {
            item(key = "albums-header", contentType = "header") { SectionHeader(title = stringResource(R.string.artist_detail_albums)) }
        }
        items(uiState.albums, key = { "album-${it.groupKey}" }, contentType = { "album" }) { album ->
            AlbumRow(
                title = album.name ?: unknown,
                artist = album.friendlyArtistName.orEmpty(),
                onClick = { onAlbumClick(album) },
                artwork = { LibraryArtwork(album, ArtworkPlaceholder.Album, size = ArtworkSize.Medium) },
                onMore = { onAlbumMore(album) },
            )
        }
        if (uiState.songs.isNotEmpty()) {
            item(key = "songs-header", contentType = "header") { SectionHeader(title = stringResource(R.string.artist_detail_songs)) }
        }
        items(uiState.songs, key = { "song-${it.id}" }, contentType = { "song" }) { song ->
            LibrarySongRow(
                song = song,
                onClick = { onPlay(uiState.songs, uiState.songs.indexOf(song)) },
                playing = song.id == uiState.currentSong?.id,
                onMore = { onSongMore(song) },
            )
        }
    }
}

@Composable
fun GenreDetailDestination(
    route: GenreRoute,
    onNavigateUp: () -> Unit,
    onOpen: (NavKey) -> Unit,
    onNavigate: (NavigationTarget) -> Unit,
) {
    val viewModel = hiltViewModel<GenreDetailViewModel, GenreDetailViewModel.Factory> { it.create(route.genreName) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    MediaActionsHost(onNavigate = onNavigate) { actions ->
        GenreDetailScreen(
            uiState = uiState,
            onNavigateUp = onNavigateUp,
            onPlay = { songs, index -> actions.dispatch(MediaAction.Play(MediaSelection.Songs(songs), index)) },
            onShuffle = { actions.dispatch(MediaAction.Shuffle(MediaSelection.Songs(uiState.songs))) },
            onGenreMore = { genre -> actions.showActions(MediaActionsTarget(genre.name, null, MediaSelection.Genres(genre), ArtworkPlaceholder.Genre)) },
            onAlbumClick = { album -> onOpen(album.route) },
            onAlbumMore = { album -> actions.showActions(MediaActionsTarget(album.name.orEmpty(), album.friendlyArtistName, MediaSelection.Albums(album), ArtworkPlaceholder.Album)) },
            onSongMore = { song -> actions.showActions(MediaActionsTarget(song.name.orEmpty(), song.rowSubtitle, MediaSelection.Songs(song), ArtworkPlaceholder.Song)) },
        )
    }
}
