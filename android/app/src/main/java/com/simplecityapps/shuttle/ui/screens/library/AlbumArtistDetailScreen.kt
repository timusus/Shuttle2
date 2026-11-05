package com.simplecityapps.shuttle.ui.screens.library

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.ArtworkPlaceholder
import com.simplecityapps.shuttle.designsystem.component.ArtworkSize
import com.simplecityapps.shuttle.designsystem.component.S2Action
import com.simplecityapps.shuttle.designsystem.component.SectionHeader
import com.simplecityapps.shuttle.designsystem.component.SongRow
import com.simplecityapps.shuttle.designsystem.theme.ArtworkTheme
import com.simplecityapps.shuttle.format.formatDuration
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.sorting.ArtistSongSortOrder
import com.simplecityapps.shuttle.ui.actions.MediaAction
import com.simplecityapps.shuttle.ui.actions.MediaSelection
import com.simplecityapps.shuttle.ui.actions.NavigationTarget
import com.simplecityapps.shuttle.ui.common.ConsumeEvents
import com.simplecityapps.shuttle.ui.common.downloads.offlineState
import com.simplecityapps.shuttle.ui.common.mediaactions.MediaActionsHost
import com.simplecityapps.shuttle.ui.common.mediaactions.MediaActionsTarget
import com.simplecityapps.shuttle.ui.screens.library.albumartists.detail.AlbumArtistDetailEvent
import com.simplecityapps.shuttle.ui.screens.library.albumartists.detail.AlbumArtistDetailUiState
import com.simplecityapps.shuttle.ui.screens.library.albumartists.detail.AlbumArtistDetailViewModel
import com.simplecityapps.shuttle.ui.shell.LocalShellSnackbarHostState
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel

