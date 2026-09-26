// Adapted from nav3-recipes' BottomSheetSceneStrategy (github.com/android/nav3-recipes, bottomsheet/BottomSheetSceneStrategy.kt).
package com.simplecityapps.shuttle.ui.shell

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.rememberLifecycleOwner
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.get
import androidx.navigation3.runtime.metadata
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope

/** The sheet's test tag, for tests that tell a sheet from a pane. */
const val ShellSheetTestTag = "shell-sheet"

/** True inside a [ShellSheetSceneStrategy] sheet, so a screen shown there can drop chrome a sheet doesn't need, such as its back arrow. */
val LocalInShellSheet = staticCompositionLocalOf { false }

/** An [OverlayScene] that shows [entry] in a [ModalBottomSheet] over [overlaidEntries]. */
@OptIn(ExperimentalMaterial3Api::class)
internal data class ShellSheetScene<T : Any>(
    override val key: T,
    override val previousEntries: List<NavEntry<T>>,
    override val overlaidEntries: List<NavEntry<T>>,
    private val entry: NavEntry<T>,
    private val properties: ModalBottomSheetProperties,
    private val onBack: () -> Unit,
) : OverlayScene<T> {

    // NavDisplay composes and later removes the same scene instance, so onRemove reaches the state its content remembered.
    private var sheetState: SheetState? = null

    override val entries: List<NavEntry<T>> = listOf(entry)

    override val content: @Composable () -> Unit = {
        val lifecycleOwner = rememberLifecycleOwner()
        val state = rememberModalBottomSheetState()
        sheetState = state
        // The sheet holds a whole screen, so it takes the screen's surface rather than a container tone that would band under its top bar.
        ModalBottomSheet(
            onDismissRequest = onBack,
            modifier = Modifier.testTag(ShellSheetTestTag),
            sheetState = state,
            containerColor = MaterialTheme.colorScheme.surface,
            properties = properties,
        ) {
            CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner, LocalInShellSheet provides true) {
                entry.Content()
            }
        }
    }

    // A pop the sheet didn't start (a swipe or back dismisses it first) slides it away rather than cutting it.
    override suspend fun onRemove() {
        sheetState?.hide()
    }
}

/**
 * Shows the top entry in a [ModalBottomSheet] when its metadata carries [sheet] and the strategy is [enabled]; otherwise
 * it leaves the entry to the strategies after it. The shell enables it at compact width only, so a route that also
 * carries the detail-pane marker is a sheet on a phone and a detail pane from 600 dp. Put it before any non-overlay
 * strategy.
 */
@OptIn(ExperimentalMaterial3Api::class)
class ShellSheetSceneStrategy<T : Any>(
    private val enabled: Boolean,
) : SceneStrategy<T> {

    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        if (!enabled) return null
        val lastEntry = entries.lastOrNull() ?: return null
        val properties = lastEntry.metadata[SheetKey] ?: return null
        @Suppress("UNCHECKED_CAST")
        return ShellSheetScene(
            key = lastEntry.contentKey as T,
            previousEntries = entries.dropLast(1),
            overlaidEntries = entries.dropLast(1),
            entry = lastEntry,
            properties = properties,
            onBack = onBack,
        )
    }

    companion object {
        /** Entry metadata marking a route to show in a sheet at compact width. */
        fun sheet(properties: ModalBottomSheetProperties = ModalBottomSheetProperties()) = metadata { put(SheetKey, properties) }

        object SheetKey : NavMetadataKey<ModalBottomSheetProperties>
    }
}
