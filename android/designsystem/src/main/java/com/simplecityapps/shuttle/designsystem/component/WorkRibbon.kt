package com.simplecityapps.shuttle.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.simplecityapps.shuttle.designsystem.preview.S2Preview
import com.simplecityapps.shuttle.designsystem.theme.S2Spacing

/** The narrowest segment a tap jumps to; a tap on a thinner one opens the movement list instead. */
private val MinJumpWidth = 24.dp
private val BarHeight = 4.dp
private val TickHeight = 8.dp
private val SegmentGap = 4.dp

/**
 * A thin unlabelled bar over a multi-movement work: one segment per entry of [segments], sized by duration, the
 * [currentSegment] filling with [currentFill] and earlier ones full. With [continuous] (many movements) the segments
 * merge into one bar with tick marks above at the boundaries. A tap on a segment at least 24 dp wide calls
 * [onSegmentClick]; a tap on a thinner one, or anywhere on a continuous bar too fine to aim at, calls [onOpenList].
 * [caption] sits under the bar and also calls [onOpenList]. [segmentLabel] and [openListLabel] name those targets for accessibility.
 */
@Composable
fun S2WorkRibbon(
    segments: List<Long>,
    currentSegment: Int,
    currentFill: () -> Float,
    caption: String,
    onSegmentClick: (Int) -> Unit,
    onOpenList: () -> Unit,
    segmentLabel: (Int) -> String,
    openListLabel: String,
    modifier: Modifier = Modifier,
    continuous: Boolean = false,
) {
    val total = segments.sum().coerceAtLeast(1L).toFloat()
    Column(modifier = modifier) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val barWidth = maxWidth
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(if (continuous) TickHeight + BarHeight else BarHeight),
            ) {
                if (continuous) {
                    Bar(segments, currentSegment, currentFill, total, gap = 0.dp, Modifier.align(Alignment.BottomStart))
                    Ticks(segments, total, barWidth)
                } else {
                    Bar(segments, currentSegment, currentFill, total, gap = SegmentGap, Modifier.align(Alignment.BottomStart))
                }
                // The caption already speaks for the bar, so the bar itself is only its tap targets.
                if (continuous) {
                    Box(Modifier.fillMaxSize().tapTarget(openListLabel, onOpenList))
                } else {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(SegmentGap)) {
                        segments.forEachIndexed { i, length ->
                            val fraction = (length / total).coerceAtLeast(0.001f)
                            val target = if (barWidth * fraction >= MinJumpWidth) Modifier.tapTarget(segmentLabel(i)) { onSegmentClick(i) } else Modifier.tapTarget(openListLabel, onOpenList)
                            Box(Modifier.weight(fraction).fillMaxHeight().then(target))
                        }
                    }
                }
            }
        }
        S2Text(
            text = caption,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            // Hugs the bar so the two read as one unit, apart from the seek bar below.
            modifier = Modifier
                .clickable(onClickLabel = openListLabel, role = Role.Button, onClick = onOpenList)
                .padding(vertical = S2Spacing.small),
        )
    }
}

@Composable
private fun Modifier.tapTarget(label: String, onClick: () -> Unit): Modifier = this
    .semantics { contentDescription = label }
    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClickLabel = label, role = Role.Button, onClick = onClick)

@Composable
private fun Bar(
    segments: List<Long>,
    currentSegment: Int,
    currentFill: () -> Float,
    total: Float,
    gap: Dp,
    modifier: Modifier,
) {
    // Quieter than the seek bar's solid primary: earlier movements and the track are muted, only the current fill is accent.
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val played = muted.copy(alpha = 0.6f)
    val track = muted.copy(alpha = 0.3f)
    val current = MaterialTheme.colorScheme.primary
    Row(modifier.fillMaxWidth().height(BarHeight).clip(CircleShape), horizontalArrangement = Arrangement.spacedBy(gap)) {
        segments.forEachIndexed { i, length ->
            Box(
                Modifier
                    .weight((length / total).coerceAtLeast(0.001f))
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(if (i < currentSegment) played else track),
            ) {
                if (i == currentSegment) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth()
                            .graphicsLayer {
                                scaleX = currentFill().coerceIn(0f, 1f)
                                transformOrigin = TransformOrigin(0f, 0.5f)
                            }.background(current),
                    )
                }
            }
        }
    }
}

@Composable
private fun Ticks(
    segments: List<Long>,
    total: Float,
    width: Dp,
) {
    val colour = MaterialTheme.colorScheme.outlineVariant
    var start = 0L
    Box(Modifier.fillMaxWidth().height(TickHeight)) {
        segments.dropLast(1).forEach { length ->
            start += length
            Box(Modifier.offset(x = width * (start / total)).width(1.dp).height(TickHeight / 2).background(colour))
        }
    }
}

@Preview
@Composable
private fun S2WorkRibbonPreview() {
    S2Preview {
        Column(verticalArrangement = Arrangement.spacedBy(S2Spacing.medium), modifier = Modifier.padding(S2Spacing.medium)) {
            S2WorkRibbon(listOf(480_000, 560_000, 300_000, 420_000), 1, { 0.4f }, "II of IV · 24:10 left in work", {}, {}, { "Play movement ${it + 1}" }, "Show queue")
            S2WorkRibbon(List(30) { 120_000L }, 11, { 0.5f }, "12 of 30 · 58:00 left in work", {}, {}, { "Play movement ${it + 1}" }, "Show queue", continuous = true)
        }
    }
}
