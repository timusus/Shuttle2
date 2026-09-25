package com.simplecityapps.shuttle.ui.shell.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.simplecityapps.shuttle.ui.shell.ShellQueueUiState
import kotlinx.coroutines.launch

/** The persistent player pane's width: 360 dp, 412 dp at Extra-large (app-shell.md, section 2). */
fun playerPaneWidth(extraLarge: Boolean): Dp = if (extraLarge) 412.dp else 360.dp

/**
 * The trailing player pane from 1200 dp, shown at NowPlaying and Queue; Mini collapses it to the
 * docked mini player. No gesture drives it: the level snaps and the queue push animates on the
 * level with the slow spatial spec.
 */
@Composable
internal fun PlayerPane(
    state: PlayerSheetState,
    queue: ShellQueueUiState,
    width: Dp,
    tabletopFold: Rect?,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val queueFraction by animateFloatAsState(
        targetValue = if (state.level == PlayerLevel.Queue) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
        label = "paneQueue",
    )
    Surface(
        modifier = modifier.width(width).fillMaxHeight().testTag(PlayerTestTags.Pane),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        BoxWithConstraints {
            val density = LocalDensity.current
            val height = with(density) { maxHeight.toPx() }
            val statusBarTop = WindowInsets.statusBars.getTop(density)
            val navigationBarBottom = WindowInsets.navigationBars.getBottom(density)
            // The pane's own stacked geometry: always fully expanded, pushed up by the queue as a compact sheet is.
            val geometry = remember(height, density, statusBarTop, navigationBarBottom) {
                with(density) {
                    PlayerSheetGeometry(
                        height = height,
                        navBarHeight = 0f,
                        miniHeight = MiniPlayerHeight.toPx(),
                        queueTravel = stackedQueueTravel(height, statusBarTop, navigationBarBottom),
                    )
                }
            }
            StackedPlayer(
                queue = queue,
                geometry = { geometry },
                offset = { -geometry.queueTravel * queueFraction },
                tabletopFold = tabletopFold,
                onCollapse = { scope.launch { state.moveTo(PlayerLevel.Mini) } },
                onShowQueue = {
                    scope.launch { state.moveTo(if (state.level == PlayerLevel.Queue) PlayerLevel.NowPlaying else PlayerLevel.Queue) }
                },
            )
        }
    }
}
