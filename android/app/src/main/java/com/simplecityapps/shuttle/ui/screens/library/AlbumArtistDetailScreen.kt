package com.simplecityapps.shuttle.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.UnfoldLess
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.AlbumRow
import com.simplecityapps.shuttle.designsystem.component.ArtworkPlaceholder
import com.simplecityapps.shuttle.designsystem.component.ArtworkSize
import com.simplecityapps.shuttle.designsystem.component.S2Action
import com.simplecityapps.shuttle.designsystem.component.S2Button
import com.simplecityapps.shuttle.designsystem.component.S2ButtonStyle
import com.simplecityapps.shuttle.designsystem.component.S2IconButton
import com.simplecityapps.shuttle.designsystem.component.S2Menu
import com.simplecityapps.shuttle.designsystem.component.SectionHeader
import com.simplecityapps.shuttle.designsystem.component.SongRow
import com.simplecityapps.shuttle.designsystem.theme.ArtworkTheme
import com.simplecityapps.shuttle.designsystem.theme.S2TouchTarget
import com.simplecityapps.shuttle.designsystem.theme.isLargeText
import com.simplecityapps.shuttle.format.formatDuration
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.sorting.ArtistSongSortOrder
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
import kotlin.math.min

/**
 * Artist detail (inventory §1, #631): the songs in the app-wide artist sort, chosen from the songs header's menu. Grouped
 * by album, the song list is the albums (#678): each album's header unfolds its tracks inline when tapped (its thumbnail
 * opens the album), and stays pinned under the bar while its tracks scroll by; the header's Expand all / Collapse all
 * folds them all. Then Appears On (#637), a shelf of others' albums crediting them, after the artist's own albums
 * (#788), and the songs on none of their albums as Other Songs. Listed flat, the artist's albums come first as a shelf,
 * then Appears On, then every song. Play / Shuffle play all of them in the visible order; the overflow holds the
 * artist's actions plus Shuffle albums.
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
    val unfoldedSections = remember(uiState.sections, uiState.expandedAlbums) { unfoldedSectionsByKey(uiState) }
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
            // The shared rule's image (#781): the artist's own, else their top album's cover, full-bleed and square.
            artwork = uiState.hero,
            placeholder = ArtworkPlaceholder.Artist,
            bleed = true,
            onNavigateUp = onNavigateUp,
            onPlay = { onPlay(uiState.songs, 0) },
            onShuffle = onShuffle,
            onMore = { artist?.let(onArtistMore) },
            modifier = modifier.testTag("artist-detail"),
            listState = listState,
            overlay = { topInset -> PinnedAlbumHeader(listState, topInset, unfoldedSections, albumHeader) },
        ) {
            val appearsOn: LazyListScope.() -> Unit = {
                if (uiState.appearsOn.isNotEmpty()) {
                    albumShelf("artist-appears-on", appearsOnTitle, uiState.appearsOn, unknown, onAppearsOnClick, onAlbumMore)
                }
            }
            // Grouped by album, the song list's album headers are the albums (#678), and Appears On follows them (#788),
            // ahead of the songs on none of them. Otherwise the shelves come first: the artist's albums, then Appears On.
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
                        sortOrder = uiState.sortOrder,
                        // An album order with no album sections, only Other Songs, has nothing to fold (#636)
                        allExpanded = uiState.allAlbumsExpanded.takeIf { uiState.hasAlbumSections },
                        onSortOrderSelected = onSortOrderSelected,
                        onExpandAll = onExpandAll,
                        onCollapseAll = onCollapseAll,
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
                    val albumSongs = AlbumSongs(album, section.songs)
                    item(key = albumSongs.headerKey, contentType = "album") { albumHeader(albumSongs, Modifier) }
                    if (album.groupKey in uiState.expandedAlbums) {
                        itemsIndexed(section.songs, key = { _, song -> albumSongs.songKey(song) }, contentType = { _, _ -> "song" }) { index, song ->
                            SongRow(
                                title = song.name ?: unknown,
                                subtitle = song.artistUnlessAlbumArtist(album),
                                onClick = { onPlay(uiState.songs, startIndex + index) },
                                trackNumber = song.track,
                                duration = formatDuration(song.duration.toLong()),
                                playing = song.id == uiState.currentSong?.id,
                                onMore = { onSongMore(song) },
                            )
                        }
                    }
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

/** An album section of the song list: [album]'s header and its [songs], keyed apart from the page's other rows. */
private class AlbumSongs(val album: Album, val songs: List<Song>) {
    val headerKey: String get() = "album-${album.groupKey}"

