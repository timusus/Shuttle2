package com.simplecityapps.shuttle.ui.shell.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import com.simplecityapps.shuttle.designsystem.theme.ArtworkSchemeStyle
import com.simplecityapps.shuttle.designsystem.theme.ArtworkTheme
import kotlinx.coroutines.launch

/** The persistent player pane's width: 360 dp, 412 dp at Extra-large (app-shell.md, section 2). */
fun playerPaneWidth(extraLarge: Boolean): Dp = if (extraLarge) 412.dp else 360.dp

/**
 * The trailing player pane from 1200 dp, shown at Full; Mini collapses it to the docked mini
 * player. It holds the compact sheet's [FullPlayer] without the sheet: no gesture drives it, and its
 * panels open from the bar in the same way. Back closes an open panel, as it does in the sheet.
 */
@Composable
internal fun PlayerPane(
    state: PlayerSheetState,
    player: PlayerUiState,
    progress: () -> PlayerProgress,
    actions: PlayerActions,
    width: Dp,
    onOpenRoute: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    PanelResetEffect(state, player, actions)
    ArtworkTheme(player.seed, ArtworkSchemeStyle.Player) {
        Surface(
            modifier = modifier.width(width).fillMaxHeight().testTag(PlayerTestTags.Pane),
            color = PlayerSheetColor,
        ) {
            Box {
                PlayerGround()
                FullPlayer(
                    player = player,
                    progress = progress,
                    actions = actions,
                    onCollapse = { scope.launch { state.moveTo(PlayerLevel.Mini) } },
                    onOpenRoute = onOpenRoute,
                )
            }
        }
    }
    PlayerBackHandler(state, panelOpen = player.panel != null, onClosePanel = { actions.showPanel(null) })
}
