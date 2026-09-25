package com.simplecityapps.shuttle.ui.shell

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.simplecityapps.shuttle.designsystem.component.AlbumRow
import com.simplecityapps.shuttle.designsystem.component.Artwork
import com.simplecityapps.shuttle.designsystem.component.ArtworkPlaceholder
import com.simplecityapps.shuttle.designsystem.component.ArtworkSize
import com.simplecityapps.shuttle.designsystem.component.SectionHeader
import com.simplecityapps.shuttle.designsystem.component.SongRow
import com.simplecityapps.shuttle.ui.common.components.DetailScaffold
import com.simplecityapps.shuttle.ui.screens.library.AlbumArtistRoute

/**
 * Stand-in screens for the shell's own tests: the real destinations need the Hilt graph, and these tests exercise
 * tabs, list-detail and back stacks rather than any one screen. Home and Library list "Album N"; an album lists
 * "Track N"; an artist lists its key over "Album N".
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
fun fakeShellEntryProvider(navigator: AppNavigator): (NavKey) -> NavEntry<NavKey> = entryProvider {
    val openAlbum = { index: Int -> navigator.open(AlbumRoute(albumKey = "album-$index", albumArtistKey = "artist")) }
    entry<HomeRoute> { FakeList("Recently played", (1..8).toList(), openAlbum) }
    entry<LibraryRoute>(metadata = ListDetailSceneStrategy.listPane()) { FakeList("Albums", (1..24).toList(), openAlbum) }
    entry<SearchRoute> { FakeList("Search", emptyList(), openAlbum) }
    entry<AlbumRoute>(metadata = ListDetailSceneStrategy.detailPane()) { route ->
        DetailScaffold(
            title = route.albumKey.orEmpty(),
            subtitle = null,
            onNavigateUp = { navigator.back() },
            hero = { Artwork(ArtworkPlaceholder.Album, size = ArtworkSize.Hero) },
        ) {
            items((1..10).toList(), key = { it }) { track -> SongRow(title = "Track $track", subtitle = "Artist", onClick = {}, trackNumber = track) }
        }
    }
    entry<AlbumArtistRoute>(metadata = ListDetailSceneStrategy.detailPane()) { route -> FakeList(route.albumArtistKey.orEmpty(), (1..3).toList(), openAlbum) }
    entry<SettingsRoute> { FakeList("Settings", emptyList(), openAlbum) }
}

@Composable
private fun FakeList(header: String, albums: List<Int>, onOpenAlbum: (Int) -> Unit) {
    LazyColumn {
        item { SectionHeader(title = header) }
        items(albums, key = { it }) { index ->
            AlbumRow(title = "Album $index", artist = "Artist", onClick = { onOpenAlbum(index) }, artwork = { Artwork(ArtworkPlaceholder.Album) })
        }
    }
}
