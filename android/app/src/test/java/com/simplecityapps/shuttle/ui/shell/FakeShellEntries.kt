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
import com.simplecityapps.shuttle.designsystem.component.ArtworkPlaceholder
import com.simplecityapps.shuttle.designsystem.component.ArtworkSize
import com.simplecityapps.shuttle.designsystem.component.SectionHeader
import com.simplecityapps.shuttle.designsystem.component.SongRow
import com.simplecityapps.shuttle.fixtures.SampleAlbum
import com.simplecityapps.shuttle.fixtures.SampleLibrary
import com.simplecityapps.shuttle.ui.common.components.DetailScaffold
import com.simplecityapps.shuttle.ui.screens.library.AlbumArtistRoute
import com.simplecityapps.shuttle.ui.screens.library.LibraryArtwork
import com.simplecityapps.shuttle.ui.screens.library.route
import com.simplecityapps.toAlbum
import com.simplecityapps.toAlbumArtist

/**
 * Stand-in screens for the shell's own tests: the real destinations need the Hilt graph, and these tests exercise
 * tabs, list-detail and back stacks rather than any one screen. Home lists the first eight sample albums and Library
 * all of them; an album lists its tracks; an artist lists its albums.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
fun fakeShellEntryProvider(navigator: AppNavigator): (NavKey) -> NavEntry<NavKey> = entryProvider {
    val openAlbum = { album: SampleAlbum -> navigator.open(album.toAlbum().route) }
    entry<HomeRoute> { FakeList("Recently played", SampleLibrary.albums.take(8), openAlbum) }
    entry<LibraryRoute>(metadata = ListDetailSceneStrategy.listPane()) { FakeList("Albums", SampleLibrary.albums, openAlbum) }
    entry<SearchRoute> { FakeList("Search", emptyList(), openAlbum) }
    entry<AlbumRoute>(metadata = ListDetailSceneStrategy.detailPane()) { route ->
        val album = SampleLibrary.albums.firstOrNull { it.toAlbum().route == route }
        DetailScaffold(
            title = album?.title ?: route.albumKey.orEmpty(),
            subtitle = album?.artist,
            onNavigateUp = { navigator.back() },
            hero = { LibraryArtwork(album?.toAlbum(), ArtworkPlaceholder.Album, size = ArtworkSize.Hero) },
        ) {
            items(album?.songs.orEmpty(), key = { it.id }) { song -> SongRow(title = song.title, subtitle = song.artist, onClick = {}, trackNumber = song.track) }
        }
    }
    entry<AlbumArtistRoute>(metadata = ListDetailSceneStrategy.detailPane()) { route ->
        val artist = SampleLibrary.artists.firstOrNull { it.toAlbumArtist().route == route }
        FakeList(artist?.name ?: route.albumArtistKey.orEmpty(), artist?.albums.orEmpty(), openAlbum)
    }
    entry<SettingsRoute> { FakeList("Settings", emptyList(), openAlbum) }
}

@Composable
private fun FakeList(header: String, albums: List<SampleAlbum>, onOpenAlbum: (SampleAlbum) -> Unit) {
    LazyColumn {
        item { SectionHeader(title = header) }
        items(albums, key = { it.id }) { album ->
            AlbumRow(title = album.title, artist = album.artist, onClick = { onOpenAlbum(album) }, artwork = { LibraryArtwork(album.toAlbum(), ArtworkPlaceholder.Album) })
        }
    }
}
