package com.simplecityapps.shuttle.ui.shell.player

import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.util.lerp
import androidx.navigation3.runtime.NavKey
import com.simplecityapps.shuttle.designsystem.theme.ArtworkSchemeStyle
import com.simplecityapps.shuttle.designsystem.theme.ArtworkTheme
import com.simplecityapps.shuttle.designsystem.theme.ContinuousRoundedCornerShape
import com.simplecityapps.shuttle.ui.shell.adaptive.ShellLayout
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The player sheet below 1200 dp (docs/architecture/app-shell.md, section 1). The shell places
 * it at [PlayerSheetGeometry.sheetTop]; everything inside tracks the offset in layout and layer
 * lambdas, so a drag never recomposes. Dragging the sheet, or tapping, moves it between Mini and
 * Full; nothing inside hands its scrolling to the sheet.
 */
@Composable
internal fun PlayerSheet(
    state: PlayerSheetState,
    player: PlayerUiState,
    progress: () -> PlayerProgress,
    actions: PlayerActions,
    layout: ShellLayout,
    onOpenRoute: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    collapsedInset: () -> Int = { 0 },
) {
    val scope = rememberCoroutineScope()
    val flingBehavior = AnchoredDraggableDefaults.flingBehavior(state.draggable, animationSpec = state.animationSpec)
    val miniInteractive by remember(state) { derivedStateOf { state.geometry.miniInteractive(state.geometry.expand(state.offset)) } }
    val levelDescription = state.settledLevel.description
    val nowPlayingShown by remember(state) { derivedStateOf { state.geometry.nowPlayingAlpha(state.offset) > 0f } }
    val statusBarTop = WindowInsets.statusBars.getTop(LocalDensity.current).toFloat()
    val corner = with(LocalDensity.current) { SheetCorner.toPx() }
    val collapse = { scope.launch { state.moveTo(PlayerLevel.Mini) } }

    PanelResetEffect(state, player, actions)

    ArtworkTheme(player.seed, ArtworkSchemeStyle.Player) {
        Surface(
            modifier = modifier
                .fillMaxSize()
                .graphicsLayer {
                    val radius = state.geometry.cornerRadius(state.offset, statusBarTop, corner)
                    shape = ContinuousRoundedCornerShape(topStart = radius, topEnd = radius)
                    clip = radius > 0f
                }.testTag(PlayerTestTags.Sheet)
                .semantics { stateDescription = levelDescription }
                .anchoredDraggable(state.draggable, orientation = Orientation.Vertical, flingBehavior = flingBehavior),
            color = PlayerSheetColor,
        ) {
            Box(Modifier.fillMaxSize()) {
                val nowPlayingModifier = Modifier
                    .hiddenFromSemantics(!nowPlayingShown)
                    .graphicsLayer { alpha = state.geometry.nowPlayingAlpha(state.offset) }
                if (state.mode == PlayerMode.CompactSheet) {
                    FullPlayer(
                        player = player,
                        progress = progress,
                        actions = actions,
                        onCollapse = { collapse() },
                        onOpenRoute = onOpenRoute,
                        tabletopFold = layout.horizontalFold,
                        modifier = nowPlayingModifier,
                    )
                } else {
                    SideBySidePlayer(
                        player = player,
                        progress = progress,
                        actions = actions,
                        verticalFold = layout.verticalFold,
                        onCollapse = { collapse() },
                        onOpenRoute = onOpenRoute,
                        modifier = nowPlayingModifier,
                    )
                }
                MiniPlayer(
                    player = player,
                    progress = progress,
                    actions = actions,
                    interactive = miniInteractive,
                    onClick = { scope.launch { state.moveTo(PlayerLevel.Full) } },
                    modifier = Modifier
                        .collapsedInset(collapsedInset) { state.geometry.expand(state.offset) }
                        .graphicsLayer { alpha = state.geometry.miniAlpha(state.offset) },
                )
            }
        }
    }

    PlayerBackHandler(state, panelOpen = player.panel != null, onClosePanel = { actions.showPanel(null) })
}

/**
 * Closes the open panel once the player comes to rest at Mini, however it got there, so the player
 * always opens on the song; a panel belongs to the open player.
 */
@Composable
internal fun PanelResetEffect(
    state: PlayerSheetState,
    player: PlayerUiState,
    actions: PlayerActions,
) {
    val currentPlayer by rememberUpdatedState(player)
    LaunchedEffect(state, actions) {
        snapshotFlow { state.settledLevel }.collect { level ->
            if (level != PlayerLevel.Full && currentPlayer.panel != null) actions.showPanel(null)
        }
    }
}

/**
 * Narrows the mini player by [inset] px and slides it back to the leading edge as the sheet
 * expands, for a sheet that grows over the rail. [expand] is read in placement only.
 */
private fun Modifier.collapsedInset(
    inset: () -> Int,
    expand: () -> Float,
): Modifier = layout { measurable, constraints ->
    val insetPx = inset()
    val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = (constraints.maxWidth - insetPx).coerceAtLeast(0)))
    layout(constraints.maxWidth, placeable.height) {
        placeable.placeRelative((insetPx * (1f - expand())).roundToInt(), 0)
    }
}

/** Spoken state of the sheet, which tests also read to find the settled level. */
internal val PlayerLevel.description: String
    get() = when (this) {
        PlayerLevel.Hidden -> "Hidden"
        PlayerLevel.Mini -> "Collapsed"
        PlayerLevel.Full -> "Expanded"
    }

/**
 * Back closes an open panel first ([onClosePanel]), then collapses the sheet from Full to Mini,
 * following the gesture, then disables so the destinations pop. Keyed on the settled level so the
 * handler re-registers, and so outranks handlers registered by destinations, whenever the sheet
 * comes to rest at Full.
 */
@Composable
internal fun PlayerBackHandler(
    state: PlayerSheetState,
    panelOpen: Boolean = false,
    onClosePanel: () -> Unit = {},
) {
    val from = state.settledLevel
    val to = from.stepDown()
    val sheet = state.mode != PlayerMode.Pane
    val closesPanel = panelOpen && from == PlayerLevel.Full
    key(from, closesPanel) {
        BackHandler(enabled = sheet && closesPanel, onBack = onClosePanel)
        PredictiveBackHandler(enabled = sheet && !closesPanel && to != null) { progress ->
            val lower = to ?: return@PredictiveBackHandler
            val start = state.geometry.offsetOf(from)
            val end = state.geometry.offsetOf(lower)
            try {
                state.draggable.anchoredDrag {
                    progress.collect { event -> dragTo(lerp(start, end, event.progress)) }
                }
                state.draggable.animateTo(lower, state.animationSpec)
            } catch (e: CancellationException) {
                withContext(NonCancellable) { state.draggable.animateTo(from, state.animationSpec) }
                throw e
            }
        }
    }
}

/**
 * Scrim over the destinations: its alpha follows expand, and while the sheet is rising or falling it
 * takes taps, which settle the player back to Mini.
 */
@Composable
internal fun PlayerScrim(
    state: PlayerSheetState,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val active by remember(state) { derivedStateOf { state.geometry.expand(state.offset) > 0f } }
    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { alpha = state.geometry.scrimAlpha(state.offset) }
            .background(MaterialTheme.colorScheme.scrim)
            .then(
                if (active) {
                    Modifier
                        .testTag(PlayerTestTags.Scrim)
                        .clickable(interactionSource = null, indication = null, onClickLabel = "Collapse player") {
                            scope.launch { state.moveTo(PlayerLevel.Mini) }
                        }
                } else {
                    Modifier
                },
            ),
    )
}
