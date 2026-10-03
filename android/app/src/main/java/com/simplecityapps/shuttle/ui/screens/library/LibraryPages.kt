package com.simplecityapps.shuttle.ui.screens.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.AlbumRow
import com.simplecityapps.shuttle.designsystem.component.ArtistRow
import com.simplecityapps.shuttle.designsystem.component.ArtworkPlaceholder
import com.simplecityapps.shuttle.designsystem.component.ArtworkShape
import com.simplecityapps.shuttle.designsystem.component.ArtworkSize
import com.simplecityapps.shuttle.designsystem.component.FolderEntryKind
import com.simplecityapps.shuttle.designsystem.component.FolderRow
import com.simplecityapps.shuttle.designsystem.component.GenreRow
import com.simplecityapps.shuttle.designsystem.component.GridTile
import com.simplecityapps.shuttle.designsystem.component.PlaylistRow
import com.simplecityapps.shuttle.designsystem.component.S2Text
import com.simplecityapps.shuttle.designsystem.component.SectionHeader
import com.simplecityapps.shuttle.designsystem.component.SongRow
import com.simplecityapps.shuttle.designsystem.theme.S2Spacing
import com.simplecityapps.shuttle.format.formatDuration
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.Genre
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.SmartPlaylist
import com.simplecityapps.shuttle.model.SmartPlaylistId
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.sorting.AlbumArtistSortOrder
import com.simplecityapps.shuttle.sorting.LetterSection
import com.simplecityapps.shuttle.sorting.SongSortOrder
import com.simplecityapps.shuttle.sorting.letterSections
import com.simplecityapps.shuttle.sorting.songLetterKey
import com.simplecityapps.shuttle.ui.common.components.AlphabetFastScroller
import com.simplecityapps.shuttle.ui.common.components.FastScrollableState
import com.simplecityapps.shuttle.ui.common.components.FastScroller
import com.simplecityapps.shuttle.ui.common.components.NoPopup
import com.simplecityapps.shuttle.ui.common.components.rememberFastScrollableState
import com.simplecityapps.shuttle.ui.screens.library.albumartists.AlbumArtistListUiState
import com.simplecityapps.shuttle.ui.screens.library.albums.AlbumListUiState
import com.simplecityapps.shuttle.ui.screens.library.albums.albumThumbLabel
import com.simplecityapps.shuttle.ui.screens.library.folders.Folder
import com.simplecityapps.shuttle.ui.screens.library.folders.FolderListUiState
import com.simplecityapps.shuttle.ui.screens.library.folders.displayName
import com.simplecityapps.shuttle.ui.screens.library.folders.displayPath
import com.simplecityapps.shuttle.ui.screens.library.genres.GenreListUiState
import com.simplecityapps.shuttle.ui.screens.library.playlists.PlaylistListUiState
import com.simplecityapps.shuttle.ui.screens.library.songs.SongListUiState
import com.simplecityapps.shuttle.ui.screens.library.songs.songThumbLabel
import com.simplecityapps.shuttle.ui.text.stringResource as stringResourceKey

// The library tabs' pages: state in, events out, restyled with catalogue rows. The ViewModels are the existing
// tab ViewModels; LibraryScreen wires them.

/**
 * Fills the page so the scroller's track sits at its end edge, as the legacy lists have it. The track starts below the
 * page's [controls] row, the list's first item (#669), so the resting thumb never covers its Play or Shuffle button.
 */
private fun fastScrollerModifier(controls: LibraryTabControls?) = Modifier
    .fillMaxSize()
    .padding(top = S2Spacing.small + controlsRowHeight(controls), bottom = S2Spacing.small)
    .testTag("library-fast-scroller")

/**
 * The fast scroller for a page of [items]: by first letter over [sections] when the sort is by a name (#491), else a
 * plain thumb with [thumbLabel] in its popup, or none. The list leads with the
 * page's [controls] row, which the scroller's indices skip.
 */
