package com.simplecityapps.shuttle.ui.screens.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
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
import com.simplecityapps.shuttle.designsystem.component.S2ButtonGroup
import com.simplecityapps.shuttle.designsystem.component.S2GroupAction
import com.simplecityapps.shuttle.designsystem.component.SectionHeader
import com.simplecityapps.shuttle.designsystem.component.SongRow
import com.simplecityapps.shuttle.designsystem.component.formatDuration
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.Genre
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.SmartPlaylist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.sorting.SongSortOrder
import com.simplecityapps.shuttle.ui.common.components.AlphabetFastScroller
import com.simplecityapps.shuttle.ui.common.components.FastScrollableState
import com.simplecityapps.shuttle.ui.common.components.FastScroller
import com.simplecityapps.shuttle.ui.common.components.NoPopup
import com.simplecityapps.shuttle.ui.common.components.letterSections
import com.simplecityapps.shuttle.ui.common.components.rememberFastScrollableState
import com.simplecityapps.shuttle.ui.screens.library.albumartists.AlbumArtistListUiState
import com.simplecityapps.shuttle.ui.screens.library.albums.AlbumListUiState
import com.simplecityapps.shuttle.ui.screens.library.albums.albumLetterKey
import com.simplecityapps.shuttle.ui.screens.library.albums.albumThumbLabel
import com.simplecityapps.shuttle.ui.screens.library.folders.Folder
import com.simplecityapps.shuttle.ui.screens.library.folders.FolderListUiState
import com.simplecityapps.shuttle.ui.screens.library.folders.displayName
import com.simplecityapps.shuttle.ui.screens.library.folders.displayPath
import com.simplecityapps.shuttle.ui.screens.library.genres.GenreListUiState
import com.simplecityapps.shuttle.ui.screens.library.playlists.PlaylistListUiState
import com.simplecityapps.shuttle.ui.screens.library.songs.SongListUiState
import com.simplecityapps.shuttle.ui.screens.library.songs.songLetterKey
import com.simplecityapps.shuttle.ui.screens.library.songs.songThumbLabel

// The library tabs' pages: state in, events out, restyled with catalogue rows. The ViewModels are the existing
// tab ViewModels; LibraryScreen wires them.

/** The Play / Shuffle row leading the Songs and Albums pages: the button group's 40dp and its padding. */
private val PlayShuffleHeaderHeight = 40.dp + 16.dp

/**
 * Fills the page so the scroller's track sits at its end edge, as the legacy lists have it. The track starts below the
 * pages' leading header, so the thumb never covers the header's action, such as Shuffle (#396).
 */
private val FastScrollerModifier = Modifier.fillMaxSize().padding(top = PlayShuffleHeaderHeight + 8.dp, bottom = 8.dp).testTag("library-fast-scroller")

/**
 * Play and Shuffle for a whole tab, styled like a detail screen's (#491). The count lives in the top bar's subtitle, so
 * this row doesn't repeat it.
 */
@Composable
private fun PlayShuffleHeader(onPlay: () -> Unit, onShuffle: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(PlayShuffleHeaderHeight).padding(horizontal = 16.dp, vertical = 8.dp)) {
        S2ButtonGroup(
            primary = S2GroupAction(stringResource(R.string.menu_title_play), onPlay, Icons.Rounded.PlayArrow),
            secondary = listOf(S2GroupAction(stringResource(R.string.menu_title_shuffle), onShuffle, Icons.Rounded.Shuffle)),
        )
    }
}

/**
 * The fast scroller for a page of [items] sorted by [sortOrder]: by first letter of [letterKey] when the sort is by a
 * name (#491), else a plain thumb with [thumbLabel] in its popup, or none. [headerCount] items lead [items] in the list.
 */
@Composable
private fun <T> LibraryFastScroller(
    items: List<T>,
    sortOrder: Any,
    letterKey: ((T) -> String?)?,
    scrollableState: FastScrollableState,
    headerCount: Int,
    thumbLabel: ((T) -> String?)? = null,
) {
    if (letterKey != null) {
        val sections = remember(items, sortOrder) { letterSections(items, letterKey) }
        AlphabetFastScroller(sections, scrollableState, FastScrollerModifier, itemOffset = headerCount)
    } else {
        FastScroller(
            getPopupText = { index -> items.getOrNull(index - headerCount)?.let { thumbLabel?.invoke(it) } },
            scrollableState = scrollableState,
            modifier = FastScrollerModifier,
            popup = if (thumbLabel == null) ::NoPopup else null,
        )
    }
}