    fun songKey(song: Song): String = "album-${album.groupKey}-song-${song.id}"
}

/** Every unfolded album section of [uiState]'s song list, by the key of its header and of each of its song rows. */
private fun unfoldedSectionsByKey(uiState: AlbumArtistDetailUiState): Map<Any, AlbumSongs> = buildMap {
    uiState.sections.forEach { section ->
        val album = section.album ?: return@forEach
        if (album.groupKey !in uiState.expandedAlbums) return@forEach
        val albumSongs = AlbumSongs(album, section.songs)
        put(albumSongs.headerKey, albumSongs)
        section.songs.forEach { put(albumSongs.songKey(it), albumSongs) }
    }
}

/**
 * An album section's header: the album, its year and song count; a tap folds or unfolds its songs, the thumbnail opens
 * the album, and a long press or the overflow opens its actions.
 */
@Composable
private fun AlbumSectionHeader(
    album: Album,
    songCount: Int,
    expanded: Boolean,
    unknown: String,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val foldState = stringResource(if (expanded) R.string.artist_detail_album_expanded else R.string.artist_detail_album_collapsed)
    AlbumRow(
        title = album.name ?: unknown,
        artist = listOfNotNull(album.year?.toString(), pluralString(R.plurals.songsPlural, songCount)).joinToString(" · "),
        onClick = onToggle,
        modifier = modifier.semantics { stateDescription = foldState },
        artwork = {
            val openAlbumLabel = stringResource(R.string.artist_detail_open_album, album.name ?: unknown)
            // The thumbnail opens the album; the rest of the row folds it (#631). The description alone names
            // the click, so it isn't announced twice.
            LibraryArtwork(
                album,
                ArtworkPlaceholder.Album,
                Modifier
                    .semantics { contentDescription = openAlbumLabel }
                    .clickable(onClick = onOpen),
                size = ArtworkSize.Medium,
            )
        },
        selected = expanded,
        onLongClick = onMore,
        onMore = onMore,
    )
}

/**
 * The header of the unfolded album whose rows are passing under the bar, pinned just below it (#631), as a sticky header
 * would be: the list runs up behind the bar, so a LazyColumn sticky header would pin out of sight. The next section's
 * first row pushes it up and away.
 */
@Composable
private fun BoxScope.PinnedAlbumHeader(
    listState: LazyListState,
    topInset: Dp,
    sectionsByKey: Map<Any, AlbumSongs>,
    header: @Composable (AlbumSongs, Modifier) -> Unit,
) {
    val obscuredPx = with(LocalDensity.current) { topInset.roundToPx() }
    val pinned by remember(listState, sectionsByKey, obscuredPx) {
        derivedStateOf { listState.pinnedSection(sectionsByKey, obscuredPx) }
    }
    var heightPx by remember { mutableIntStateOf(0) }
    val (section, nextOffset) = pinned ?: return
    Box(
        Modifier
            .padding(top = topInset)
            .fillMaxWidth()
            // Pushed up, it slides under the bar's lower edge rather than over the bar
            .clipToBounds()
            // A copy of the header in the list, which screen readers already have
            .semantics { hideFromAccessibility() }
            .testTag("artist-pinned-album"),
    ) {
        header(
            section,
            Modifier
                .onSizeChanged { heightPx = it.height }
                .offset { IntOffset(0, nextOffset?.let { min(0, it - obscuredPx - heightPx) } ?: 0) }
                .background(MaterialTheme.colorScheme.surface),
        )
    }
}

