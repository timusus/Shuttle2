package com.simplecityapps.shuttle.ui.screens.library

import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.HeartBroken
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.ArtworkPlaceholder
import com.simplecityapps.shuttle.designsystem.component.S2Action
import com.simplecityapps.shuttle.designsystem.component.formatDuration
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.ui.actions.MediaAction
import com.simplecityapps.shuttle.ui.actions.MediaSelection
import com.simplecityapps.shuttle.ui.actions.NavigationTarget
import com.simplecityapps.shuttle.ui.common.mediaactions.MediaActionsHost
import com.simplecityapps.shuttle.ui.common.mediaactions.MediaActionsTarget

/** A built-in smart playlist (inventory §1): its songs in the playlist's own order, Play / Shuffle, and their actions. */
@Composable
fun SmartPlaylistDetailScreen(
    uiState: SmartPlaylistDetailUiState,
    onNavigateUp: () -> Unit,
    onPlay: (songs: List<Song>, index: Int) -> Unit,
    onShuffle: () -> Unit,
    onPlaylistMore: () -> Unit,
    onSongMore: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    val playlist = uiState.smartPlaylist
    val state = when {
        uiState.loading -> DetailContentState.Loading
        playlist == null -> DetailContentState.NotFound
        else -> DetailContentState.Ready
    }
    LibraryDetailScaffold(
        state = state,
        title = playlist?.let { stringResource(it.nameResId) } ?: stringResource(com.simplecityapps.core.R.string.unknown),
        subtitle = playlist?.let { listOf(pluralString(R.plurals.songsPlural, uiState.songs.size), formatDuration(uiState.songs.sumOf { it.duration }.toLong())).joinToString(" · ") },
        artwork = null,
        placeholder = playlist?.placeholder ?: ArtworkPlaceholder.SmartPlaylist,
        onNavigateUp = onNavigateUp,
        onPlay = { onPlay(uiState.songs, 0) },
        onShuffle = onShuffle,
        onMore = onPlaylistMore,
        modifier = modifier.testTag("smart-playlist-detail"),
    ) {
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
fun SmartPlaylistDetailDestination(
    route: SmartPlaylistRoute,
    onNavigateUp: () -> Unit,
    onNavigate: (NavigationTarget) -> Unit,
) {
    val viewModel = hiltViewModel<SmartPlaylistDetailViewModel, SmartPlaylistDetailViewModel.Factory> { it.create(route.smartPlaylistId) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val title = uiState.smartPlaylist?.let { stringResource(it.nameResId) }.orEmpty()
    val placeholder = uiState.smartPlaylist?.placeholder ?: ArtworkPlaceholder.SmartPlaylist
    val isFavourites = route.smartPlaylistId == SmartPlaylistId.Favourites.id
    val removeFromFavourites = stringResource(R.string.menu_title_remove_from_favorites)
    MediaActionsHost(onNavigate = onNavigate) { actions ->
        SmartPlaylistDetailScreen(
            uiState = uiState,
            onNavigateUp = onNavigateUp,
            onPlay = { songs, index -> actions.dispatch(MediaAction.Play(MediaSelection.Songs(songs), index)) },
            onShuffle = { actions.dispatch(MediaAction.Shuffle(MediaSelection.Songs(uiState.songs))) },
            // A smart playlist is a query, not a stored playlist, so its sheet acts on the songs it currently holds.
            onPlaylistMore = { actions.showActions(MediaActionsTarget(title, null, MediaSelection.Songs(uiState.songs), placeholder)) },
            onSongMore = { song ->
                val selection = MediaSelection.Songs(song)
                // Favourites are a flag on the song, so taking one out of the list stops it being a favourite (#497)
                val extraActions = if (isFavourites) {
                    listOf(S2Action(removeFromFavourites, { actions.dispatch(MediaAction.Favourite(selection, favourite = false)) }, Icons.Rounded.HeartBroken))
                } else {
                    emptyList()
                }
                actions.showActions(MediaActionsTarget(song.name.orEmpty(), song.rowSubtitle, selection, ArtworkPlaceholder.Song, extraActions))
            },
        )
    }
}