@Composable
private fun <T> LibraryFastScroller(
    items: List<T>,
    sections: List<LetterSection>?,
    scrollableState: FastScrollableState,
    thumbLabel: ((T) -> String?)? = null,
    controls: LibraryTabControls? = null,
) {
    val itemOffset = controlsItemCount(controls)
    if (sections != null) {
        AlphabetFastScroller(sections, scrollableState, fastScrollerModifier(controls), itemOffset)
    } else {
        FastScroller(
            getPopupText = { index -> items.getOrNull(index - itemOffset)?.let { thumbLabel?.invoke(it) } },
            scrollableState = scrollableState,
            modifier = fastScrollerModifier(controls),
            popup = if (thumbLabel == null) ::NoPopup else null,
        )
    }
}

/** Each smart playlist's own placeholder, so the four don't share one icon (#491). */
internal val SmartPlaylist.placeholder: ArtworkPlaceholder
    get() = when (id) {
        SmartPlaylistId.Favourites -> ArtworkPlaceholder.Favorites
        SmartPlaylistId.RecentlyAdded -> ArtworkPlaceholder.RecentlyAdded
        SmartPlaylistId.MostPlayed -> ArtworkPlaceholder.MostPlayed
        SmartPlaylistId.History -> ArtworkPlaceholder.History
    }

/** The catalogue's compact grid: two columns of tiles on a phone, more as the width allows. */
private val LibraryGridColumns = GridCells.Adaptive(minSize = 160.dp)

private val GridHorizontalPadding = S2Spacing.medium

/** A grid's padding: [top] above the first tiles, or none when the controls row leads the grid. */
private fun gridPadding(controls: LibraryTabControls?, top: Dp) = PaddingValues(
    start = GridHorizontalPadding,
    end = GridHorizontalPadding,
    top = if (controlsItemCount(controls) > 0) 0.dp else top,
    bottom = S2Spacing.medium,
)

/** Songs: every song. Tap plays from that row; long-press selects. */
@Composable
fun SongsPage(
    state: SongListUiState,
    onSongClick: (Song) -> Unit,
    onSongLongClick: (Song) -> Unit,
    onSongMore: (Song) -> Unit,
    modifier: Modifier = Modifier,
    controls: LibraryTabControls? = null,
) {
    val content = when (state.loadingState) {
        SongListUiState.LoadingState.Loading -> LibraryContentState.Loading
        SongListUiState.LoadingState.Scanning -> LibraryContentState.Scanning
        SongListUiState.LoadingState.Empty -> LibraryContentState.Empty
        SongListUiState.LoadingState.Ready -> LibraryContentState.Ready
    }
    LibraryContent(content, stringResource(R.string.song_list_empty), modifier, state.scanProgress, controls) {
        val listState = rememberLazyListState()
        val byAlbum = state.sortOrder == SongSortOrder.AlbumGroupKey || state.sortOrder == SongSortOrder.Default
        val entries = remember(state.songs, byAlbum) { songEntries(state.songs, byAlbum) }
        Box(modifier.fillMaxSize()) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().testTag("library-songs")) {
                controlsItem(controls)
                items(entries, key = SongEntry::key, contentType = { it::class }) { entry ->
                    when (entry) {
                        is SongEntry.AlbumHeader -> SongAlbumHeader(entry.song)

                        is SongEntry.Row -> LibrarySongRow(
                            song = entry.song,
                            selected = entry.song in state.selectedSongs,
                            onClick = { onSongClick(entry.song) },
                            onLongClick = { onSongLongClick(entry.song) },
                            onMore = { onSongMore(entry.song) },
                            underAlbumHeader = byAlbum,
                        )
                    }
                }
            }
            // Indexed over the entries rather than state.letterIndex: the album headers of a by-album sort shift each
            // song's position in the list.
            val sections = remember(entries, state.sortOrder) {
                songLetterKey(state.sortOrder)?.let { key -> letterSections(entries) { entry -> key(entry.song) } }
            }
            LibraryFastScroller(
                items = entries,
                sections = sections,
                scrollableState = rememberFastScrollableState(listState),
                thumbLabel = songThumbLabel(state.sortOrder)?.let { label -> { entry: SongEntry -> label(entry.song) } },
                controls = controls,
            )
        }
    }
}

