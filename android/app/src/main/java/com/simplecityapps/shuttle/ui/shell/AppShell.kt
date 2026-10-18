package com.simplecityapps.shuttle.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import com.simplecityapps.shuttle.designsystem.component.S2SnackbarHost
import com.simplecityapps.shuttle.designsystem.theme.LocalS2ThemeSettings
import com.simplecityapps.shuttle.ui.common.mediaactions.MediaActionsHost
import com.simplecityapps.shuttle.ui.common.mediaactions.MediaActionsViewModel
import com.simplecityapps.shuttle.ui.screens.library.openTarget
import com.simplecityapps.shuttle.ui.shell.adaptive.LocalShellLayout
import com.simplecityapps.shuttle.ui.shell.adaptive.ShellLayout
import com.simplecityapps.shuttle.ui.shell.player.LocalPlayerMediaActions
import com.simplecityapps.shuttle.ui.shell.player.PlayerActions
import com.simplecityapps.shuttle.ui.shell.player.PlayerLevel
import com.simplecityapps.shuttle.ui.shell.player.PlayerMode
import com.simplecityapps.shuttle.ui.shell.player.PlayerProgress
import com.simplecityapps.shuttle.ui.shell.player.PlayerSheetState
import com.simplecityapps.shuttle.ui.shell.player.PlayerUiState
import com.simplecityapps.shuttle.ui.shell.player.rememberPlayerSheetState
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The Compose app shell (docs/architecture/app-shell.md): navigation bar or rail by width class,
 * a Navigation 3 display with list-detail, and the player as a sheet below 1200 dp or a
 * persistent pane from 1200 dp. The player state sits above the class branch, so one level
 * survives every resize, fold and rotation. [entryProvider] maps each route to its screen; tests of
 * the shell itself swap in screens that need no Metro graph.
 */
@Composable
fun AppShell(
    playerUi: PlayerUiState,
    progress: () -> PlayerProgress,
    actions: PlayerActions,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    startTab: ShellTab = ShellTab.Home,
    windowAdaptiveInfo: WindowAdaptiveInfo = currentWindowAdaptiveInfoV2(),
    entryProvider: (AppNavigator) -> (NavKey) -> NavEntry<NavKey> = ::shellEntryProvider,
    shellRequests: Flow<ShellRequest> = emptyFlow(),
    mediaActionsViewModel: MediaActionsViewModel = metroViewModel(),
    appGraphAvailable: Boolean = true,
) {
    val layout = remember(windowAdaptiveInfo) { ShellLayout.from(windowAdaptiveInfo) }
    val navigator = rememberAppNavigator(startTab)
    val player = rememberPlayerSheetState(layout.playerMode)
    SideEffect { player.configure(layout.playerMode, playerUi.hasQueue) }
    LaunchedEffect(player, player.revealPending) { if (player.revealPending) player.reveal() }
    LaunchedEffect(player, player.collapsePending) { if (player.collapsePending) player.collapse() }
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
    // A destination another entry point asks for, such as a launcher shortcut.
    LaunchedEffect(shellRequests) {
        shellRequests.collect { request ->
            if (request.openPlayer) {
                // On a cold start Full is not allowed until the queue has reported.
                snapshotFlow { player.hasQueue }.first { it != null }
                player.moveTo(PlayerLevel.Full)
            } else {
                navigate { navigator.show(request) }
            }
        }
    }
    // Screens post to the shell's one snackbar host, which sits above the nav bar and mini player.
    val statusBarRequests = remember { StatusBarRequests() }
    ApplyStatusBarAppearance(
        statusBarRequests,
        playerLevel = if (player.mode == PlayerMode.Pane) PlayerLevel.Mini else player.level,
        darkTheme = LocalS2ThemeSettings.current.isDark,
    )
    val destinations: @Composable () -> Unit = {
        CompositionLocalProvider(
            LocalShellSnackbarHostState provides snackbarHostState,
            LocalTabReselects provides navigator.reselects,
            LocalStatusBarRequests provides statusBarRequests,
        ) {
            ShellNavDisplay(navigator, layout, windowAdaptiveInfo, entryProvider)
        }
    }

    // The player's links into Settings (Equalizer, Playback & sound) settle it to Mini like any other navigation.
    val playerContent = PlayerContent(playerUi, progress, actions, openRoute = { route -> navigate { navigator.openFromPlayer(route) } })
    // Back from a route the player opened returns to the player, not the tab beneath it.
    LaunchedEffect(player, navigator.playerReturnPending) {
        if (navigator.playerReturnPending) {
            navigator.consumePlayerReturn()
            player.moveTo(PlayerLevel.Full)
        }
    }
    // No tab is lit, and the nav bar or rail slides away, while a utility destination shows.
    val selectedTab = navigator.selectedTab.takeIf { navigator.showsNavigation }
    // The player's song menus and its queue's save-to-playlist share the destinations' media action path; the screens a result opens settle the player first.
    // The layout is provided above the whole surface so the player sheet, rail and overlays read the same value as the destinations.
    CompositionLocalProvider(LocalShellSnackbarHostState provides snackbarHostState, LocalShellLayout provides layout) {
        MediaActionsHost(onNavigate = { target -> navigate { navigator.openTarget(target) } }, viewModel = mediaActionsViewModel, appGraphAvailable = appGraphAvailable) { mediaActions ->
            CompositionLocalProvider(LocalPlayerMediaActions provides mediaActions) {
                ShellSurface(modifier, layout, player, playerContent, selectedTab, onSelectTab, destinations, snackbarHostState)
            }
        }
    }
}

@Composable
private fun ShellSurface(
    modifier: Modifier,
    layout: ShellLayout,
    player: PlayerSheetState,
    playerContent: PlayerContent,
    selectedTab: ShellTab?,
    onSelectTab: (ShellTab) -> Unit,
    destinations: @Composable () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Box(Modifier.fillMaxSize()) {
            // The compact shell reports how far up the docked chrome has slid this frame, for the snackbar to ride.
            val compactDockedHeight = remember { mutableIntStateOf(0) }
            when (layout.playerMode) {
                PlayerMode.CompactSheet -> CompactShell(player, playerContent, layout, selectedTab, onSelectTab, destinations, compactDockedHeight)
                PlayerMode.Sheet -> RailSheetShell(player, playerContent, layout, selectedTab, onSelectTab, destinations)
                PlayerMode.Pane -> PaneShell(player, playerContent, layout, selectedTab, onSelectTab, destinations)
            }
            S2SnackbarHost(
                snackbarHostState,
                Modifier
                    .align(Alignment.BottomCenter)
                    .then(
                        if (layout.playerMode == PlayerMode.CompactSheet) {
                            Modifier.offset { IntOffset(0, -compactDockedHeight.intValue) }
                        } else {
                            Modifier.padding(bottom = rememberSnackbarBottomPadding(player))
                        },
                    ),
            )
        }
    }
}