/**
 * The unfolded section in [sectionsByKey] whose rows are under the bar ([obscuredPx] from the top of the list), with the
 * offset of the first row after it on screen, if any; null while that section's own header is still in full view.
 */
private fun LazyListState.pinnedSection(sectionsByKey: Map<Any, AlbumSongs>, obscuredPx: Int): Pair<AlbumSongs, Int?>? {
    val visible = layoutInfo.visibleItemsInfo
    val under = visible.firstOrNull { it.offset + it.size > obscuredPx } ?: return null
    val section = sectionsByKey[under.key] ?: return null
    if (under.key == section.headerKey && under.offset >= obscuredPx) return null
    val next = visible.firstOrNull { it.index > under.index && sectionsByKey[it.key] !== section }
    return section to next?.offset
}

/** The artist sorts in menu order: each with its menu label and the sort button's short one. */
private enum class ArtistSort(val order: ArtistSongSortOrder, val label: Int, val shortLabel: Int) {
    AlbumNewest(ArtistSongSortOrder.AlbumNewest, R.string.artist_sort_album_newest, R.string.artist_sort_short_album_newest),
    AlbumOldest(ArtistSongSortOrder.AlbumOldest, R.string.artist_sort_album_oldest, R.string.artist_sort_short_album_oldest),
    AlbumTitle(ArtistSongSortOrder.AlbumTitle, R.string.artist_sort_album_title, R.string.artist_sort_short_album_title),
    SongTitle(ArtistSongSortOrder.SongTitle, R.string.artist_sort_song_title, R.string.artist_sort_short_song_title),
    MostPlayed(ArtistSongSortOrder.MostPlayed, R.string.artist_sort_most_played, R.string.artist_sort_most_played),
}

/**
 * The song list's header: "Albums" while album sections stand in for the albums (#678), else "Songs"; then the sort
 * menu, labelled with the current sort, and, when [allExpanded] isn't null (there are album sections), Expand all or
 * Collapse all.
 */
@Composable
private fun SongsHeader(
    title: String,
    sortOrder: ArtistSongSortOrder,
    allExpanded: Boolean?,
    onSortOrderSelected: (ArtistSongSortOrder) -> Unit,
    onExpandAll: () -> Unit,
    onCollapseAll: () -> Unit,
) {
    SectionHeader(title = title) {
        Box {
            var sorting by remember { mutableStateOf(false) }
            val current = ArtistSort.entries.firstOrNull { it.order == sortOrder } ?: ArtistSort.AlbumNewest
            val description = stringResource(R.string.library_sort_current, stringResource(current.label))
            // At large text the label gives way to the icon alone, as on the library tabs
            if (isLargeText()) {
                S2IconButton(
                    icon = Icons.Rounded.SwapVert,
                    contentDescription = description,
                    onClick = { sorting = true },
                    modifier = Modifier.testTag("artist-sort"),
                )
            } else {
                S2Button(
                    text = stringResource(current.shortLabel),
                    onClick = { sorting = true },
                    style = S2ButtonStyle.Text,
                    icon = Icons.Rounded.SwapVert,
                    modifier = Modifier
                        .heightIn(min = S2TouchTarget.minimum)
                        .semantics { contentDescription = description }
                        .testTag("artist-sort"),
                )
            }
            S2Menu(
                expanded = sorting,
                onDismissRequest = { sorting = false },
                groups = listOf(sortOptions(sortOrder, ArtistSort.entries.map { it.order to it.label }, onSortOrderSelected)),
            )
        }
        if (allExpanded != null) {
            S2IconButton(
                icon = if (allExpanded) Icons.Rounded.UnfoldLess else Icons.Rounded.UnfoldMore,
                contentDescription = stringResource(if (allExpanded) R.string.artist_detail_collapse_all else R.string.artist_detail_expand_all),
                onClick = if (allExpanded) onCollapseAll else onExpandAll,
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
            onSortOrderSelected = viewModel::onSortOrderSelected,
            onExpandAll = viewModel::onExpandAll,
            onCollapseAll = viewModel::onCollapseAll,
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