/**
 * A row of the Songs page. Sorted by album, each album's songs follow an [AlbumHeader] that shows its cover once, rather
 * than every row repeating it (#491).
 */
private sealed interface SongEntry {
    val song: Song
    val key: Any

    /** The album [song] opens. */
    data class AlbumHeader(override val song: Song) : SongEntry {
        override val key: Any get() = "album-${song.id}"
    }

    data class Row(override val song: Song) : SongEntry {
        override val key: Any get() = song.id
    }
}

/** [songs] as rows, with an album header wherever the album changes when [byAlbum]. */
private fun songEntries(songs: List<Song>, byAlbum: Boolean): List<SongEntry> = if (!byAlbum) {
    songs.map(SongEntry::Row)
} else {
    buildList {
        songs.forEachIndexed { index, song ->
            if (index == 0 || songs[index - 1].albumGroupKey != song.albumGroupKey) add(SongEntry.AlbumHeader(song))
            add(SongEntry.Row(song))
        }
    }
}

/** The cover, name and album artist of the album the rows under it belong to. */
@Composable
private fun SongAlbumHeader(song: Song) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = S2Spacing.medium, end = S2Spacing.medium, top = S2Spacing.medium, bottom = S2Spacing.xsmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(S2Spacing.medium),
    ) {
        LibraryArtwork(song, ArtworkPlaceholder.Album, size = ArtworkSize.Medium)
        Column(Modifier.weight(1f)) {
            S2Text(song.album.orEmpty(), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            song.albumArtist?.let {
                S2Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * A song in a list: artwork and "artist · album" outside its album; under an album header, the track number and the
 * artist, since the header already shows the cover and album.
 */
@Composable
fun LibrarySongRow(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    playing: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onMore: (() -> Unit)? = null,
    underAlbumHeader: Boolean = false,
) {
    SongRow(
        title = song.name.orEmpty(),
        subtitle = if (underAlbumHeader) (song.friendlyArtistName ?: song.albumArtist).orEmpty() else song.rowSubtitle,
        onClick = onClick,
        modifier = modifier,
        artwork = if (underAlbumHeader) null else ({ LibraryArtwork(song, ArtworkPlaceholder.Song, size = ArtworkSize.Small) }),
        trackNumber = if (underAlbumHeader) song.track else null,
        duration = formatDuration(song.duration.toLong()),
        playing = playing,
        selected = selected,
        onLongClick = onLongClick,
        onMore = onMore,
    )
}

/** Albums: a grid by default (#491) or a list. */
@Composable
fun AlbumsPage(
    state: AlbumListUiState,
    onAlbumClick: (Album) -> Unit,
    onAlbumLongClick: (Album) -> Unit,
    onAlbumMore: (Album) -> Unit,
    modifier: Modifier = Modifier,
    controls: LibraryTabControls? = null,
) {
    val content = when (state.loadingState) {
        AlbumListUiState.LoadingState.Loading -> LibraryContentState.Loading
        AlbumListUiState.LoadingState.Scanning -> LibraryContentState.Scanning
        AlbumListUiState.LoadingState.Empty -> LibraryContentState.Empty
        AlbumListUiState.LoadingState.Ready -> LibraryContentState.Ready
    }
    LibraryContent(content, stringResource(R.string.album_list_empty), modifier, state.scanProgress, controls) {
        val fastScroller: @Composable (FastScrollableState) -> Unit = { scrollableState ->
            LibraryFastScroller(state.albums, state.letterIndex, scrollableState, thumbLabel = albumThumbLabel(state.sortOrder), controls = controls)
        }
        Box(modifier.fillMaxSize()) {
            if (state.viewMode == ViewMode.Grid) {
                val gridState = rememberLazyGridState()
                LazyVerticalGrid(
                    columns = LibraryGridColumns,
                    state = gridState,
                    contentPadding = gridPadding(controls, top = S2Spacing.small),
                    horizontalArrangement = Arrangement.spacedBy(S2Spacing.small),
                    verticalArrangement = Arrangement.spacedBy(S2Spacing.small),
                    modifier = Modifier.fillMaxSize().testTag("library-albums"),
                ) {
                    controlsItem(controls, GridHorizontalPadding)
                    items(state.albums, key = { it.groupKey.toString() }) { album ->
                        GridTile(
                            title = album.name.orEmpty(),
                            subtitle = album.friendlyArtistName,
                            onClick = { onAlbumClick(album) },
                            onLongClick = { onAlbumLongClick(album) },
                            selected = album in state.selectedAlbums,
                            artwork = { LibraryArtwork(album, ArtworkPlaceholder.Album, Modifier.fillMaxSize(), size = ArtworkSize.Grid) },
                        )
                    }
                }
                fastScroller(rememberFastScrollableState(gridState))
            } else {
                val listState = rememberLazyListState()
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize().testTag("library-albums")) {
                    controlsItem(controls)
                    items(state.albums, key = { it.groupKey.toString() }) { album ->
                        AlbumRow(
                            title = album.name.orEmpty(),
                            artist = album.friendlyArtistName.orEmpty(),
                            meta = album.year?.toString(),
                            onClick = { onAlbumClick(album) },
                            onLongClick = { onAlbumLongClick(album) },
                            onMore = { onAlbumMore(album) },
                            selected = album in state.selectedAlbums,
                            artwork = { LibraryArtwork(album, ArtworkPlaceholder.Album) },
                        )
                    }
                }
                fastScroller(rememberFastScrollableState(listState))
            }
        }
    }
}

/** Album artists: list or adaptive grid. */
@Composable
fun ArtistsPage(
    state: AlbumArtistListUiState,
    onArtistClick: (AlbumArtist) -> Unit,
    onArtistLongClick: (AlbumArtist) -> Unit,
    onArtistMore: (AlbumArtist) -> Unit,
    modifier: Modifier = Modifier,
    controls: LibraryTabControls? = null,
) {
    val content = when (state.loadingState) {
        AlbumArtistListUiState.LoadingState.Loading -> LibraryContentState.Loading
        AlbumArtistListUiState.LoadingState.Scanning -> LibraryContentState.Scanning
        AlbumArtistListUiState.LoadingState.Empty -> LibraryContentState.Empty
        AlbumArtistListUiState.LoadingState.Ready -> LibraryContentState.Ready
    }
    LibraryContent(content, stringResource(R.string.artist_list_empty), modifier, state.scanProgress, controls) {
        val artists = state.albumArtists
        val fastScroller: @Composable (FastScrollableState) -> Unit = { scrollableState ->
            LibraryFastScroller(artists, state.letterIndex, scrollableState, thumbLabel = artistThumbLabel(state.sortOrder), controls = controls)
        }
        Box(modifier.fillMaxSize()) {
            if (state.viewMode == ViewMode.Grid) {
                val gridState = rememberLazyGridState()
                LazyVerticalGrid(
                    columns = LibraryGridColumns,
                    state = gridState,
                    contentPadding = gridPadding(controls, top = S2Spacing.medium),
                    horizontalArrangement = Arrangement.spacedBy(S2Spacing.small),
                    verticalArrangement = Arrangement.spacedBy(S2Spacing.small),
                    modifier = Modifier.fillMaxSize().testTag("library-artists"),
                ) {
                    controlsItem(controls, GridHorizontalPadding)
                    items(artists, key = { it.groupKey.toString() }) { artist ->
                        GridTile(
                            title = artist.name ?: artist.friendlyArtistName.orEmpty(),
                            subtitle = pluralString(R.plurals.albumsPlural, artist.albumCount),
                            onClick = { onArtistClick(artist) },
                            onLongClick = { onArtistLongClick(artist) },
                            selected = artist in state.selectedArtists,
                            artwork = { LibraryArtwork(artist, ArtworkPlaceholder.Artist, Modifier.fillMaxSize(), size = ArtworkSize.Grid, shape = ArtworkShape.Circle) },
                        )
                    }
                }
                fastScroller(rememberFastScrollableState(gridState))
            } else {
                val listState = rememberLazyListState()
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize().testTag("library-artists")) {
                    controlsItem(controls)
                    items(artists, key = { it.groupKey.toString() }) { artist ->
                        ArtistRow(
                            name = artist.name ?: artist.friendlyArtistName.orEmpty(),
                            summary = "${pluralString(R.plurals.albumsPlural, artist.albumCount)} · ${pluralString(R.plurals.songsPlural, artist.songCount)}",
                            onClick = { onArtistClick(artist) },
                            onLongClick = { onArtistLongClick(artist) },
                            onMore = { onArtistMore(artist) },
                            selected = artist in state.selectedArtists,
                            artwork = { LibraryArtwork(artist, ArtworkPlaceholder.Artist, shape = ArtworkShape.Circle) },
                        )
                    }
                }
                fastScroller(rememberFastScrollableState(listState))
            }
        }
    }
}

/** The popup label of a plain-thumb artists scroll: the album count a count sort orders by; none under the name sort, which has letter sections. */
private fun artistThumbLabel(sortOrder: AlbumArtistSortOrder): ((AlbumArtist) -> String?)? = when (sortOrder) {
    AlbumArtistSortOrder.AlbumCount -> { artist -> artist.albumCount.toString() }
    AlbumArtistSortOrder.Default, AlbumArtistSortOrder.PlayCount -> null
}

/** Genres: a list, no multi-select (inventory §1). */
@Composable
fun GenresPage(
    state: GenreListUiState,
    onGenreClick: (Genre) -> Unit,
    onGenreMore: (Genre) -> Unit,
    modifier: Modifier = Modifier,
    controls: LibraryTabControls? = null,
) {
    val content = when (state.loadingState) {
        GenreListUiState.LoadingState.Loading -> LibraryContentState.Loading
        GenreListUiState.LoadingState.Scanning -> LibraryContentState.Scanning
        GenreListUiState.LoadingState.Empty -> LibraryContentState.Empty
        GenreListUiState.LoadingState.Ready -> LibraryContentState.Ready
    }
    LibraryContent(content, stringResource(R.string.genre_list_empty), modifier, state.scanProgress, controls) {
        val listState = rememberLazyListState()
        val leadingItems = controlsItemCount(controls)
        Box(modifier.fillMaxSize()) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().testTag("library-genres")) {
                controlsItem(controls)
                items(state.genres, key = { it.name }) { genre ->
                    GenreRow(
                        name = genre.name,
                        songCount = pluralString(R.plurals.songsPlural, genre.songCount),
                        onClick = { onGenreClick(genre) },
                        onMore = { onGenreMore(genre) },
                        artwork = { LibraryArtwork(null, ArtworkPlaceholder.Genre) },
                    )
                }
            }
            FastScroller(modifier = fastScrollerModifier(controls), getPopupText = { index -> state.genres.getOrNull(index - leadingItems)?.name?.firstOrNull()?.uppercase() }, state = listState)
        }
    }
}

