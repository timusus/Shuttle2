package com.simplecityapps.shuttle.ui.screens.tageditor

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.simplecityapps.shuttle.ui.screens.songinfo.SongInfoDestination
import com.simplecityapps.shuttle.ui.shell.AppNavigator
import com.simplecityapps.shuttle.ui.shell.ShellSheetSceneStrategy
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

/** Song info's markers: a bottom sheet on a phone, a detail pane beside the list from 600 dp. */
@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3Api::class)
val SongInfoMetadata: Map<String, Any> = ListDetailSceneStrategy.detailPane() + ShellSheetSceneStrategy.sheet()

/** The tag editor, as a detail pane (beside the list on a wide window, full screen on a phone), and song info ([SongInfoMetadata]). */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
fun EntryProviderScope<NavKey>.tagEditorEntries(navigator: AppNavigator) {
    val onNavigateUp: () -> Unit = { navigator.back() }
    entry<TagEditorRoute>(metadata = ListDetailSceneStrategy.detailPane()) { route -> TagEditorDestination(route, onNavigateUp = onNavigateUp) }
    entry<SongInfoRoute>(metadata = SongInfoMetadata) { route -> SongInfoDestination(route, onNavigateUp = onNavigateUp) }
}
