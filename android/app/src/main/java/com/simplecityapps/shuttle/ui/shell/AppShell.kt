package com.simplecityapps.shuttle.ui.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.material3.WideNavigationRailValue
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.material3.rememberWideNavigationRailState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.lerp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.ui.NavDisplay
import com.simplecityapps.shuttle.ui.shell.adaptive.ShellLayout
import com.simplecityapps.shuttle.ui.shell.adaptive.ShellWidth
import com.simplecityapps.shuttle.ui.shell.adaptive.listDetailDirective
import com.simplecityapps.shuttle.ui.shell.player.MiniPlayer
import com.simplecityapps.shuttle.ui.shell.player.MiniPlayerHeight
import com.simplecityapps.shuttle.ui.shell.player.PlayerLevel
import com.simplecityapps.shuttle.ui.shell.player.PlayerMode
import com.simplecityapps.shuttle.ui.shell.player.PlayerPane
import com.simplecityapps.shuttle.ui.shell.player.PlayerScrim
import com.simplecityapps.shuttle.ui.shell.player.PlayerSheet
import com.simplecityapps.shuttle.ui.shell.player.PlayerSheetGeometry
import com.simplecityapps.shuttle.ui.shell.player.PlayerSheetState
import com.simplecityapps.shuttle.ui.shell.player.playerPaneWidth
import com.simplecityapps.shuttle.ui.shell.player.rememberPlayerSheetState
import com.simplecityapps.shuttle.ui.shell.player.stackedQueueTravel
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * The Compose app shell (docs/architecture/app-shell.md): navigation bar or rail by width class,
 * a Navigation 3 display with list-detail, and the player as a sheet below 1200 dp or a
 * persistent pane from 1200 dp. The player state sits above the class branch, so one level
 * survives every resize, fold and rotation.
 */
@Composable
fun AppShell(
    queue: ShellQueueUiState,
    modifier: Modifier = Modifier,
    startTab: ShellTab = ShellTab.Home,
    windowAdaptiveInfo: WindowAdaptiveInfo = currentWindowAdaptiveInfoV2(),
) {
    val layout = remember(windowAdaptiveInfo) { ShellLayout.from(windowAdaptiveInfo) }
    val navigator = rememberAppNavigator(startTab)
    val player = rememberPlayerSheetState(layout.playerMode)
    SideEffect { player.configure(layout.playerMode, queue.hasQueue) }
    LaunchedEffect(player, player.revealPending) { if (player.revealPending) player.reveal() }
    LaunchedEffect(player, player.requestedLevel) { player.applyRequestedLevel() }

    val scope = rememberCoroutineScope()

    // A nav tap with the player above Mini settles it to Mini first, then navigates.
    fun navigate(action: () -> Unit) {
        scope.launch {
            if (player.mode != PlayerMode.Pane && player.level > PlayerLevel.Mini) player.moveTo(PlayerLevel.Mini)
            action()
        }
    }
    val onSelectTab: (ShellTab) -> Unit = { tab -> navigate { navigator.selectTab(tab) } }
    val onOpenSettings: () -> Unit = { navigate { navigator.open(SettingsRoute) } }
    val destinations: @Composable () -> Unit = { ShellNavDisplay(navigator, layout, windowAdaptiveInfo) }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        when (layout.playerMode) {
            PlayerMode.CompactSheet -> CompactShell(player, queue, layout, navigator.selectedTab, onSelectTab, onOpenSettings, destinations)
            PlayerMode.Sheet -> RailSheetShell(player, queue, layout, navigator.selectedTab, onSelectTab, onOpenSettings, destinations)
            PlayerMode.Pane -> PaneShell(player, queue, layout, navigator.selectedTab, onSelectTab, onOpenSettings, destinations)
        }
    }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun ShellNavDisplay(
    navigator: AppNavigator,
    layout: ShellLayout,
    windowAdaptiveInfo: WindowAdaptiveInfo,
) {
    val directive = remember(layout, windowAdaptiveInfo) { layout.listDetailDirective(windowAdaptiveInfo) }
    val listDetail = rememberListDetailSceneStrategy<NavKey>(directive = directive)
    val saveableState = rememberSaveableStateHolderNavEntryDecorator<NavKey>()
    val viewModelStores = rememberViewModelStoreNavEntryDecorator<NavKey>()
    val decorators = remember(saveableState, viewModelStores) { listOf(saveableState, viewModelStores) }
    val entryProvider = remember(navigator) { shellEntryProvider(navigator) }
    // Every tab's entries stay decorated, so a tab's screens keep their state while another is shown.
    val entriesByTab = ShellTab.entries.associateWith { tab -> rememberDecoratedNavEntries(navigator.stack(tab), decorators, entryProvider) }
    NavDisplay(
        entries = navigator.visibleTabs.flatMap { entriesByTab.getValue(it) },
        sceneStrategies = listOf(listDetail, SinglePaneSceneStrategy()),
        onBack = { navigator.back() },
    )
}