/** Playlists: the smart playlists, Favorites first, pinned above the user's, with "New playlist" in their header. */
@Composable
fun PlaylistsPage(
    state: PlaylistListUiState,
    onPlaylistClick: (Playlist) -> Unit,
    onPlaylistMore: (Playlist) -> Unit,
    onSmartPlaylistClick: (SmartPlaylist) -> Unit,
    onNewPlaylist: () -> Unit,
    modifier: Modifier = Modifier,
    controls: LibraryTabControls? = null,
) {
    val content = when (state.loadingState) {
        PlaylistListUiState.LoadingState.Loading -> LibraryContentState.Loading

        PlaylistListUiState.LoadingState.Scanning -> LibraryContentState.Scanning

        // Smart playlists are always there, so an empty list still shows them and "New playlist".
        PlaylistListUiState.LoadingState.Ready -> LibraryContentState.Ready
    }
    LibraryContent(content, stringResource(R.string.playlist_list_empty), modifier, state.scanProgress, controls) {
        val listState = rememberLazyListState()
        val headerCount = controlsItemCount(controls) + 1 + state.smartPlaylists.size + 1
        Box(modifier.fillMaxSize()) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().testTag("library-playlists")) {
                controlsItem(controls)
                item(key = "smart-header") { SectionHeader(title = stringResource(R.string.library_smart_playlists)) }
                items(state.smartPlaylists, key = { "smart-${it.id}" }) { smartPlaylist ->
                    PlaylistRow(
                        name = stringResourceKey(smartPlaylist.id.nameKey),
                        onClick = { onSmartPlaylistClick(smartPlaylist) },
                        artwork = { LibraryArtwork(null, smartPlaylist.placeholder) },
                    )
                }
                item(key = "playlists-header") {
                    SectionHeader(
                        title = stringResource(R.string.library_playlists),
                        action = stringResource(R.string.playlist_menu_create_playlist),
                        onAction = onNewPlaylist,
                    )
                }
                items(state.playlists, key = { it.id }) { playlist ->
                    PlaylistRow(
                        name = playlist.name,
                        summary = pluralString(R.plurals.songsPlural, playlist.songCount),
                        onClick = { onPlaylistClick(playlist) },
                        onMore = { onPlaylistMore(playlist) },
                        artwork = { CoverMosaic(state.covers[playlist.id].orEmpty(), ArtworkPlaceholder.Playlist) },
                    )
                }
            }
            FastScroller(modifier = fastScrollerModifier(controls), getPopupText = { index -> state.playlists.getOrNull(index - headerCount)?.name?.firstOrNull()?.uppercase() }, state = listState)
        }
    }
}

