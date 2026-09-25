package com.simplecityapps.shuttle.ui.screens.library

import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.ArtworkPlaceholder
import com.simplecityapps.shuttle.designsystem.component.SectionHeader
import com.simplecityapps.shuttle.designsystem.component.SongRow
import com.simplecityapps.shuttle.designsystem.component.formatDuration
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.ui.actions.MediaAction
import com.simplecityapps.shuttle.ui.actions.MediaSelection
import com.simplecityapps.shuttle.ui.actions.NavigationTarget
import com.simplecityapps.shuttle.ui.common.mediaactions.MediaActionsHost
import com.simplecityapps.shuttle.ui.common.mediaactions.MediaActionsTarget
import com.simplecityapps.shuttle.ui.screens.library.albums.detail.AlbumDetailUiState
import com.simplecityapps.shuttle.ui.screens.library.albums.detail.AlbumDetailViewModel
import com.simplecityapps.shuttle.ui.shell.AlbumRoute
import com.squareup.phrase.Phrase

/** Album detail (inventory §1): the album's songs by disc, Play / Shuffle, and the album's actions in the overflow. */
@Composable
fun AlbumDetailScreen(
    uiState: AlbumDetailUiState,
    onNavigateUp: () -> Unit,
    onPlay: (songs: List<Song>, index: Int) -> Unit,
    onShuffle: () -> Unit,
    onAlbumMore: (Album) -> Unit,
    onSongMore: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    val album = uiState.album
    val state = when {
        uiState.loadingState == AlbumDetailUiState.LoadingState.Loading -> DetailContentState.Loading
        album == null -> DetailContentState.NotFound
        else -> DetailContentState.Ready
    }
    val songs = uiState.songs
    LibraryDetailScaffold(
        state = state,
        title = album?.name ?: stringResource(com.simplecityapps.core.R.string.unknown),
        subtitle = album?.let { albumSubtitle(it) },
        artwork = album,
        placeholder = ArtworkPlaceholder.Album,
        onNavigateUp = onNavigateUp,
        onPlay = { onPlay(songs, 0) },
        onShuffle = onShuffle,
        onMore = { album?.let(onAlbumMore) },
        modifier = modifier.testTag("album-detail"),
    ) {
        val discs = songs.groupBy { it.disc ?: 1 }.toSortedMap()
        discs.forEach { (disc, discSongs) ->
            if (discs.size > 1) {
                item(key = "disc-$disc", contentType = "disc") {
                    SectionHeader(title = Phrase.from(stringResource(R.string.album_detail_disc)).put("disc", disc).format().toString())
                }
            }
            items(discSongs, key = { "song-${it.id}" }, contentType = { "song" }) { song ->
                SongRow(
                    title = song.name ?: stringResource(com.simplecityapps.core.R.string.unknown),
                    subtitle = song.friendlyArtistName.orEmpty(),
                    onClick = { onPlay(songs, songs.indexOf(song)) },
                    trackNumber = song.track,
                    duration = formatDuration(song.duration.toLong()),
                    playing = song.id == uiState.currentSong?.id,
                    onMore = { onSongMore(song) },
                )
            }
        }
    }
}

/** "Artist · year · N songs · duration". */
@Composable
private fun albumSubtitle(album: Album): String = listOfNotNull(
    album.friendlyArtistName,
    album.year?.toString(),
    pluralString(R.plurals.songsPlural, album.songCount),
    formatDuration(album.duration.toLong()),
).filter { it.isNotBlank() }.joinToString(" · ")

@Composable
fun AlbumDetailDestination(
    route: AlbumRoute,
    onNavigateUp: () -> Unit,
    onNavigate: (NavigationTarget) -> Unit,
) {
    val viewModel = hiltViewModel<AlbumDetailViewModel, AlbumDetailViewModel.Factory> { it.create(route.groupKey) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    MediaActionsHost(onNavigate = onNavigate) { actions ->
        AlbumDetailScreen(
            uiState = uiState,
            onNavigateUp = onNavigateUp,
            onPlay = { songs, index -> actions.dispatch(MediaAction.Play(MediaSelection.Songs(songs), index)) },
            onShuffle = { actions.dispatch(MediaAction.Shuffle(MediaSelection.Songs(uiState.songs))) },
            onAlbumMore = { album -> actions.showActions(MediaActionsTarget(album.name.orEmpty(), album.friendlyArtistName, MediaSelection.Albums(album), ArtworkPlaceholder.Album)) },
            onSongMore = { song -> actions.showActions(MediaActionsTarget(song.name.orEmpty(), song.rowSubtitle, MediaSelection.Songs(song), ArtworkPlaceholder.Song)) },
        )
    }
}
