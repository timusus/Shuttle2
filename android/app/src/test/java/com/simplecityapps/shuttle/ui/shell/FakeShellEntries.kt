package com.simplecityapps.shuttle.ui.shell

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.simplecityapps.shuttle.designsystem.component.AlbumRow
import com.simplecityapps.shuttle.designsystem.component.ArtworkPlaceholder
import com.simplecityapps.shuttle.designsystem.component.SectionHeader
import com.simplecityapps.shuttle.designsystem.component.SongRow
import com.simplecityapps.shuttle.fixtures.SampleAlbum
import com.simplecityapps.shuttle.fixtures.SampleLibrary
import com.simplecityapps.shuttle.ui.preview.toAlbum
import com.simplecityapps.shuttle.ui.preview.toAlbumArtist
import com.simplecityapps.shuttle.ui.screens.home.HomeVisibilityEffect
import com.simplecityapps.shuttle.ui.screens.library.AlbumArtistRoute
import com.simplecityapps.shuttle.ui.screens.library.DetailContentState
import com.simplecityapps.shuttle.ui.screens.library.LibraryArtwork
import com.simplecityapps.shuttle.ui.screens.library.LibraryDetailScaffold
import com.simplecityapps.shuttle.ui.screens.library.route
import com.simplecityapps.shuttle.ui.screens.settings.EqualizerRoute
import com.simplecityapps.shuttle.ui.screens.settings.FolderRulesRoute
import com.simplecityapps.shuttle.ui.screens.settings.SettingsDestinationRoute
import com.simplecityapps.shuttle.ui.screens.settings.SettingsDetailPane
import com.simplecityapps.shuttle.ui.screens.settings.SettingsList
import com.simplecityapps.shuttle.ui.screens.settings.SettingsProState
import com.simplecityapps.shuttle.ui.screens.settings.SettingsScenarios
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsDestination
import com.simplecityapps.shuttle.ui.screens.settings.settingsListPane
import com.simplecityapps.shuttle.ui.screens.songinfo.SongInfoScreen
import com.simplecityapps.shuttle.ui.screens.songinfo.songInfoReady
import com.simplecityapps.shuttle.ui.screens.tageditor.SongInfoMetadata
import com.simplecityapps.shuttle.ui.screens.tageditor.SongInfoRoute

/**
 * Stand-in screens for the shell's own tests: the real destinations need the Metro graph, and these tests exercise
 * tabs, list-detail, sheets and back stacks rather than any one screen. Home lists the first eight sample albums and Library
 * all of them; an album lists its tracks under the library's detail header; an artist lists its albums.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
fun fakeShellEntryProvider(
    navigator: AppNavigator,
    onHomeVisibilityChanged: (Boolean) -> Unit = {},
): (NavKey) -> NavEntry<NavKey> = entryProvider {
    val openAlbum = { album: SampleAlbum -> navigator.open(album.toAlbum().route) }
    entry<HomeRoute> {
        // The real Home's visibility effect, so the shell's tests see when it reloads
        HomeVisibilityEffect(onHomeVisibilityChanged)
        FakeList("Recently played", SampleLibrary.albums.take(8), openAlbum, ShellTab.Home)
    }
    entry<LibraryRoute>(metadata = ListDetailSceneStrategy.listPane()) { FakeList("Albums", SampleLibrary.albums, openAlbum, ShellTab.Library) }
    entry<SearchRoute> { FakeList("Search", emptyList(), openAlbum) }
    entry<AlbumRoute>(metadata = ListDetailSceneStrategy.detailPane()) { route ->
        val album = SampleLibrary.albums.firstOrNull { it.toAlbum().route == route }
        LibraryDetailScaffold(
            state = DetailContentState.Ready,
            title = album?.title ?: route.albumKey.orEmpty(),
            subtitle = album?.artist,
            artwork = album?.toAlbum(),
            placeholder = ArtworkPlaceholder.Album,
            onNavigateUp = { navigator.back() },
            onPlay = {},
            onShuffle = {},
            onMore = {},
        ) {
            items(album?.songs.orEmpty(), key = { it.id }) { song -> SongRow(title = song.title, subtitle = song.artist.takeIf { it != album?.artist }, onClick = {}, trackNumber = song.track) }
        }
    }
    entry<AlbumArtistRoute>(metadata = ListDetailSceneStrategy.detailPane()) { route ->
        val artist = SampleLibrary.artists.firstOrNull { it.toAlbumArtist().route == route }
        FakeList(artist?.name ?: route.albumArtistKey.orEmpty(), artist?.albums.orEmpty(), openAlbum)
    }
    // The real screen, so a sheet shows what a phone would; its ViewModel needs the Metro graph.
    entry<SongInfoRoute>(metadata = SongInfoMetadata) { SongInfoScreen(uiState = songInfoReady(), onNavigateUp = { navigator.back() }, onCopyPath = {}) }
    // The real Settings list and its pane metadata, over scenario state rather than its ViewModels; the stand-in and the
    // pages are fakes. Sources opens Folder rules, a page under a page.
    entry<SettingsRoute>(metadata = settingsListPane { FakeList("Settings: Appearance", emptyList(), openAlbum) }) {
        SettingsList(navigator, SettingsScenarios.equalizerOn, SettingsProState.Upsell)
    }
    entry<EqualizerRoute>(metadata = SettingsDetailPane) { FakeList("Equalizer screen", emptyList(), openAlbum) }
    entry<FolderRulesRoute>(metadata = SettingsDetailPane) { FakeList("Folder rules screen", emptyList(), openAlbum) }
    entry<SettingsDestinationRoute>(metadata = SettingsDetailPane) { route ->
        Column {
            if (route.destination == SettingsDestination.Sources) Text("Open folder rules", Modifier.clickable { navigator.open(FolderRulesRoute) })
            FakeList("Settings: ${route.destination.name}", emptyList(), openAlbum)
        }
    }
}

@Composable
private fun FakeList(header: String, albums: List<SampleAlbum>, onOpenAlbum: (SampleAlbum) -> Unit, tab: ShellTab? = null) {
    val listState = rememberLazyListState()
    // The real screens' effect, on the tabs' roots
    if (tab != null) ScrollToTopOnReselect(tab, listState)
    LazyColumn(state = listState) {
        item { SectionHeader(title = header) }
        items(albums, key = { it.id }) { album ->
            AlbumRow(title = album.title, artist = album.artist, onClick = { onOpenAlbum(album) }, artwork = { LibraryArtwork(album.toAlbum(), ArtworkPlaceholder.Album) })
        }
    }
}