/** Sheet visible unless it is settled at Hidden: nothing is composed for an empty queue. */
@Composable
private fun rememberSheetVisible(player: PlayerSheetState): Boolean {
    val visible by remember(player) { derivedStateOf { player.level != PlayerLevel.Hidden || player.settledLevel != PlayerLevel.Hidden } }
    return visible
}

/** Destination bottom padding: nav bar plus the revealed mini player. Follows reveal only, so a drag never reflows. */
@Composable
private fun rememberContentBottomPadding(player: PlayerSheetState) = with(LocalDensity.current) {
    val padding by remember(player) { derivedStateOf { player.geometry.contentBottomPadding(player.geometry.reveal(player.offset)) } }
    padding.toDp()
}

/** Below 600 dp: bottom bar, one pane, the sheet with a Queue level. The bar draws over the sheet's foot. */
@Composable
private fun CompactShell(
    player: PlayerSheetState,
    queue: ShellQueueUiState,
    layout: ShellLayout,
    selectedTab: ShellTab,
    onSelectTab: (ShellTab) -> Unit,
    onOpenSettings: () -> Unit,
    destinations: @Composable () -> Unit,
) {
    var showMore by rememberSaveable { mutableStateOf(false) }
    val density = LocalDensity.current
    val statusBarTop = WindowInsets.statusBars.getTop(density)
    val navigationBarBottom = WindowInsets.navigationBars.getBottom(density)
    val miniHeight = with(density) { MiniPlayerHeight.toPx() }
    val sheetVisible = rememberSheetVisible(player)
    val bottomPadding = rememberContentBottomPadding(player)

    Layout(
        contents = listOf(
            { Box(Modifier.fillMaxSize().padding(bottom = bottomPadding)) { destinations() } },
            { PlayerScrim(player) },
            { if (sheetVisible) PlayerSheet(player, queue, layout) },
            {
                ShellNavigationBar(
                    selectedTab = selectedTab,
                    onSelectTab = onSelectTab,
                    onMore = { showMore = true },
                    modifier = Modifier.graphicsLayer { translationY = player.geometry.navBarTranslation(player.offset) },
                )
            },
        ),
    ) { (destination, scrim, sheet, navBar), constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val navBarPlaceables = navBar.map { it.measure(constraints.copy(minWidth = width, minHeight = 0)) }
        val navBarHeight = navBarPlaceables.maxOfOrNull { it.height } ?: 0
        player.onMeasured(
            PlayerSheetGeometry(
                height = height.toFloat(),
                navBarHeight = navBarHeight.toFloat(),
                miniHeight = miniHeight,
                queueTravel = stackedQueueTravel(height.toFloat(), statusBarTop, navigationBarBottom),
            ),
        )
        val fill = Constraints.fixed(width, height)
        val destinationPlaceables = destination.map { it.measure(fill) }
        val scrimPlaceables = scrim.map { it.measure(fill) }
        val sheetPlaceables = sheet.map { it.measure(fill) }
        layout(width, height) {
            destinationPlaceables.forEach { it.place(0, 0) }
            scrimPlaceables.forEach { it.place(0, 0) }
            val sheetTop = player.geometry.sheetTop(player.offset).roundToInt()
            sheetPlaceables.forEach { it.place(0, sheetTop) }
            navBarPlaceables.forEach { it.place(0, height - navBarHeight) }
        }
    }

    if (showMore) {
        ShellMoreSheet(onDismiss = { showMore = false }, onOpenSettings = {
            showMore = false
            onOpenSettings()
        })
    }
}

/**
 * 600 to 1199 dp: collapsed rail, the sheet without a Queue level. On Medium the sheet covers the
 * content pane and the rail stays live; on Expanded the mini player docks across the content area
 * and the expanded player grows over the rail to fill the window.
 */
@Composable
private fun RailSheetShell(
    player: PlayerSheetState,
    queue: ShellQueueUiState,
    layout: ShellLayout,
    selectedTab: ShellTab,
    onSelectTab: (ShellTab) -> Unit,
    onOpenSettings: () -> Unit,
    destinations: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val navigationBarBottom = WindowInsets.navigationBars.getBottom(density)
    val miniHeight = with(density) { MiniPlayerHeight.toPx() }
    val sheetVisible = rememberSheetVisible(player)
    val bottomPadding = rememberContentBottomPadding(player)
    val coversRail = layout.width == ShellWidth.Expanded

    Layout(
        contents = listOf(
            { ShellRail(selectedTab, expanded = false, onSelectTab = onSelectTab, onOpenSettings = onOpenSettings) },
            { Box(Modifier.fillMaxSize().padding(bottom = bottomPadding)) { destinations() } },
            { PlayerScrim(player) },
            { if (sheetVisible) PlayerSheet(player, queue, layout) },
        ),
    ) { (rail, destination, scrim, sheet), constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val railPlaceables = rail.map { it.measure(constraints.copy(minWidth = 0, minHeight = height)) }
        val railWidth = railPlaceables.maxOfOrNull { it.width } ?: 0
        val contentWidth = width - railWidth
        player.onMeasured(
            PlayerSheetGeometry(height = height.toFloat(), navBarHeight = navigationBarBottom.toFloat(), miniHeight = miniHeight, queueTravel = 0f),
        )
        // Reading the offset here re-runs layout, not composition, as the sheet grows over the rail.
        val sheetX = if (coversRail) lerp(railWidth.toFloat(), 0f, player.geometry.expand(player.offset)).roundToInt() else railWidth
        val destinationPlaceables = destination.map { it.measure(Constraints.fixed(contentWidth, height)) }
        val scrimX = if (coversRail) 0 else railWidth
        val scrimPlaceables = scrim.map { it.measure(Constraints.fixed(width - scrimX, height)) }
        val sheetPlaceables = sheet.map { it.measure(Constraints.fixed(width - sheetX, height)) }
        layout(width, height) {
            railPlaceables.forEach { it.place(0, 0) }
            destinationPlaceables.forEach { it.place(railWidth, 0) }
            scrimPlaceables.forEach { it.place(scrimX, 0) }
            val sheetTop = player.geometry.sheetTop(player.offset).roundToInt()
            sheetPlaceables.forEach { it.place(sheetX, sheetTop) }
        }
    }
}

