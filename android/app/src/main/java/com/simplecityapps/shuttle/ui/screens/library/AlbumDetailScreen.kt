package com.simplecityapps.shuttle.ui.screens.library

import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.ArtworkPlaceholder
import com.simplecityapps.shuttle.designsystem.component.S2Action
import com.simplecityapps.shuttle.designsystem.component.SectionHeader
import com.simplecityapps.shuttle.designsystem.component.SongRow
import com.simplecityapps.shuttle.designsystem.theme.ArtworkTheme
import com.simplecityapps.shuttle.format.formatDuration
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
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel

/** Album detail (inventory §1): the album's songs by disc, Play / Shuffle, the album's actions in the overflow, and More by the artist's other albums. */
@Composable
fun AlbumDetailScreen(
    uiState: AlbumDetailUiState,
    onNavigateUp: () -> Unit,
    onPlay: (songs: List<Song>, index: Int) -> Unit,
    onShuffle: () -> Unit,
    onAlbumMore: (Album) -> Unit,
    onSongMore: (Song) -> Unit,
    onOpenAlbum: (Album) -> Unit,
    onMoreByAlbumMore: (Album) -> Unit,
    modifier: Modifier = Modifier,
) {
    val album = uiState.album
    val state = when {
        uiState.loadingState == AlbumDetailUiState.LoadingState.Loading -> DetailContentState.Loading
        album == null -> DetailContentState.NotFound
        else -> DetailContentState.Ready
    }
    val songs = uiState.songs
    val unknown = stringResource(com.simplecityapps.core.R.string.unknown)
    val moreByTitle = stringResource(R.string.album_detail_more_by, album?.albumArtist ?: album?.friendlyArtistName.orEmpty())
    // The artwork tints the whole screen, as the player does, when Colour from artwork is on (#496).
    ArtworkTheme(uiState.seed) {
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
                        SectionHeader(title = stringResource(R.string.album_detail_disc, disc))
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
            if (uiState.moreByArtist.isNotEmpty()) {
                albumShelf(
                    key = "album-more-by",
                    title = moreByTitle,
                    albums = uiState.moreByArtist,
                    unknown = unknown,
                    onClick = onOpenAlbum,
                    onLongClick = onMoreByAlbumMore,
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
    onOpen: (NavKey) -> Unit,
    onNavigate: (NavigationTarget) -> Unit,
) {
    val resources = LocalResources.current
    val viewModel = assistedMetroViewModel<AlbumDetailViewModel, AlbumDetailViewModel.Factory> { create(route.groupKey) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    MediaActionsHost(onNavigate = onNavigate) { actions ->
        AlbumDetailScreen(
            uiState = uiState,
            onNavigateUp = onNavigateUp,
            onPlay = { songs, index -> actions.dispatch(MediaAction.Play(MediaSelection.Songs(songs), index, uiState.playContext)) },
            onShuffle = { actions.dispatch(MediaAction.Shuffle(MediaSelection.Songs(uiState.songs), uiState.playContext)) },
            onAlbumMore = { album -> actions.showActions(MediaActionsTarget(album.name.orEmpty(), album.friendlyArtistName, MediaSelection.Albums(album), ArtworkPlaceholder.Album)) },
            onSongMore = { song -> actions.showActions(MediaActionsTarget(song.name.orEmpty(), song.rowSubtitle, MediaSelection.Songs(song), ArtworkPlaceholder.Song)) },
            onOpenAlbum = { album -> onOpen(album.route) },
            onMoreByAlbumMore = { album ->
                actions.showActions(
                    MediaActionsTarget(
                        title = album.name.orEmpty(),
                        subtitle = album.friendlyArtistName,
                        selection = MediaSelection.Albums(album),
                        placeholder = ArtworkPlaceholder.Album,
                        extraActions = listOf(S2Action(resources.getString(R.string.menu_title_view_album), { onOpen(album.route) }, Icons.Rounded.Album)),
                    ),
                )
            },
        )
    }
}
