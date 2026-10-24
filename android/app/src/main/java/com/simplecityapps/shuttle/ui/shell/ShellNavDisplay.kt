package com.simplecityapps.shuttle.ui.shell

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.ui.NavDisplay
import com.simplecityapps.shuttle.ui.shell.adaptive.ShellLayout
import com.simplecityapps.shuttle.ui.shell.adaptive.ShellWidth
import com.simplecityapps.shuttle.ui.shell.adaptive.listDetailDirective

@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalSharedTransitionApi::class)
@Composable
internal fun ShellNavDisplay(
    navigator: AppNavigator,
    layout: ShellLayout,
    windowAdaptiveInfo: WindowAdaptiveInfo,
    entryProvider: (AppNavigator) -> (NavKey) -> NavEntry<NavKey>,
) {
    val directive = remember(layout, windowAdaptiveInfo) { layout.listDetailDirective(windowAdaptiveInfo) }
    val listDetailScenes = rememberListDetailSceneStrategy<NavKey>(directive = directive)
    val listDetail = remember(listDetailScenes) { PopOneEntrySceneStrategy(listDetailScenes) }
    // Routes marked sheet() open in a bottom sheet on a phone; from 600 dp their detail-pane marker applies instead.
    val sheet = remember(layout.width) { ShellSheetSceneStrategy<NavKey>(enabled = layout.width == ShellWidth.Compact) }
    val saveableState = rememberSaveableStateHolderNavEntryDecorator<NavKey>()
    val viewModelStores = rememberViewModelStoreNavEntryDecorator<NavKey>()
    val decorators = remember(saveableState, viewModelStores) { listOf(saveableState, viewModelStores) }
    val entries = remember(navigator, entryProvider) { entryProvider(navigator) }
    // Every tab's entries stay decorated, so a tab's screens keep their state while another is shown.
    val entriesByTab = ShellTab.entries.associateWith { tab -> rememberDecoratedNavEntries(navigator.stack(tab), decorators, entries) }
    val transitions = rememberShellTransitions(navigator)
    SharedTransitionLayout {
        CompositionLocalProvider(
            LocalListBesideDetail provides (directive.maxHorizontalPartitions > 1),
            LocalSharedTransitionScope provides this,
            LocalAnimationsEnabled provides rememberAnimationsEnabled(),
        ) {
            NavDisplay(
                entries = navigator.visibleTabs.flatMap { entriesByTab.getValue(it) },
                sceneStrategies = listOf(sheet, listDetail, SinglePaneSceneStrategy()),
                onBack = { navigator.back() },
                transitionSpec = transitions.transition,
                popTransitionSpec = transitions.popTransition,
                predictivePopTransitionSpec = transitions.predictivePopTransition,
            )
        }
    }
}
