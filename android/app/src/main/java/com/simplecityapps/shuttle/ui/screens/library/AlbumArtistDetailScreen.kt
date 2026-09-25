package com.simplecityapps.shuttle.ui.screens.library

import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.AlbumRow
import com.simplecityapps.shuttle.designsystem.component.ArtworkPlaceholder
import com.simplecityapps.shuttle.designsystem.component.ArtworkShape
import com.simplecityapps.shuttle.designsystem.component.ArtworkSize
import com.simplecityapps.shuttle.designsystem.component.S2Action
import com.simplecityapps.shuttle.designsystem.component.SectionHeader
import com.simplecityapps.shuttle.designsystem.component.SongRow
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.ui.actions.MediaAction
import com.simplecityapps.shuttle.ui.actions.MediaSelection
import com.simplecityapps.shuttle.ui.actions.NavigationTarget
import com.simplecityapps.shuttle.ui.common.mediaactions.MediaActionsHost
import com.simplecityapps.shuttle.ui.common.mediaactions.MediaActionsTarget
import com.simplecityapps.shuttle.ui.common.utils.toHms
import com.simplecityapps.shuttle.ui.screens.library.albumartists.detail.AlbumArtistDetailUiState
import com.simplecityapps.shuttle.ui.screens.library.albumartists.detail.AlbumArtistDetailViewModel

/**
 * Album artist detail (inventory §1): albums newest first, each expanding its tracks inline when tapped, then every
 * song. Play / Shuffle play all of them; the overflow holds the artist's actions plus Shuffle albums.
 */
@Composable
fun AlbumArtistDetailScreen(
    uiState: AlbumArtistDetailUiState,
    onNavigateUp: () -> Unit,
    onPlay: (songs: List<Song>, index: Int) -> Unit,
    onShuffle: () -> Unit,
    onArtistMore: (AlbumArtist) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onAlbumMore: (Album) -> Unit,
    onSongMore: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    val artist = uiState.albumArtist
    val state = when {
        uiState.loadingState == AlbumArtistDetailUiState.LoadingState.Loading -> DetailContentState.Loading
        artist == null -> DetailContentState.NotFound
        else -> DetailContentState.Ready
    }
    val unknown = stringResource(com.simplecityapps.core.R.string.unknown)
    LibraryDetailScaffold(
        state = state,
        title = artist?.name ?: artist?.friendlyArtistName ?: unknown,
        subtitle = artist?.let { listOf(pluralString(R.plurals.albumsPlural, uiState.albums.size), pluralString(R.plurals.songsPlural, uiState.songs.size)).joinToString(" · ") },
        artwork = artist,
        placeholder = ArtworkPlaceholder.Artist,
        artworkShape = ArtworkShape.Circle,
        onNavigateUp = onNavigateUp,
        onPlay = { onPlay(uiState.songs, 0) },
        onShuffle = onShuffle,
        onMore = { artist?.let(onArtistMore) },
        modifier = modifier.testTag("artist-detail"),
    ) {
        if (uiState.albums.isNotEmpty()) {
            item(key = "albums-header", contentType = "header") { SectionHeader(title = stringResource(R.string.artist_detail_albums)) }
        }
        uiState.albums.forEach { album ->
            val expanded = album.groupKey in uiState.expandedAlbums
            item(key = "album-${album.groupKey}", contentType = "album") {
                AlbumRow(
                    title = album.name ?: unknown,
                    artist = listOfNotNull(album.year?.toString(), pluralString(R.plurals.songsPlural, album.songCount)).joinToString(" · "),
                    onClick = { onAlbumClick(album) },
                    artwork = { LibraryArtwork(album, ArtworkPlaceholder.Album, size = ArtworkSize.Medium) },
                    selected = expanded,
                    onMore = { onAlbumMore(album) },
                )
            }
            if (expanded) {
                val albumSongs = uiState.songsForAlbum(album)
                items(albumSongs, key = { "album-${album.groupKey}-song-${it.id}" }, contentType = { "song" }) { song ->
                    SongRow(
                        title = song.name ?: unknown,
                        subtitle = song.friendlyArtistName.orEmpty(),
                        onClick = { onPlay(albumSongs, albumSongs.indexOf(song)) },
                        trackNumber = song.track,
                        duration = song.duration.toHms().trim(),
                        playing = song.id == uiState.currentSong?.id,
                        onMore = { onSongMore(song) },
                    )
                }
            }
        }
        if (uiState.songs.isNotEmpty()) {
            item(key = "songs-header", contentType = "header") { SectionHeader(title = stringResource(R.string.artist_detail_songs)) }
        }
        items(uiState.songs, key = { "song-${it.id}" }, contentType = { "song" }) { song ->
            SongRow(
                title = song.name ?: unknown,
                subtitle = song.album.orEmpty(),
                onClick = { onPlay(uiState.songs, uiState.songs.indexOf(song)) },
                artwork = { LibraryArtwork(song, ArtworkPlaceholder.Song, size = ArtworkSize.Small) },
                duration = song.duration.toHms().trim(),
                playing = song.id == uiState.currentSong?.id,
                onMore = { onSongMore(song) },
            )
        }
    }
}

@Composable
fun AlbumArtistDetailDestination(
    route: AlbumArtistRoute,
    onNavigateUp: () -> Unit,
    onOpen: (NavKey) -> Unit,
    onNavigate: (NavigationTarget) -> Unit,
) {
    val viewModel = hiltViewModel<AlbumArtistDetailViewModel, AlbumArtistDetailViewModel.Factory> { it.create(route.groupKey) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    MediaActionsHost(onNavigate = onNavigate) { actions ->
        AlbumArtistDetailScreen(
            uiState = uiState,
            onNavigateUp = onNavigateUp,
            onPlay = { songs, index -> actions.dispatch(MediaAction.Play(MediaSelection.Songs(songs), index)) },
            onShuffle = { actions.dispatch(MediaAction.Shuffle(MediaSelection.Songs(uiState.songs))) },
            onArtistMore = { artist ->
                actions.showActions(
                    MediaActionsTarget(
                        title = artist.name ?: artist.friendlyArtistName.orEmpty(),
                        subtitle = null,
                        selection = MediaSelection.AlbumArtists(artist),
                        placeholder = ArtworkPlaceholder.Artist,
                        // Album-grouped shuffle has no MediaAction yet; the detail ViewModel keeps it.
                        extraActions = listOf(S2Action(resources.getString(R.string.detail_shuffle_albums), viewModel::onShuffleAlbums, Icons.Rounded.Shuffle)),
                    ),
                )
            },
            onAlbumClick = viewModel::onAlbumClick,
            onAlbumMore = { album ->
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
            onSongMore = { song -> actions.showActions(MediaActionsTarget(song.name.orEmpty(), song.rowSubtitle, MediaSelection.Songs(song), ArtworkPlaceholder.Song)) },
        )
    }
}
