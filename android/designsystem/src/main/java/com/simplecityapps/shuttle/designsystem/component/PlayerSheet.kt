package com.simplecityapps.shuttle.designsystem.component

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.simplecityapps.shuttle.designsystem.R
import com.simplecityapps.shuttle.designsystem.preview.S2Preview
import com.simplecityapps.shuttle.designsystem.theme.ContinuousRoundedCornerShape
import com.simplecityapps.shuttle.designsystem.theme.S2Spacing
import com.simplecityapps.shuttle.designsystem.theme.S2TouchTarget
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** The player sheet's measurements, shared by the sheet, the panel sheet inside it and the pane. */
object S2SheetDefaults {
    /** The top corners of the player sheet while it moves, and of a [S2PanelSheet]. */
    val corner: Dp = 28.dp

    /** A [S2PanelSheet]'s grip: short, since the header under it drags the sheet too. */
    val gripHeight: Dp = S2Spacing.medium

    /** The share of the [S2ExpandableSheetScaffold]'s height a panel takes, so the transport stays in view above it. */
    const val MAX_PANEL_FRACTION = 0.6f
}

/** The pill's size: M3's bottom sheet drag handle. */
private val HandleWidth = 32.dp
private val HandleThickness = 4.dp

/** How fast a downward fling dismisses a [S2PanelSheet] however little it has moved, per second. */
private val PanelDismissVelocity = 800.dp

/**
 * The sheet handle: a centred pill in a [height] tall strip, a full touch target by default. With
 * [onClick] it's a button, spoken as [contentDescription] (the player's "Collapse player"), which
 * names the action, so no click label repeats it.
 */
@Composable
fun S2SheetHandle(
    modifier: Modifier = Modifier,
    height: Dp = S2TouchTarget.minimum,
    contentDescription: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val clickModifier = if (onClick != null) {
        Modifier
            .clickable(onClick = onClick)
            .semantics { contentDescription?.let { this.contentDescription = it } }
    } else {
        Modifier
    }
    Box(modifier.fillMaxWidth().height(height).then(clickModifier), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(width = HandleWidth, height = HandleThickness)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), CircleShape),
        )
    }
}

/**
 * A sheet inside the player that holds a panel (the queue, the sleep timer): rounded top corners,
 * a [S2SheetHandle] grip, and [content], as tall as [content] up to the height it's given. Dragging
 * the grip down, or anything [content] passes the `grip` modifier to, or pulling its scrolling
 * content down past the top, calls [onDismiss] once it has moved a quarter of its height or is
 * flung; short of that it springs back. Accessibility services close it from the grip, "Close panel".
 */
@Composable
fun S2PanelSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
    content: @Composable ColumnScope.(grip: Modifier) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    var offset by remember { mutableFloatStateOf(0f) }
    var height by remember { mutableIntStateOf(0) }
    // Cancelled by the next drag or scroll, so only one thing moves the sheet at a time.
    var springBack by remember { mutableStateOf<Job?>(null) }
    val stopSpringBack = { springBack?.cancel() }
    val dismissVelocity = with(LocalDensity.current) { PanelDismissVelocity.toPx() }
    val settle: (Float) -> Unit = remember(scope, dismissVelocity) {
        { velocity ->
            springBack?.cancel()
            if (offset > height / 4f || velocity > dismissVelocity) {
                currentOnDismiss()
            } else {
                springBack = scope.launch { animate(offset, 0f) { value, _ -> offset = value } }
            }
        }
    }
    val pullToDismiss = remember(settle) {
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (available.y >= 0f || offset <= 0f) return Offset.Zero
                stopSpringBack()
                val consumed = maxOf(available.y, -offset)
                offset += consumed
                return Offset(0f, consumed)
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (available.y <= 0f || source != NestedScrollSource.UserInput) return Offset.Zero
                stopSpringBack()
                offset += available.y
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (offset <= 0f) return Velocity.Zero
                settle(available.y)
                return available
            }
        }
    }
    val grip = Modifier.draggable(
        state = rememberDraggableState { delta -> offset = (offset + delta).coerceAtLeast(0f) },
        orientation = Orientation.Vertical,
        onDragStarted = { stopSpringBack() },
        onDragStopped = { velocity -> settle(velocity) },
    )
    val closeLabel = stringResource(R.string.ds_close_panel)
    Surface(
        modifier = modifier
            .onSizeChanged { height = it.height }
            .graphicsLayer { translationY = offset }
            .nestedScroll(pullToDismiss),
        shape = ContinuousRoundedCornerShape(topStart = S2SheetDefaults.corner, topEnd = S2SheetDefaults.corner),
        color = color,
    ) {
        Column(Modifier.fillMaxWidth()) {
            S2SheetHandle(
                grip.semantics {
                    contentDescription = closeLabel
                    onClick {
                        currentOnDismiss()
                        true
                    }
                    dismiss {
                        currentOnDismiss()
                        true
                    }
                },
                height = S2SheetDefaults.gripHeight,
            )
            content(grip)
        }
    }
}

