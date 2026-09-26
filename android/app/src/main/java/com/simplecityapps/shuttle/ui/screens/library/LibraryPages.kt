package com.simplecityapps.shuttle.ui.screens.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
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
import com.simplecityapps.shuttle.designsystem.component.SectionHeader
import com.simplecityapps.shuttle.designsystem.component.SongRow
import com.simplecityapps.shuttle.designsystem.component.formatDuration
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.Genre
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.SmartPlaylist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.ui.common.components.FastScroller
import com.simplecityapps.shuttle.ui.common.components.rememberFastScrollableState
import com.simplecityapps.shuttle.ui.screens.library.albumartists.AlbumArtistListUiState
import com.simplecityapps.shuttle.ui.screens.library.albums.AlbumListUiState
import com.simplecityapps.shuttle.ui.screens.library.albums.getAlbumFastscrollPopup
import com.simplecityapps.shuttle.ui.screens.library.albums.getAlbumPopupText
import com.simplecityapps.shuttle.ui.screens.library.folders.Folder
import com.simplecityapps.shuttle.ui.screens.library.folders.FolderListUiState
import com.simplecityapps.shuttle.ui.screens.library.folders.displayName
import com.simplecityapps.shuttle.ui.screens.library.folders.displayPath
import com.simplecityapps.shuttle.ui.screens.library.genres.GenreListUiState
import com.simplecityapps.shuttle.ui.screens.library.playlists.PlaylistListUiState
import com.simplecityapps.shuttle.ui.screens.library.songs.SongListUiState
import com.simplecityapps.shuttle.ui.screens.library.songs.getFastscrollPopup
import com.simplecityapps.shuttle.ui.screens.library.songs.getFastscrollPopupText

// The library tabs' pages: state in, events out, restyled with catalogue rows. The ViewModels are the existing
// tab ViewModels; LibraryScreen wires them.

/**
 * Fills the page so the scroller's track sits at its end edge, as the legacy lists have it. The track starts below the
 * pages' leading section header (48dp at least), so the thumb never covers the header's action, such as Shuffle (#396).
 */
private val FastScrollerModifier = Modifier.fillMaxSize().padding(top = 48.dp + 8.dp, bottom = 8.dp).testTag("library-fast-scroller")

/** The catalogue's compact grid: two columns of tiles on a phone, more as the width allows. */
private val LibraryGridColumns = GridCells.Adaptive(minSize = 160.dp)

/** Songs: a count header with Shuffle, then every song. Tap plays from that row; long-press selects. */
@Composable
fun SongsPage(
    state: SongListUiState,
    onSongClick: (Song) -> Unit,
    onSongLongClick: (Song) -> Unit,
    onSongMore: (Song) -> Unit,
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
        Box(modifier.fillMaxSize()) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().testTag("library-songs")) {
                item(key = "header") {
                    SectionHeader(
                        title = pluralString(R.plurals.songsPlural, state.songs.size),
                        action = stringResource(R.string.menu_title_shuffle),
                        onAction = onShuffle,
                    )
                }
                items(state.songs, key = { it.id }) { song ->
                    LibrarySongRow(
                        song = song,
                        selected = song in state.selectedSongs,
                        onClick = { onSongClick(song) },
                        onLongClick = { onSongLongClick(song) },
                        onMore = { onSongMore(song) },
                    )
                }
            }
            FastScroller(
                modifier = FastScrollerModifier,
                getPopupText = { index -> state.songs.getOrNull(index - 1)?.let { getFastscrollPopupText(it, state.sortOrder) } },
                state = listState,
                popup = getFastscrollPopup(state.sortOrder),
            )
        }
    }
}

/** A song in a list outside its album: artwork, "artist · album", duration. */
@Composable
fun LibrarySongRow(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    playing: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onMore: (() -> Unit)? = null,
) {
    SongRow(
        title = song.name.orEmpty(),
        subtitle = song.rowSubtitle,
        onClick = onClick,
        modifier = modifier,
        artwork = { LibraryArtwork(song, ArtworkPlaceholder.Song, size = ArtworkSize.Small) },
        duration = formatDuration(song.duration.toLong()),
        playing = playing,
        selected = selected,
        onLongClick = onLongClick,
        onMore = onMore,
    )
}

/** Albums: a grid by default (#491) or a list, with Shuffle in the header. */
@Composable
fun AlbumsPage(
    state: AlbumListUiState,
    onAlbumClick: (Album) -> Unit,
    onAlbumLongClick: (Album) -> Unit,
    onAlbumMore: (Album) -> Unit,
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
        val header: @Composable () -> Unit = {
            SectionHeader(
                title = pluralString(R.plurals.albumsPlural, state.albums.size),
                action = stringResource(R.string.menu_title_shuffle),
                onAction = onShuffle,
            )
        }
        val popupText = { index: Int -> state.albums.getOrNull(index - 1)?.let { getAlbumPopupText(it, state.sortOrder) } }
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
                FastScroller(modifier = FastScrollerModifier, getPopupText = popupText, scrollableState = rememberFastScrollableState(gridState), popup = getAlbumFastscrollPopup(state.sortOrder))
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
                FastScroller(modifier = FastScrollerModifier, getPopupText = popupText, state = listState, popup = getAlbumFastscrollPopup(state.sortOrder))
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
        val popupText = { index: Int -> artists.getOrNull(index)?.name?.firstOrNull()?.uppercase() }
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
                FastScroller(modifier = FastScrollerModifier, getPopupText = popupText, scrollableState = rememberFastScrollableState(gridState))
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
                FastScroller(modifier = FastScrollerModifier, getPopupText = popupText, state = listState)
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
                            artwork = { LibraryArtwork(null, ArtworkPlaceholder.SmartPlaylist) },
                        )
                    }
                }
                items(state.smartPlaylists, key = { "smart-${it.nameResId}" }) { smartPlaylist ->
                    PlaylistRow(
                        name = stringResource(smartPlaylist.nameResId),
                        onClick = { onSmartPlaylistClick(smartPlaylist) },
                        artwork = { LibraryArtwork(null, ArtworkPlaceholder.SmartPlaylist) },
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
                        artwork = { LibraryArtwork(null, ArtworkPlaceholder.Playlist) },
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