/** From 1200 dp: rail, list-detail, and the persistent player pane on the trailing side. */
@Composable
private fun PaneShell(
    player: PlayerSheetState,
    queue: ShellQueueUiState,
    layout: ShellLayout,
    selectedTab: ShellTab,
    onSelectTab: (ShellTab) -> Unit,
    onOpenSettings: () -> Unit,
    destinations: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val paneOpen = player.level == PlayerLevel.NowPlaying || player.level == PlayerLevel.Queue
    val docked = player.level == PlayerLevel.Mini
    val spec = MaterialTheme.motionScheme.slowSpatialSpec<IntSize>()
    Row(Modifier.fillMaxSize()) {
        ShellRail(selectedTab, expanded = layout.railExpanded, onSelectTab = onSelectTab, onOpenSettings = onOpenSettings)
        Column(Modifier.weight(1f)) {
            Box(
                Modifier
                    .weight(1f)
                    .then(if (docked) Modifier else Modifier.windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))),
            ) {
                destinations()
            }
            if (docked) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                    MiniPlayer(
                        current = queue.current,
                        interactive = true,
                        onClick = { scope.launch { player.moveTo(PlayerLevel.NowPlaying) } },
                        modifier = Modifier.navigationBarsPadding(),
                    )
                }
            }
        }
        AnimatedVisibility(visible = paneOpen, enter = expandHorizontally(spec), exit = shrinkHorizontally(spec)) {
            PlayerPane(player, queue, width = playerPaneWidth(layout.width == ShellWidth.ExtraLarge), tabletopFold = layout.horizontalFold)
        }
    }
}

private val ShellTab.label: String
    get() = when (this) {
        ShellTab.Home -> "Home"
        ShellTab.Library -> "Library"
        ShellTab.Search -> "Search"
    }

private val ShellTab.icon: ImageVector
    get() = when (this) {
        ShellTab.Home -> Icons.Rounded.Home
        ShellTab.Library -> Icons.Rounded.LibraryMusic
        ShellTab.Search -> Icons.Rounded.Search
    }

@Composable
private fun ShellNavigationBar(
    selectedTab: ShellTab,
    onSelectTab: (ShellTab) -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ShortNavigationBar(modifier = modifier) {
        ShellTab.entries.forEach { tab ->
            ShortNavigationBarItem(
                selected = tab == selectedTab,
                onClick = { onSelectTab(tab) },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(tab.label) },
            )
        }
        ShortNavigationBarItem(
            selected = false,
            onClick = onMore,
            icon = { Icon(Icons.Rounded.MoreHoriz, contentDescription = null) },
            label = { Text("More") },
        )
    }
}

@Composable
private fun ShellRail(
    selectedTab: ShellTab,
    expanded: Boolean,
    onSelectTab: (ShellTab) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state = rememberWideNavigationRailState(if (expanded) WideNavigationRailValue.Expanded else WideNavigationRailValue.Collapsed)
    LaunchedEffect(state, expanded) { if (expanded) state.expand() else state.collapse() }
    WideNavigationRail(state = state) {
        ShellTab.entries.forEach { tab ->
            WideNavigationRailItem(
                selected = tab == selectedTab,
                onClick = { onSelectTab(tab) },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(tab.label) },
                railExpanded = expanded,
            )
        }
        // The settings drawer's entries are the rail's secondary items (app-shell.md, section 4).
        WideNavigationRailItem(
            selected = false,
            onClick = onOpenSettings,
            icon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
            label = { Text("Settings") },
            railExpanded = expanded,
        )
    }
}

/** The compact settings drawer: a shell-owned bottom sheet. Only Settings is wired in the spike. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShellMoreSheet(
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        ListItem(
            headlineContent = { Text("Settings") },
            leadingContent = { Icon(Icons.Rounded.Settings, contentDescription = null) },
            modifier = Modifier.clickable(onClick = onOpenSettings),
        )
    }
}