/** Each smart playlist's own placeholder, so the four don't share one icon (#491). */
private val SmartPlaylist.placeholder: ArtworkPlaceholder
    get() = when (SmartPlaylistId.of(this)) {
        SmartPlaylistId.RecentlyAdded -> ArtworkPlaceholder.RecentlyAdded
        SmartPlaylistId.MostPlayed -> ArtworkPlaceholder.MostPlayed
        SmartPlaylistId.History -> ArtworkPlaceholder.History
        null -> ArtworkPlaceholder.SmartPlaylist
    }

/** The gap between a playlist mosaic's covers. */
private val MosaicGap = 2.dp

/**
 * A playlist's artwork from its [covers] (#491): a 2x2 mosaic of four albums' covers, the one cover of a playlist
 * with fewer albums, or the playlist placeholder when it has no songs.
 */
@Composable
private fun PlaylistMosaic(covers: List<Song>) {
    if (covers.size < 4) {
        LibraryArtwork(covers.firstOrNull(), ArtworkPlaceholder.Playlist)
        return
    }
    val cell = (ArtworkSize.Medium.dp - MosaicGap) / 2
    Column(Modifier.size(ArtworkSize.Medium.dp), verticalArrangement = Arrangement.spacedBy(MosaicGap)) {
        covers.take(4).chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(MosaicGap)) {
                pair.forEach { song -> LibraryArtwork(song, ArtworkPlaceholder.Album, Modifier.size(cell), size = ArtworkSize.Small) }
            }
        }
    }
}

/** The catalogue's compact grid: two columns of tiles on a phone, more as the width allows. */
private val LibraryGridColumns = GridCells.Adaptive(minSize = 160.dp)

