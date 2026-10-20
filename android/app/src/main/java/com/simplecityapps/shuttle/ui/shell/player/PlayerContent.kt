package com.simplecityapps.shuttle.ui.shell.player

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import com.simplecityapps.shuttle.designsystem.theme.S2Spacing
import com.simplecityapps.shuttle.designsystem.theme.S2TouchTarget
import kotlin.math.roundToInt

/**
 * Medium and Expanded Now Playing: the player alone, centred, until a panel opens; then the player
 * and its bar beside the open panel's sheet, split at a vertical fold if there is one.
 */
@Composable
internal fun SideBySidePlayer(
    player: PlayerUiState,
    progress: () -> PlayerProgress,
    actions: PlayerActions,
    verticalFold: Rect?,
    horizontalFold: Rect?,
    compactHeight: Boolean,
    onCollapse: () -> Unit,
    onOpenRoute: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (horizontalFold != null) {
        Column(modifier.fillMaxSize().testTag(PlayerTestTags.NowPlaying).windowInsetsPadding(WindowInsets.statusBars)) {
            TabletopPlayer(
                player = player,
                progress = progress,
                actions = actions,
                fold = horizontalFold,
                onCollapse = onCollapse,
                onOpenRoute = onOpenRoute,
                bar = {
                    NowPlayingBar(player, actions, selected = player.panel, onPanel = actions::togglePanel, modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars))
                },
            )
        }
        return
    }
    val panel = player.panel
    // One FoldSplit whatever the panel, so the player keeps its place in the composition, and its
    // menu, seek and artwork, as a panel opens or closes; with none open it takes the whole width.
    FoldSplit(
        fold = verticalFold,
        orientation = Orientation.Horizontal,
        split = panel != null,
        modifier = modifier.fillMaxSize().testTag(PlayerTestTags.NowPlaying),
        first = {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                if (compactHeight) {
                    CompactHeightPlayer(player, progress, actions, onCollapse, onOpenRoute)
                    return@Box
                }
                Column(
                    Modifier
                        .widthIn(max = SideBySideMaxWidth)
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .windowInsetsPadding(WindowInsets.navigationBars),
                ) {
                    CollapseHandle(onCollapse)
                    // Nothing pushes this player, so the song and transport centre together in the room above the bar.
                    Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center) {
                        NowPlayingSong(player, actions, gap = S2Spacing.large, onOpenRoute = onOpenRoute, modifier = Modifier.weight(1f, fill = false).fillMaxWidth())
                        Transport(player, progress, actions, gap = S2Spacing.large)
                    }
                    NowPlayingBar(player, actions, selected = panel, onPanel = actions::togglePanel)
                }
            }
        },
        second = {
            if (panel != null) {
                Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
                    // The panel starts level with the player's handle.
                    Spacer(Modifier.height(S2TouchTarget.minimum))
                    PlayerPanel(
                        panel = panel,
                        player = player,
                        actions = actions,
                        onOpenRoute = onOpenRoute,
                        onClose = { actions.showPanel(null) },
                        modifier = Modifier.weight(1f).padding(end = S2Spacing.small),
                        contentPadding = WindowInsets.navigationBars.asPaddingValues(),
                    )
                }
            }
        },
    )
}

/**
 * A landscape phone's player: the artwork square on the left at the full height, and on the right
 * the handle, the title, the transport and the bar, centred. Stacked, the artwork would be squeezed out.
 * With a panel open, the controls alone fill their half and the panel takes the other.
 */
@Composable
private fun CompactHeightPlayer(
    player: PlayerUiState,
    progress: () -> PlayerProgress,
    actions: PlayerActions,
    onCollapse: () -> Unit,
    onOpenRoute: (NavKey) -> Unit,
) {
    Row(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // An open panel takes half the width, which leaves the artwork no room beside the controls.
        if (player.panel == null) {
            Box(Modifier.fillMaxHeight().aspectRatio(1f)) {
                NowPlayingArtwork(player, actions, gap = S2Spacing.small, modifier = Modifier.fillMaxSize())
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
            CollapseHandle(onCollapse)
            NowPlayingTitle(player, actions, onOpenRoute)
            Transport(player, progress, actions, gap = S2Spacing.small)
            NowPlayingBar(player, actions, selected = player.panel, onPanel = actions::togglePanel)
        }
    }
}

/** The widest the side-by-side player grows on its own, before a panel shares the width. */
private val SideBySideMaxWidth = 560.dp

/**
 * Two slots split along [orientation] (Horizontal: side by side). A separating [fold], in window
 * pixels, is the boundary and nothing is laid out inside it; without one the slots split evenly.
 * Unless [split], [first] takes the whole length and [second] none.
 */
@Composable
internal fun FoldSplit(
    fold: Rect?,
    orientation: Orientation,
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    split: Boolean = true,
) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    Layout(
        contents = listOf(first, second),
        modifier = modifier.onPlaced { origin = it.positionInWindow() },
    ) { (firstMeasurables, secondMeasurables), constraints ->
        val horizontal = orientation == Orientation.Horizontal
        val total = if (horizontal) constraints.maxWidth else constraints.maxHeight
        val cross = if (horizontal) constraints.maxHeight else constraints.maxWidth
        val (firstEnd, secondStart) = if (!split) {
            total to total
        } else {
            fold
                ?.let { bounds ->
                    val start = (if (horizontal) bounds.left - origin.x else bounds.top - origin.y).roundToInt()
                    val end = (if (horizontal) bounds.right - origin.x else bounds.bottom - origin.y).roundToInt()
                    if (start in 1 until total && end <= total) start to end else null
                }
                ?: (total / 2 to total / 2)
        }

        fun slot(length: Int) = if (horizontal) Constraints.fixed(length, cross) else Constraints.fixed(cross, length)
        val firstPlaceables = firstMeasurables.map { it.measure(slot(firstEnd)) }
        val secondPlaceables = secondMeasurables.map { it.measure(slot(total - secondStart)) }
        val width = if (horizontal) total else cross
        val height = if (horizontal) cross else total
        layout(width, height) {
            firstPlaceables.forEach { it.place(0, 0) }
            secondPlaceables.forEach { if (horizontal) it.place(secondStart, 0) else it.place(0, secondStart) }
        }
    }
}
