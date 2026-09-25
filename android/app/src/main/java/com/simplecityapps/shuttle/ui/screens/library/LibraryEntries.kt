package com.simplecityapps.shuttle.ui.screens.library

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.EmptyState
import com.simplecityapps.shuttle.ui.actions.NavigationTarget
import com.simplecityapps.shuttle.ui.shell.AlbumRoute
import com.simplecityapps.shuttle.ui.shell.AppNavigator
import com.simplecityapps.shuttle.ui.shell.LibraryRoute

/** The Library tab's entries: the container as the list pane, its detail screens as the detail pane. */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
fun EntryProviderScope<NavKey>.libraryEntries(navigator: AppNavigator) {
    val onNavigate = navigator::openTarget
    entry<LibraryRoute>(metadata = ListDetailSceneStrategy.listPane(detailPlaceholder = { LibraryDetailPlaceholder() })) {
        LibraryDestination(onOpen = navigator::open, onNavigate = onNavigate)
    }
    val detail = ListDetailSceneStrategy.detailPane()
    val onNavigateUp: () -> Unit = { navigator.back() }
    entry<AlbumRoute>(metadata = detail) { route -> AlbumDetailDestination(route, onNavigateUp = onNavigateUp, onNavigate = onNavigate) }
    entry<AlbumArtistRoute>(metadata = detail) { route -> AlbumArtistDetailDestination(route, onNavigateUp = onNavigateUp, onOpen = navigator::open, onNavigate = onNavigate) }
    entry<GenreRoute>(metadata = detail) { route -> GenreDetailDestination(route, onNavigateUp = onNavigateUp, onOpen = navigator::open, onNavigate = onNavigate) }
    entry<PlaylistRoute>(metadata = detail) { route -> PlaylistDetailDestination(route, onNavigateUp = onNavigateUp, onNavigate = onNavigate) }
    entry<SmartPlaylistRoute>(metadata = detail) { route -> SmartPlaylistDetailDestination(route, onNavigateUp = onNavigateUp, onNavigate = onNavigate) }
}

/**
 * Opens a detail screen a media action asked for, from the library or any screen that links into it (Home,
 * Search). The tag editor and song info have no shell screens yet.
 */
fun AppNavigator.openTarget(target: NavigationTarget) {
    when (target) {
        is NavigationTarget.Album -> open(target.album.route)
        is NavigationTarget.AlbumArtist -> open(target.albumArtist.route)
        is NavigationTarget.TagEditor, is NavigationTarget.SongInfo -> Unit
    }
}

@Composable
private fun LibraryDetailPlaceholder() {
    EmptyState(title = stringResource(R.string.library_detail_placeholder))
}