/** Artist detail: album headers fold their tracks inline, and Appears On and Other Songs follow the albums. */
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
    onSortOrderSelected: (ArtistSongSortOrder) -> Unit = {},
    onExpandAll: () -> Unit = {},
    onCollapseAll: () -> Unit = {},
) {
    val artist = uiState.albumArtist
    val state = when {
        uiState.loadingState == AlbumArtistDetailUiState.LoadingState.Loading -> DetailContentState.Loading
        artist == null -> DetailContentState.NotFound
        else -> DetailContentState.Ready
    }
    val unknown = stringResource(com.simplecityapps.core.R.string.unknown)
    val albumsTitle = stringResource(R.string.artist_detail_albums)
    val appearsOnTitle = stringResource(R.string.artist_detail_appears_on)
    val listState = rememberLazyListState()
    // The unfolded album sections, by the key of each of their rows, for the header pinned over the list
    val albumSections = remember(uiState.sections) { uiState.sections.mapNotNull { section -> section.album?.let { AlbumSongs(it, section.songs) } } }
    val unfoldedSections = remember(albumSections, uiState.expandedAlbums) { albumSections.unfoldedByKey(uiState.expandedAlbums) }
    val albumHeader: @Composable (AlbumSongs, Modifier) -> Unit = { section, headerModifier ->
        AlbumSectionHeader(
            album = section.album,
            songCount = section.songs.size,
            expanded = section.album.groupKey in uiState.expandedAlbums,
            unknown = unknown,
            onToggle = { onToggleAlbum(section.album) },
            onOpen = { onOpenAlbum(section.album) },
            onMore = { onAlbumMore(section.album) },
            modifier = headerModifier,
        )
    }
    // The artwork tints the whole screen, as the player does, when Colour from artwork is on.
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
            // The artist's own image, else their top album's cover, full-bleed and square.
            artwork = uiState.hero,
            placeholder = ArtworkPlaceholder.Artist,
            bleed = true,
            onNavigateUp = onNavigateUp,
            onPlay = { onPlay(uiState.songs, 0) },
            onShuffle = onShuffle,
            onMore = { artist?.let(onArtistMore) },
            modifier = modifier.testTag("artist-detail"),
            listState = listState,
            overlay = { topInset -> PinnedAlbumHeader(listState, topInset, unfoldedSections, "artist-pinned-album", albumHeader) },
        ) {
            val appearsOn: LazyListScope.() -> Unit = {
                if (uiState.appearsOn.isNotEmpty()) {
                    albumShelf("artist-appears-on", appearsOnTitle, uiState.appearsOn, unknown, onAppearsOnClick, onAlbumMore)
                }
            }
            // With album sections, Appears On follows the album headers; otherwise the shelves come first.
            if (!uiState.hasAlbumSections) {
                if (uiState.showAlbumsShelf) {
                    albumShelf("artist-albums", albumsTitle, uiState.albums, unknown, onOpenAlbum, onAlbumMore, subtitle = { it.year?.toString() })
                }
                appearsOn()
            }
            if (uiState.songs.isNotEmpty()) {
                item(key = "songs-header", contentType = "header") {
                    SongsHeader(
                        title = stringResource(if (uiState.hasAlbumSections) R.string.artist_detail_albums else R.string.artist_detail_songs),
                        sort = uiState.sortOrder,
                        choices = ArtistSorts,
                        // An album order with no album sections, only Other Songs, has nothing to fold
                        allExpanded = uiState.allAlbumsExpanded.takeIf { uiState.hasAlbumSections },
                        onSortSelected = onSortOrderSelected,
                        onExpandAll = onExpandAll,
                        onCollapseAll = onCollapseAll,
                        sortTag = "artist-sort",
                    )
                }
            }
            // Each section's songs start at its offset in the play order, which runs across every section
            var offset = 0
            uiState.sections.forEach { section ->
                val startIndex = offset
                offset += section.songs.size
                val album = section.album
                if (album != null) {
                    albumSection(
                        section = AlbumSongs(album, section.songs),
                        expanded = album.groupKey in uiState.expandedAlbums,
                        startIndex = startIndex,
                        currentSong = uiState.currentSong,
                        unknown = unknown,
                        header = albumHeader,
                        onPlay = { onPlay(uiState.songs, it) },
                        onSongMore = { onSongMore(uiState.songs[it]) },
                    )
                } else {
                    if (uiState.hasAlbumSections) {
                        appearsOn()
                        item(key = "other-songs-header", contentType = "header") { SectionHeader(title = stringResource(R.string.artist_detail_other_songs)) }
                    }
                    itemsIndexed(section.songs, key = { _, song -> "song-${song.id}" }, contentType = { _, _ -> "song" }) { index, song ->
                        SongRow(
                            title = song.name ?: unknown,
                            subtitle = song.album.orEmpty(),
                            onClick = { onPlay(uiState.songs, startIndex + index) },
                            artwork = { LibraryArtwork(song, ArtworkPlaceholder.Song, size = ArtworkSize.Small) },
                            duration = formatDuration(song.duration.toLong()),
                            playing = song.id == uiState.currentSong?.id,
                            offlineState = song.offlineState(),
                            onMore = { onSongMore(song) },
                        )
                    }
                }
            }
            // Grouped with no songs off the artist's albums, Appears On closes the page
            if (uiState.hasAlbumSections && uiState.sections.none { it.album == null }) appearsOn()
        }
    }
}

/** The artist sorts in menu order. */
private val ArtistSorts = listOf(
    SortChoice(ArtistSongSortOrder.AlbumNewest, R.string.artist_sort_album_newest, R.string.artist_sort_short_album_newest),
    SortChoice(ArtistSongSortOrder.AlbumOldest, R.string.artist_sort_album_oldest, R.string.artist_sort_short_album_oldest),
    SortChoice(ArtistSongSortOrder.AlbumTitle, R.string.artist_sort_album_title, R.string.artist_sort_short_album_title),
    SortChoice(ArtistSongSortOrder.SongTitle, R.string.artist_sort_song_title, R.string.artist_sort_short_song_title),
    SortChoice(ArtistSongSortOrder.MostPlayed, R.string.artist_sort_most_played),
)

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
            onSortOrderSelected = viewModel::onSortOrderSelected,
            onExpandAll = viewModel::onExpandAll,
            onCollapseAll = viewModel::onCollapseAll,
            onOpenAlbum = { album -> onOpen(album.route) },
            onAlbumMore = { album -> actions.showActions(albumMoreTarget(album, resources.getString(R.string.menu_title_view_album)) { onOpen(album.route) }) },
            onSongMore = { song -> actions.showActions(MediaActionsTarget(song.name.orEmpty(), song.rowSubtitle, MediaSelection.Songs(song), ArtworkPlaceholder.Song)) },
            onAppearsOnClick = { album -> onOpen(album.route) },
        )
    }
}
