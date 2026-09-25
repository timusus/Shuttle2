package com.simplecityapps.shuttle.ui.shell

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.simplecityapps.shuttle.designsystem.component.AlbumRow
import com.simplecityapps.shuttle.designsystem.component.Artwork
import com.simplecityapps.shuttle.designsystem.component.ArtworkPlaceholder
import com.simplecityapps.shuttle.designsystem.component.ArtworkSize
import com.simplecityapps.shuttle.designsystem.component.EmptyState
import com.simplecityapps.shuttle.designsystem.component.S2IconButton
import com.simplecityapps.shuttle.designsystem.component.SectionHeader
import com.simplecityapps.shuttle.designsystem.component.SongRow
import com.simplecityapps.shuttle.ui.common.components.DetailScaffold
import com.simplecityapps.shuttle.ui.screens.settings.settingsEntries

// Placeholder destinations for the shell spike (#375): enough to exercise tabs, list-detail and
// back stacks. The real screens replace them one route at a time.

private data class PlaceholderAlbum(
    val key: String,
    val title: String,
    val artist: String,
)

private val placeholderAlbums = (1..24).map { PlaceholderAlbum(key = "album-$it", title = "Album $it", artist = "Artist ${(it - 1) / 3 + 1}") }

/** Routes each key to its placeholder screen; the navigator stays out of the screens. */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
fun shellEntryProvider(navigator: AppNavigator): (NavKey) -> NavEntry<NavKey> = entryProvider {
    val openAlbum = { album: PlaceholderAlbum -> navigator.open(AlbumRoute(albumKey = album.key, albumArtistKey = album.artist)) }
    entry<HomeRoute> { HomeScreen(onOpenAlbum = openAlbum) }
    entry<LibraryRoute>(
        metadata = ListDetailSceneStrategy.listPane(
            detailPlaceholder = { EmptyState(title = "No album selected", message = "Pick an album to see it here") },
        ),
    ) {
        LibraryScreen(onOpenAlbum = openAlbum)
    }
    entry<SearchRoute> { SearchScreen() }
    entry<AlbumRoute>(metadata = ListDetailSceneStrategy.detailPane()) { route -> AlbumScreen(route, onNavigateUp = { navigator.back() }) }
    settingsEntries(navigator)
}

/** A top-level screen: a collapsing `LargeFlexibleTopAppBar` over one list (app-shell.md, section 3). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ShellListScreen(
    title: String,
    subtitle: String?,
    onNavigateUp: (() -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        // The shell pads destinations clear of the nav bar and player; the bar takes the status bar.
        contentWindowInsets = WindowInsets(0),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(title) },
                subtitle = subtitle?.let { { Text(it) } },
                navigationIcon = {
                    if (onNavigateUp != null) {
                        S2IconButton(icon = Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Navigate up", onClick = onNavigateUp)
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), content = content)
    }
}

@Composable
private fun HomeScreen(onOpenAlbum: (PlaceholderAlbum) -> Unit) {
    ShellListScreen(title = "Home", subtitle = null) {
        item { SectionHeader(title = "Recently played") }
        albumItems(placeholderAlbums.take(8), onOpenAlbum)
    }
}

@Composable
private fun LibraryScreen(onOpenAlbum: (PlaceholderAlbum) -> Unit) {
    ShellListScreen(title = "Library", subtitle = "${placeholderAlbums.size} albums") {
        albumItems(placeholderAlbums, onOpenAlbum)
    }
}

private fun LazyListScope.albumItems(
    albums: List<PlaceholderAlbum>,
    onOpenAlbum: (PlaceholderAlbum) -> Unit,
) {
    items(albums, key = { it.key }) { album ->
        AlbumRow(
            title = album.title,
            artist = album.artist,
            onClick = { onOpenAlbum(album) },
            artwork = { Artwork(ArtworkPlaceholder.Album) },
        )
    }
}

@Composable
private fun SearchScreen() {
    ShellListScreen(title = "Search", subtitle = null) {
        item { EmptyState(title = "Search", message = "Placeholder destination", icon = Icons.Rounded.Search) }
    }
}

@Composable
private fun AlbumScreen(
    route: AlbumRoute,
    onNavigateUp: () -> Unit,
) {
    val album = placeholderAlbums.firstOrNull { it.key == route.albumKey }
    DetailScaffold(
        title = album?.title ?: "Album",
        subtitle = album?.artist,
        onNavigateUp = onNavigateUp,
        hero = { Artwork(ArtworkPlaceholder.Album, size = ArtworkSize.Hero) },
    ) {
        item { SectionHeader(title = album?.title ?: "Album") }
        items((1..10).toList(), key = { it }) { track ->
            SongRow(title = "Track $track", subtitle = album?.artist.orEmpty(), onClick = {}, trackNumber = track)
        }
    }
}
