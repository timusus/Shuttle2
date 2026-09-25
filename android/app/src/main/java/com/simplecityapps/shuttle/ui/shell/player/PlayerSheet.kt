package com.simplecityapps.shuttle.ui.shell.player

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.util.lerp
import androidx.navigation3.runtime.NavKey
import com.simplecityapps.shuttle.designsystem.theme.ArtworkSchemeStyle
import com.simplecityapps.shuttle.designsystem.theme.ArtworkTheme
import com.simplecityapps.shuttle.ui.shell.adaptive.ShellLayout
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The player sheet below 1200 dp (docs/architecture/app-shell.md, section 1). The shell places
 * it at [PlayerSheetGeometry.sheetTop]; everything inside tracks the offset in layer lambdas, so
 * a drag never recomposes.
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
    val nestedScroll = remember(state, flingBehavior) { PlayerSheetNestedScrollConnection(state, flingBehavior) }
    val miniInteractive by remember(state) { derivedStateOf { state.geometry.miniInteractive(state.geometry.expand(state.offset)) } }
    val levelDescription = state.settledLevel.description
    val nowPlayingShown by remember(state) { derivedStateOf { state.geometry.nowPlayingAlpha(state.offset) > 0f } }

    ArtworkTheme(player.seed, ArtworkSchemeStyle.Player) {
        Surface(
            modifier = modifier
                .fillMaxSize()
                .testTag(PlayerTestTags.Sheet)
                .semantics { stateDescription = levelDescription }
                .nestedScroll(nestedScroll)
                .anchoredDraggable(state.draggable, orientation = Orientation.Vertical, flingBehavior = flingBehavior),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Box(Modifier.fillMaxSize()) {
                val geometry = { state.geometry }
                val offset = { state.offset }
                if (state.mode == PlayerMode.CompactSheet) {
                    StackedPlayer(
                        player = player,
                        progress = progress,
                        actions = actions,
                        geometry = geometry,
                        offset = offset,
                        tabletopFold = layout.horizontalFold,
                        onCollapse = { scope.launch { state.moveTo(PlayerLevel.Mini) } },
                        onShowQueue = { scope.launch { state.moveTo(PlayerLevel.Queue) } },
                        onOpenRoute = onOpenRoute,
                    )
                } else {
                    SideBySidePlayer(
                        player = player,
                        progress = progress,
                        actions = actions,
                        verticalFold = layout.verticalFold,
                        onCollapse = { scope.launch { state.moveTo(PlayerLevel.Mini) } },
                        onOpenRoute = onOpenRoute,
                        modifier = Modifier
                            .hiddenFromSemantics(!nowPlayingShown)
                            .graphicsLayer { alpha = state.geometry.nowPlayingAlpha(state.offset) },
                    )
                }
                MiniPlayer(
                    player = player,
                    progress = progress,
                    actions = actions,
                    interactive = miniInteractive,
                    onClick = { scope.launch { state.moveTo(PlayerLevel.NowPlaying) } },
                    modifier = Modifier
                        .collapsedInset(collapsedInset) { state.geometry.expand(state.offset) }
                        .graphicsLayer { alpha = state.geometry.miniAlpha(state.offset) },
                )
            }
        }
    }

    PlayerBackHandler(state)
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
        PlayerLevel.NowPlaying -> "Expanded"
        PlayerLevel.Queue -> "Showing queue"
    }

/**
 * Back steps the sheet down one level (Queue → NowPlaying → Mini), following the gesture, then
 * disables so the destinations pop. Keyed on the settled level so the handler re-registers, and
 * so outranks handlers registered by destinations, whenever the sheet comes to rest above Mini.
 */
@Composable
internal fun PlayerBackHandler(state: PlayerSheetState) {
    val from = state.settledLevel
    val to = from.stepDown()
    key(from) {
        PredictiveBackHandler(enabled = to != null && state.mode != PlayerMode.Pane) { progress ->
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
 * Scrim over the destinations: its alpha follows expand, and while the player is above Mini it
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

/**
 * Hands scrolling between the sheet and the queue list (M3's bottom sheet pattern): an upward
 * drag raises the sheet towards Queue before the list scrolls, and a downward drag the list
 * doesn't use pulls the sheet down.
 */
internal class PlayerSheetNestedScrollConnection(
    private val state: PlayerSheetState,
    private val flingBehavior: FlingBehavior,
) : NestedScrollConnection {
    override fun onPreScroll(
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        val delta = available.y
        return if (delta < 0 && source == NestedScrollSource.UserInput) Offset(0f, state.dispatchRawDelta(delta)) else Offset.Zero
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        val delta = available.y
        return if (delta != 0f && source == NestedScrollSource.UserInput) Offset(0f, state.dispatchRawDelta(delta)) else Offset.Zero
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        val velocity = available.y
        return if (velocity < 0 && state.offset > state.minOffset) {
            state.fling(flingBehavior, velocity)
            // Settling goes to an anchor, so the list gets none of this fling.
            available
        } else {
            Velocity.Zero
        }
    }

    override suspend fun onPostFling(
        consumed: Velocity,
        available: Velocity,
    ): Velocity = Velocity(0f, state.fling(flingBehavior, available.y))
}
