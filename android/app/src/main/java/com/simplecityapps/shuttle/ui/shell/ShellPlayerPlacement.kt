package com.simplecityapps.shuttle.ui.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.navigation3.runtime.NavKey
import com.simplecityapps.shuttle.designsystem.component.S2MiniPlayerHeight
import com.simplecityapps.shuttle.ui.shell.adaptive.ShellLayout
import com.simplecityapps.shuttle.ui.shell.adaptive.ShellWidth
import com.simplecityapps.shuttle.ui.shell.player.PlayerActions
import com.simplecityapps.shuttle.ui.shell.player.PlayerLevel
import com.simplecityapps.shuttle.ui.shell.player.PlayerMode
import com.simplecityapps.shuttle.ui.shell.player.PlayerPane
import com.simplecityapps.shuttle.ui.shell.player.PlayerProgress
import com.simplecityapps.shuttle.ui.shell.player.PlayerSheetState
import com.simplecityapps.shuttle.ui.shell.player.PlayerUiState
import com.simplecityapps.shuttle.ui.shell.player.playerPaneWidth

/** What every player surface shows and does, handed down the shell as one value. */
internal class PlayerContent(
    val state: PlayerUiState,
    val progress: () -> PlayerProgress,
    val actions: PlayerActions,
    val openRoute: (NavKey) -> Unit,
)

/**
 * Snackbars sit above whatever is docked at the bottom: the pane shell's docked mini player, or the rail shell's mini player.
 * The compact shell's nav bar and mini player slide, so it reports their height each frame instead.
 */
@Composable
internal fun rememberSnackbarBottomPadding(player: PlayerSheetState): Dp = if (player.mode == PlayerMode.Pane) {
    val navigationBars = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    if (player.level == PlayerLevel.Mini) navigationBars + S2MiniPlayerHeight else navigationBars
} else {
    rememberContentBottomPadding(player)
}

/** Sheet visible unless it is settled at Hidden: nothing is composed for an empty queue. */
@Composable
internal fun rememberSheetVisible(player: PlayerSheetState): Boolean {
    val visible by remember(player) { derivedStateOf { player.level != PlayerLevel.Hidden || player.settledLevel != PlayerLevel.Hidden } }
    return visible
}

/** Destination bottom padding: nav bar plus the revealed mini player. Follows reveal only, so a drag never reflows. */
@Composable
internal fun rememberContentBottomPadding(player: PlayerSheetState) = with(LocalDensity.current) {
    val padding by remember(player) { derivedStateOf { player.geometry.contentBottomPadding(player.geometry.reveal(player.offset)) } }
    padding.toDp()
}

/** The player pane, which slides in at the end edge while the player is [visible] at Full. */
@Composable
internal fun AnimatedPlayerPane(
    player: PlayerSheetState,
    content: PlayerContent,
    layout: ShellLayout,
    visible: Boolean,
    sideInsets: WindowInsets,
) {
    val spec = MaterialTheme.motionScheme.slowSpatialSpec<IntSize>()
    AnimatedVisibility(visible = visible, enter = expandHorizontally(spec), exit = shrinkHorizontally(spec)) {
        PlayerPane(
            player,
            content.state,
            content.progress,
            content.actions,
            width = playerPaneWidth(layout.width == ShellWidth.ExtraLarge),
            onOpenRoute = content.openRoute,
            sideInsets = sideInsets,
        )
    }
}
