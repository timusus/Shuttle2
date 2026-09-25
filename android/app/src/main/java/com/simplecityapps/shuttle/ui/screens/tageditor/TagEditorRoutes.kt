package com.simplecityapps.shuttle.ui.screens.tageditor

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.simplecityapps.shuttle.ui.screens.songinfo.SongInfoDestination
import com.simplecityapps.shuttle.ui.shell.AppNavigator
import kotlinx.serialization.Serializable

// Routes for the song-level screens any song action can open: the tag editor and song info. Keys only, never
// Parcelable models (app-shell.md, section 4); the screens load the songs by id.

@Serializable
data class TagEditorRoute(
    val songIds: List<Long>,
) : NavKey

@Serializable
data class SongInfoRoute(
    val songId: Long,
) : NavKey

/** The tag editor and song info, as detail panes: beside the list on a wide window, full screen on a phone. */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
fun EntryProviderScope<NavKey>.tagEditorEntries(navigator: AppNavigator) {
    val detail = ListDetailSceneStrategy.detailPane()
    val onNavigateUp: () -> Unit = { navigator.back() }
    entry<TagEditorRoute>(metadata = detail) { route -> TagEditorDestination(route, onNavigateUp = onNavigateUp) }
    entry<SongInfoRoute>(metadata = detail) { route -> SongInfoDestination(route, onNavigateUp = onNavigateUp) }
}