/** Songs: Play / Shuffle, then every song. Tap plays from that row; long-press selects. */
@Composable
fun SongsPage(
    state: SongListUiState,
    onSongClick: (Song) -> Unit,
    onSongLongClick: (Song) -> Unit,
    onSongMore: (Song) -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val content = when (state.loadingState) {
        SongListUiState.LoadingState.Loading -> LibraryContentState.Loading
        SongListUiState.LoadingState.Scanning -> LibraryContentState.Scanning
        SongListUiState.LoadingState.Empty -> LibraryContentState.Empty
        SongListUiState.LoadingState.Ready -> LibraryContentState.Ready
    }
    LibraryContent(content, stringResource(R.string.song_list_empty), modifier, state.scanProgress) {
        val listState = rememberLazyListState()
        val byAlbum = state.sortOrder == SongSortOrder.AlbumGroupKey || state.sortOrder == SongSortOrder.Default
        val entries = remember(state.songs, byAlbum) { songEntries(state.songs, byAlbum) }
        Box(modifier.fillMaxSize()) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().testTag("library-songs")) {
                item(key = "header") { PlayShuffleHeader(onPlay, onShuffle) }
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
            LibraryFastScroller(
                items = entries,
                sortOrder = state.sortOrder,
                letterKey = songLetterKey(state.sortOrder)?.let { key -> { entry: SongEntry -> key(entry.song) } },
                scrollableState = rememberFastScrollableState(listState),
                headerCount = 1,
                thumbLabel = songThumbLabel(state.sortOrder)?.let { label -> { entry: SongEntry -> label(entry.song) } },
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
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        LibraryArtwork(song, ArtworkPlaceholder.Album, size = ArtworkSize.Medium)
        Column(Modifier.weight(1f)) {
            Text(song.album.orEmpty(), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            song.albumArtist?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
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

/** Albums: Play / Shuffle, then a grid by default (#491) or a list. */
@Composable
fun AlbumsPage(
    state: AlbumListUiState,
    onAlbumClick: (Album) -> Unit,
    onAlbumLongClick: (Album) -> Unit,
    onAlbumMore: (Album) -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val content = when (state.loadingState) {
        AlbumListUiState.LoadingState.Loading -> LibraryContentState.Loading
        AlbumListUiState.LoadingState.Scanning -> LibraryContentState.Scanning
        AlbumListUiState.LoadingState.Empty -> LibraryContentState.Empty
        AlbumListUiState.LoadingState.Ready -> LibraryContentState.Ready
    }
    LibraryContent(content, stringResource(R.string.album_list_empty), modifier, state.scanProgress) {
        val header: @Composable () -> Unit = { PlayShuffleHeader(onPlay, onShuffle) }
        val fastScroller: @Composable (FastScrollableState) -> Unit = { scrollableState ->
            LibraryFastScroller(state.albums, state.sortOrder, albumLetterKey(state.sortOrder), scrollableState, headerCount = 1, thumbLabel = albumThumbLabel(state.sortOrder))
        }
        Box(modifier.fillMaxSize()) {
            if (state.viewMode == ViewMode.Grid) {
                val gridState = rememberLazyGridState()
                LazyVerticalGrid(
                    columns = LibraryGridColumns,
                    state = gridState,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize().testTag("library-albums"),
                ) {
                    item(key = "header", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) { header() }
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
                    item(key = "header") { header() }
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
) {
    val content = when (state.loadingState) {
        AlbumArtistListUiState.LoadingState.Loading -> LibraryContentState.Loading
        AlbumArtistListUiState.LoadingState.Scanning -> LibraryContentState.Scanning
        AlbumArtistListUiState.LoadingState.Empty -> LibraryContentState.Empty
        AlbumArtistListUiState.LoadingState.Ready -> LibraryContentState.Ready
    }
    LibraryContent(content, stringResource(R.string.artist_list_empty), modifier, state.scanProgress) {
        val artists = state.albumArtists
        // Artists are always sorted by their group key, which drops a leading "The".
        val sections = remember(artists) { letterSections(artists) { it.groupKey.key } }
        Box(modifier.fillMaxSize()) {
            if (state.viewMode == ViewMode.Grid) {
                val gridState = rememberLazyGridState()
                LazyVerticalGrid(
                    columns = LibraryGridColumns,
                    state = gridState,
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize().testTag("library-artists"),
                ) {
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
                AlphabetFastScroller(sections, rememberFastScrollableState(gridState), FastScrollerModifier)
            } else {
                val listState = rememberLazyListState()
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize().testTag("library-artists")) {
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
                AlphabetFastScroller(sections, rememberFastScrollableState(listState), FastScrollerModifier)
            }
        }
    }
}

/** Genres: a list, no multi-select (inventory §1). */
@Composable
fun GenresPage(
    state: GenreListUiState,
    onGenreClick: (Genre) -> Unit,
    onGenreMore: (Genre) -> Unit,
    modifier: Modifier = Modifier,
) {
    val content = when (state.loadingState) {
        GenreListUiState.LoadingState.Loading -> LibraryContentState.Loading
        GenreListUiState.LoadingState.Scanning -> LibraryContentState.Scanning
        GenreListUiState.LoadingState.Empty -> LibraryContentState.Empty
        GenreListUiState.LoadingState.Ready -> LibraryContentState.Ready
    }
    LibraryContent(content, stringResource(R.string.genre_list_empty), modifier, state.scanProgress) {
        val listState = rememberLazyListState()
        Box(modifier.fillMaxSize()) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().testTag("library-genres")) {
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
            FastScroller(modifier = FastScrollerModifier, getPopupText = { index -> state.genres.getOrNull(index)?.name?.firstOrNull()?.uppercase() }, state = listState)
        }
    }
}

/** Playlists: Favorites, then the smart playlists, pinned first, then the user's, with "New playlist" in their header. */
@Composable
fun PlaylistsPage(
    state: PlaylistListUiState,
    onPlaylistClick: (Playlist) -> Unit,
    onPlaylistMore: (Playlist) -> Unit,
    onSmartPlaylistClick: (SmartPlaylist) -> Unit,
    onNewPlaylist: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val content = when (state.loadingState) {
        PlaylistListUiState.LoadingState.Loading -> LibraryContentState.Loading

        PlaylistListUiState.LoadingState.Scanning -> LibraryContentState.Scanning

        // Smart playlists are always there, so an empty list still shows them and "New playlist".
        PlaylistListUiState.LoadingState.Ready -> LibraryContentState.Ready
    }
    LibraryContent(content, stringResource(R.string.playlist_list_empty), modifier, state.scanProgress) {
        val listState = rememberLazyListState()
        val headerCount = 1 + (if (state.favoritesPlaylist != null) 1 else 0) + state.smartPlaylists.size + 1
        Box(modifier.fillMaxSize()) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().testTag("library-playlists")) {
                item(key = "smart-header") { SectionHeader(title = stringResource(R.string.library_smart_playlists)) }
                state.favoritesPlaylist?.let { favorites ->
                    item(key = "favorites") {
                        PlaylistRow(
                            name = favorites.name,
                            summary = pluralString(R.plurals.songsPlural, favorites.songCount),
                            onClick = { onPlaylistClick(favorites) },
                            artwork = { LibraryArtwork(null, ArtworkPlaceholder.Favorites) },
                        )
                    }
                }
                items(state.smartPlaylists, key = { "smart-${it.nameResId}" }) { smartPlaylist ->
                    PlaylistRow(
                        name = stringResource(smartPlaylist.nameResId),
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
                        artwork = { PlaylistMosaic(state.covers[playlist.id].orEmpty()) },
                    )
                }
            }
            FastScroller(modifier = FastScrollerModifier, getPopupText = { index -> state.playlists.getOrNull(index - headerCount)?.name?.firstOrNull()?.uppercase() }, state = listState)
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
