package com.simplecityapps.shuttle.ui.screens.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.AlbumRow
import com.simplecityapps.shuttle.designsystem.component.ArtworkPlaceholder
import com.simplecityapps.shuttle.designsystem.component.ArtworkShape
import com.simplecityapps.shuttle.designsystem.component.ArtworkSize
import com.simplecityapps.shuttle.designsystem.component.GridTile
import com.simplecityapps.shuttle.designsystem.component.S2Action
import com.simplecityapps.shuttle.designsystem.component.SectionHeader
import com.simplecityapps.shuttle.designsystem.component.SongRow
import com.simplecityapps.shuttle.designsystem.theme.ArtworkTheme
import com.simplecityapps.shuttle.designsystem.theme.S2Spacing
import com.simplecityapps.shuttle.format.formatDuration
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.ui.actions.MediaAction
import com.simplecityapps.shuttle.ui.actions.MediaSelection
import com.simplecityapps.shuttle.ui.actions.NavigationTarget
import com.simplecityapps.shuttle.ui.common.ConsumeEvents
import com.simplecityapps.shuttle.ui.common.mediaactions.MediaActionsHost
import com.simplecityapps.shuttle.ui.common.mediaactions.MediaActionsTarget
import com.simplecityapps.shuttle.ui.screens.library.albumartists.detail.AlbumArtistDetailEvent
import com.simplecityapps.shuttle.ui.screens.library.albumartists.detail.AlbumArtistDetailUiState
import com.simplecityapps.shuttle.ui.screens.library.albumartists.detail.AlbumArtistDetailViewModel
import com.simplecityapps.shuttle.ui.shell.LocalShellSnackbarHostState
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel

/**
 * Artist detail (inventory §1): Appears On (#637), a row of others' albums crediting them, each opening its album, then the
 * songs in the artist sort order. Grouped by album, the song list is the albums (#678): each album's row unfolds its tracks
 * inline when tapped (its thumbnail opens the album), and songs on none of their albums trail as Other Songs. Flat, their own albums newest first come
 * first, unfolding the same way, and every song follows in one list. Play / Shuffle play all of them in that order; the
 * overflow holds the artist's actions plus Shuffle albums.
 */