/** Folders: the current folder's subfolders, then its songs; a header shows the path with an Up action. */
@Composable
fun FoldersPage(
    state: FolderListUiState,
    onFolderClick: (Folder) -> Unit,
    onFolderMore: (Folder) -> Unit,
    onSongClick: (Song) -> Unit,
    onSongMore: (Song) -> Unit,
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val content = when (state.loadingState) {
        FolderListUiState.LoadingState.Loading -> LibraryContentState.Loading
        FolderListUiState.LoadingState.Scanning -> LibraryContentState.Scanning
        FolderListUiState.LoadingState.Empty -> LibraryContentState.Empty
        FolderListUiState.LoadingState.Ready -> LibraryContentState.Ready
    }
    LibraryContent(content, stringResource(R.string.folder_list_empty), modifier, state.scanProgress) {
        LazyColumn(modifier = modifier.fillMaxSize().testTag("library-folders")) {
            state.currentFolder?.let { folder ->
                item(key = "path") {
                    SectionHeader(
                        title = folder.displayPath(),
                        action = stringResource(R.string.library_navigate_up),
                        onAction = onNavigateUp,
                    )
                }
            }
            items(state.folders, key = { "folder-" + it.path.joinToString("/") }) { folder ->
                FolderRow(
                    name = folder.displayName(),
                    kind = FolderEntryKind.Folder,
                    summary = pluralString(R.plurals.songsPlural, folder.songCount),
                    onClick = { onFolderClick(folder) },
                    onMore = { onFolderMore(folder) },
                )
            }
            items(state.songs, key = { "song-${it.id}" }) { song ->
                LibrarySongRow(song = song, onClick = { onSongClick(song) }, onMore = { onSongMore(song) })
            }
        }
    }
}