/**
 * The player's "sheet within a sheet", after Shuttle Podcasts' `ExpandableSheetScaffold`: a [handle]
 * over [content], with the [bottomBar] along the bottom. While [expanded], [expandedContent] (a
 * [S2PanelSheet]) slides up from the bar, as tall as it needs up to [maxExpandedFraction] of the
 * height above it, and pushes [content] up by its own height, so the bottom of [content] (the
 * transport) stays in view above it and the top clips away. The bar stays put: its buttons switch and
 * close the panels. Each opening starts [expandedContent] afresh, so a panel reopened while it was
 * still sliding away isn't left where a drag had moved it.
 */
@Composable
fun S2ExpandableSheetScaffold(
    expanded: Boolean,
    bottomBar: @Composable () -> Unit,
    expandedContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    handle: (@Composable () -> Unit)? = null,
    maxExpandedFraction: Float = S2SheetDefaults.MAX_PANEL_FRACTION,
    content: @Composable ColumnScope.() -> Unit,
) {
    var areaHeight by remember { mutableIntStateOf(0) }
    var panelHeight by remember { mutableIntStateOf(0) }
    // A position that mustn't overshoot: a bounce would open a gap between the panel and the bar.
    val fraction by animateFloatAsState(if (expanded) 1f else 0f, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "panel")
    val panelShown by remember { derivedStateOf { fraction > 0f } }
    val openings = remember { intArrayOf(0) }
    val opening = remember(expanded) { if (expanded) ++openings[0] else openings[0] }
    Column(modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds().onSizeChanged { areaHeight = it.height }) {
            Column(Modifier.fillMaxSize().offset { IntOffset(0, -(panelHeight * fraction).roundToInt()) }) {
                // The panel's grip is the one handle while it's open.
                if (handle != null) Box(if (expanded) Modifier.clearAndSetSemantics { } else Modifier) { handle() }
                content()
            }
            if (expanded || panelShown) {
                val maxHeight = with(LocalDensity.current) { (areaHeight * maxExpandedFraction).toDp() }
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .offset { IntOffset(0, (panelHeight * (1f - fraction)).roundToInt()) }
                        .heightIn(max = maxHeight)
                        .onSizeChanged { panelHeight = it.height }
                        // Switching to a panel of another height moves the content above with it, without overshooting.
                        .animateContentSize(MaterialTheme.motionScheme.defaultEffectsSpec())
                        .then(if (expanded) Modifier else Modifier.clearAndSetSemantics { }),
                ) {
                    key(opening) { expandedContent() }
                }
            }
        }
        bottomBar()
    }
}

@Preview(heightDp = 480)
@Composable
private fun S2ExpandableSheetScaffoldPreview() {
    S2Preview {
        S2ExpandableSheetScaffold(
            expanded = true,
            bottomBar = { Text("Bar", Modifier.padding(S2Spacing.medium)) },
            expandedContent = { S2PanelSheet(onDismiss = {}) { Text("Panel", Modifier.padding(S2Spacing.medium)) } },
            handle = { S2SheetHandle(onClick = {}) },
        ) {
            Text("Content", Modifier.weight(1f).padding(S2Spacing.medium))
        }
    }
}