@Composable
fun AlbumArtistDetailScreen(
    uiState: AlbumArtistDetailUiState,
    onNavigateUp: () -> Unit,
    onPlay: (songs: List<Song>, index: Int) -> Unit,
    onShuffle: () -> Unit,
    onArtistMore: (AlbumArtist) -> Unit,
    onToggleAlbum: (Album) -> Unit,
    onOpenAlbum: (Album) -> Unit,
    onAlbumMore: (Album) -> Unit,
    onSongMore: (Song) -> Unit,
    onAppearsOnClick: (Album) -> Unit,
    modifier: Modifier = Modifier,
) {
    val artist = uiState.albumArtist
    val state = when {
        uiState.loadingState == AlbumArtistDetailUiState.LoadingState.Loading -> DetailContentState.Loading
        artist == null -> DetailContentState.NotFound
        else -> DetailContentState.Ready
    }
    val unknown = stringResource(com.simplecityapps.core.R.string.unknown)
    val appearsOnTitle = stringResource(R.string.artist_detail_appears_on)
    // The artwork tints the whole screen, as the player does, when Colour from artwork is on (#496).
    ArtworkTheme(uiState.seed) {
        LibraryDetailScaffold(
            state = state,
            title = artist?.name ?: artist?.friendlyArtistName ?: unknown,
            // An artist only credited on others' albums has none of their own to count.
            subtitle = artist?.let {
                listOfNotNull(
                    uiState.albums.size.takeIf { it > 0 }?.let { pluralString(R.plurals.albumsPlural, it) },
                    pluralString(R.plurals.songsPlural, uiState.songs.size),
                ).joinToString(" · ")
            },
            artwork = artist,
            placeholder = ArtworkPlaceholder.Artist,
            artworkShape = ArtworkShape.Circle,
            onNavigateUp = onNavigateUp,
            onPlay = { onPlay(uiState.songs, 0) },
            onShuffle = onShuffle,
            onMore = { artist?.let(onArtistMore) },
            modifier = modifier.testTag("artist-detail"),
        ) {
            // Grouped by album, the song sections' album rows are the albums, so the Albums section above them goes (#678)
            if (uiState.showAlbumsShelf) {
                item(key = "albums-header", contentType = "header") { SectionHeader(title = stringResource(R.string.artist_detail_albums)) }
                uiState.albums.forEach { album ->
                    val albumSongs = uiState.songsForAlbum(album)
                    albumWithSongs(uiState, album, albumSongs, unknown, onToggleAlbum, onOpenAlbum, onAlbumMore, onSongMore) { song -> onPlay(albumSongs, albumSongs.indexOf(song)) }
                }
            }
            if (uiState.appearsOn.isNotEmpty()) {
                albumShelf("artist-appears-on", appearsOnTitle, uiState.appearsOn, unknown, onAppearsOnClick, onAlbumMore)
            }
            if (uiState.songs.isNotEmpty()) {
                val title = if (uiState.hasAlbumSections) R.string.artist_detail_albums_and_songs else R.string.artist_detail_songs
                item(key = "songs-header", contentType = "header") { SectionHeader(title = stringResource(title)) }
            }
            // Each section's songs start at its offset in the play order, which runs across every section
            var offset = 0
            uiState.sections.forEach { section ->
                val startIndex = offset
                offset += section.songs.size
                val album = section.album
                if (album != null) {
                    albumWithSongs(uiState, album, section.songs, unknown, onToggleAlbum, onOpenAlbum, onAlbumMore, onSongMore) { song ->
                        onPlay(uiState.songs, startIndex + section.songs.indexOf(song))
                    }
                } else {
                    if (uiState.hasAlbumSections) {
                        item(key = "other-songs-header", contentType = "header") { SectionHeader(title = stringResource(R.string.artist_detail_other_songs)) }
                    }
                    items(section.songs, key = { "song-${it.id}" }, contentType = { "song" }) { song ->
                        SongRow(
                            title = song.name ?: unknown,
                            subtitle = song.album.orEmpty(),
                            onClick = { onPlay(uiState.songs, startIndex + section.songs.indexOf(song)) },
                            artwork = { LibraryArtwork(song, ArtworkPlaceholder.Song, size = ArtworkSize.Small) },
                            duration = formatDuration(song.duration.toLong()),
                            playing = song.id == uiState.currentSong?.id,
                            onMore = { onSongMore(song) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * [album]'s row, which unfolds [songs] (in track order) under it when tapped; a song row's tap goes to [onPlaySong]. The Albums
 * section and the song list's album sections share it, keyed apart by whether the Albums section shows.
 */
private fun LazyListScope.albumWithSongs(
    uiState: AlbumArtistDetailUiState,
    album: Album,
    songs: List<Song>,
    unknown: String,
    onToggleAlbum: (Album) -> Unit,
    onOpenAlbum: (Album) -> Unit,
    onAlbumMore: (Album) -> Unit,
    onSongMore: (Song) -> Unit,
    onPlaySong: (Song) -> Unit,
) {
    val expanded = album.groupKey in uiState.expandedAlbums
    item(key = "album-${album.groupKey}", contentType = "album") {
        AlbumRow(
            title = album.name ?: unknown,
            artist = listOfNotNull(album.year?.toString(), pluralString(R.plurals.songsPlural, songs.size)).joinToString(" · "),
            onClick = { onToggleAlbum(album) },
            artwork = {
                val openAlbumLabel = stringResource(R.string.artist_detail_open_album, album.name ?: unknown)
                // The thumbnail opens the album; the rest of the row folds it (#631). The description alone names
                // the click, so it isn't announced twice.
                LibraryArtwork(
                    album,
                    ArtworkPlaceholder.Album,
                    Modifier
                        .semantics { contentDescription = openAlbumLabel }
                        .clickable { onOpenAlbum(album) },
                    size = ArtworkSize.Medium,
                )
            },
            selected = expanded,
            onMore = { onAlbumMore(album) },
        )
    }
    if (expanded) {
        items(songs, key = { "album-${album.groupKey}-song-${it.id}" }, contentType = { "song" }) { song ->
            SongRow(
                title = song.name ?: unknown,
                subtitle = song.friendlyArtistName.orEmpty(),
                onClick = { onPlaySong(song) },
                trackNumber = song.track,
                duration = formatDuration(song.duration.toLong()),
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
    val viewModel = assistedMetroViewModel<AlbumArtistDetailViewModel, AlbumArtistDetailViewModel.Factory> { create(route.groupKey) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    val snackbarHostState = LocalShellSnackbarHostState.current
    ConsumeEvents(uiState.events, viewModel::onEventHandled) { event ->
        when (event) {
            is AlbumArtistDetailEvent.ShuffleAlbumsFailed -> snackbarHostState.showSnackbar(
                resources.getString(R.string.shuffle_albums_failed, event.reason ?: resources.getString(R.string.error_unknown)),
            )
        }
    }
    MediaActionsHost(onNavigate = onNavigate) { actions ->
        AlbumArtistDetailScreen(
            uiState = uiState,
            onNavigateUp = onNavigateUp,
            onPlay = { songs, index -> actions.dispatch(MediaAction.Play(MediaSelection.Songs(songs), index, uiState.playContext)) },
            onShuffle = { actions.dispatch(MediaAction.Shuffle(MediaSelection.Songs(uiState.songs), uiState.playContext)) },
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
            onToggleAlbum = viewModel::onToggleAlbum,
            onOpenAlbum = { album -> onOpen(album.route) },
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
            onAppearsOnClick = { album -> onOpen(album.route) },
        )
    }
}
