package com.simplecityapps.shuttle.ui.shell.player

import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Constraints
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
 * it at [PlayerSheetGeometry.sheetTop]; everything inside tracks the offset in layout and layer
 * lambdas, so a drag never recomposes.
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
    val listState = rememberLazyListState()
    val panels = rememberNowPlayingPanels(listState, state, player, actions)
    val statusBarTop = WindowInsets.statusBars.getTop(LocalDensity.current).toFloat()
    val corner = with(LocalDensity.current) { SheetCorner.toPx() }

    PanelSettleEffect(state, listState, player, actions)

    ArtworkTheme(player.seed, ArtworkSchemeStyle.Player) {
        Surface(
            modifier = modifier
                .fillMaxSize()
                .graphicsLayer {
                    val radius = state.geometry.cornerRadius(state.offset, statusBarTop, corner)
                    shape = RoundedCornerShape(topStart = radius, topEnd = radius)
                    clip = radius > 0f
                }.testTag(PlayerTestTags.Sheet)
                .semantics { stateDescription = levelDescription }
                .nestedScroll(nestedScroll)
                .anchoredDraggable(state.draggable, orientation = Orientation.Vertical, flingBehavior = flingBehavior),
            color = PlayerSheetColor,
        ) {
            Box(Modifier.fillMaxSize()) {
                val nowPlayingModifier = Modifier
                    .hiddenFromSemantics(!nowPlayingShown)
                    .graphicsLayer { alpha = state.geometry.nowPlayingAlpha(state.offset) }
                if (state.mode == PlayerMode.CompactSheet) {
                    CompactNowPlaying(
                        state = state,
                        player = player,
                        progress = progress,
                        actions = actions,
                        listState = listState,
                        panels = panels,
                        tabletopFold = layout.horizontalFold,
                        onOpenRoute = onOpenRoute,
                        modifier = nowPlayingModifier,
                    )
                } else {
                    SideBySidePlayer(
                        player = player,
                        progress = progress,
                        actions = actions,
                        verticalFold = layout.verticalFold,
                        onCollapse = { scope.launch { state.moveTo(PlayerLevel.Mini) } },
                        onOpenRoute = onOpenRoute,
                        modifier = nowPlayingModifier,
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

    PlayerBackHandler(
        state = state,
        panelOpen = player.panel != null,
        onClosePanel = panels::close,
        onStepDown = { lower -> if (lower == PlayerLevel.NowPlaying) scope.launch { listState.animateScrollToItem(0) } },
    )
}

/**
 * Keeps the open panel in step with where the sheet comes to rest: settling at full height opens the
 * queue if nothing is open, which is how a drag up opens it; settling at a partial rest closes the
 * panel and scrolls back to the top, as does settling at Mini, since a panel belongs to the open player.
 */
@Composable
private fun PanelSettleEffect(
    state: PlayerSheetState,
    listState: LazyListState,
    player: PlayerUiState,
    actions: PlayerActions,
) {
    val currentPlayer by rememberUpdatedState(player)
    LaunchedEffect(state, listState, actions) {
        snapshotFlow { state.settledLevel to state.measured }.collect { (level, measured) ->
            if (!measured) return@collect
            val panel = currentPlayer.panel
            when {
                level == PlayerLevel.Expanded -> if (panel == null) actions.showPanel(NowPlayingPanel.Queue)

                level == PlayerLevel.Mini || (level == PlayerLevel.NowPlaying && state.geometry.partialRest) -> {
                    if (panel != null) actions.showPanel(null)
                    listState.scrollToItem(0)
                }
            }
        }
    }
}

/**
 * The compact sheet's Now Playing: [NowPlayingList] under the status bar once the sheet reaches it,
 * the pinned song over the list's top, and the bar pinned to the window's bottom edge wherever the
 * sheet is, so the list scrolls between them. The list's height is fixed, so a drag only re-places it.
 * On a [tabletopFold] the transport starts below the fold (see [nowPlayingRest]).
 */
@Composable
private fun CompactNowPlaying(
    state: PlayerSheetState,
    player: PlayerUiState,
    progress: () -> PlayerProgress,
    actions: PlayerActions,
    listState: LazyListState,
    panels: NowPlayingPanels,
    tabletopFold: Rect?,
    onOpenRoute: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val statusBarTop = WindowInsets.statusBars.getTop(density)
    val navigationBarBottom = WindowInsets.navigationBars.getBottom(density)
    val transportScrolledAway by rememberTransportScrolledAway(listState)
    BoxWithConstraints(modifier.fillMaxSize().testTag(PlayerTestTags.NowPlaying)) {
        val rest = with(density) { nowPlayingRest(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat(), statusBarTop, navigationBarBottom, tabletopFold) }
        val barHeight = with(density) { PlayerBarHeight.roundToPx() } + navigationBarBottom
        val viewport = (constraints.maxHeight - statusBarTop - barHeight).coerceAtLeast(0)
        Layout(
            contents = listOf(
                {
                    NowPlayingList(
                        player = player,
                        progress = progress,
                        actions = actions,
                        listState = listState,
                        artworkSize = with(density) { rest.artwork.toDp() },
                        artworkSlotHeight = with(density) { rest.artworkSlot.toDp() },
                        viewportHeight = with(density) { viewport.toDp() },
                        onCollapse = { scope.launch { state.moveTo(PlayerLevel.Mini) } },
                        onOpenRoute = onOpenRoute,
                    )
                },
                { PinnedSong(player, actions, visible = transportScrolledAway, onClick = panels::scrollToTitle) },
                {
                    NowPlayingBar(
                        player = player,
                        actions = actions,
                        selected = player.panel,
                        onPanel = panels::toggle,
                        modifier = Modifier.background(PlayerSheetColor).windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)),
                    )
                },
            ),
        ) { (list, pinned, bar), constraints ->
            val width = constraints.maxWidth
            val listPlaceables = list.map { it.measure(Constraints.fixed(width, viewport)) }
            val loose = Constraints(maxWidth = width, maxHeight = constraints.maxHeight)
            val pinnedPlaceables = pinned.map { it.measure(loose) }
            val barPlaceables = bar.map { it.measure(loose) }
            val barPlaceableHeight = barPlaceables.maxOfOrNull { it.height } ?: 0
            layout(width, constraints.maxHeight) {
                // Reading the offset here re-runs placement only.
                val offset = state.offset
                val top = state.geometry.contentTop(offset, statusBarTop.toFloat()).roundToInt()
                listPlaceables.forEach { it.place(0, top) }
                pinnedPlaceables.forEach { it.place(0, top) }
                val barTop = (constraints.maxHeight - state.geometry.sheetTop(offset)).roundToInt() - barPlaceableHeight
                barPlaceables.forEach { it.place(0, barTop) }
            }
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
        PlayerLevel.NowPlaying -> "Expanded"
        PlayerLevel.Expanded -> "Full screen"
    }

/**
 * Back steps the sheet down one level (Expanded → NowPlaying → Mini), following the gesture, then
 * disables so the destinations pop. Where the sheet has no level above its rest, back first closes
 * an open panel ([onClosePanel]). [onStepDown] runs as a step begins, with the level it heads for.
 * Keyed on the settled level so the handler re-registers, and so outranks handlers registered by
 * destinations, whenever the sheet comes to rest above Mini.
 */
@Composable
internal fun PlayerBackHandler(
    state: PlayerSheetState,
    panelOpen: Boolean = false,
    onClosePanel: () -> Unit = {},
    onStepDown: (PlayerLevel) -> Unit = {},
) {
    val from = state.settledLevel
    val to = from.stepDown()
    val sheet = state.mode != PlayerMode.Pane
    val closesPanel = panelOpen && from == PlayerLevel.NowPlaying && PlayerLevel.Expanded !in state.allowedLevels
    key(from, closesPanel) {
        BackHandler(enabled = sheet && closesPanel, onBack = onClosePanel)
        PredictiveBackHandler(enabled = sheet && !closesPanel && to != null) { progress ->
            val lower = to ?: return@PredictiveBackHandler
            val start = state.geometry.offsetOf(from)
            val end = state.geometry.offsetOf(lower)
            onStepDown(lower)
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
 * Hands scrolling between the sheet and Now Playing's list (M3's bottom sheet pattern): an upward
 * drag raises the sheet to full height before the list scrolls, and a downward drag the list
 * doesn't use, once it is back at the top, lowers the sheet to its rest and then to Mini.
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
