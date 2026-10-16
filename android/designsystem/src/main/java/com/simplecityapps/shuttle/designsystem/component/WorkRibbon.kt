package com.simplecityapps.shuttle.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
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
private val SegmentGap = 2.dp

/**
 * A thin unlabelled bar over a multi-movement work: one segment per entry of [segments], sized by duration, the
 * [currentSegment] filling with [currentFill] and earlier ones full. With [continuous] (many movements) the segments
 * merge into one bar with tick marks above at the boundaries. A tap on a segment at least 24 dp wide calls
 * [onSegmentClick]; a tap on a thinner one, or anywhere on a continuous bar too fine to aim at, calls [onOpenList].
 * [caption] sits under the bar and is its accessibility description.
 */
@Composable
fun S2WorkRibbon(
    segments: List<Long>,
    currentSegment: Int,
    currentFill: () -> Float,
    caption: String,
    onSegmentClick: (Int) -> Unit,
    onOpenList: () -> Unit,
    modifier: Modifier = Modifier,
    continuous: Boolean = false,
) {
    val total = segments.sum().coerceAtLeast(1L).toFloat()
    val clickSegment = rememberUpdatedState(onSegmentClick)
    val openList = rememberUpdatedState(onOpenList)
    val density = LocalDensity.current
    Column(modifier = modifier.semantics { contentDescription = caption }) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val barWidth = maxWidth
            val widthPx = with(density) { barWidth.toPx() }
            val minJumpPx = with(density) { MinJumpWidth.toPx() }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(if (continuous) TickHeight + BarHeight else BarHeight)
                    .pointerInput(segments, widthPx) {
                        detectTapGestures { tap ->
                            var start = 0f
                            val hit = segments.indices.firstOrNull { i ->
                                val end = start + segments[i] / total * widthPx
                                (tap.x < end).also { if (!it) start = end }
                            } ?: segments.lastIndex
                            val width = segments[hit] / total * widthPx
                            if (width >= minJumpPx) clickSegment.value(hit) else openList.value()
                        }
                    },
            ) {
                if (continuous) {
                    Bar(segments, currentSegment, currentFill, total, gap = 0.dp, Modifier.align(Alignment.BottomStart))
                    Ticks(segments, total, barWidth)
                } else {
                    Bar(segments, currentSegment, currentFill, total, gap = SegmentGap, Modifier.align(Alignment.BottomStart))
                }
            }
        }
        S2Text(
            text = caption,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = S2Spacing.xsmall),
        )
    }
}

@Composable
private fun Bar(
    segments: List<Long>,
    currentSegment: Int,
    currentFill: () -> Float,
    total: Float,
    gap: Dp,
    modifier: Modifier,
) {
    val played = MaterialTheme.colorScheme.primary
    val unplayed = MaterialTheme.colorScheme.surfaceContainerHighest
    Row(modifier.fillMaxWidth().height(BarHeight).clip(CircleShape), horizontalArrangement = Arrangement.spacedBy(gap)) {
        segments.forEachIndexed { i, length ->
            Box(
                Modifier
                    .weight((length / total).coerceAtLeast(0.001f))
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(if (i < currentSegment) played else unplayed),
            ) {
                if (i == currentSegment) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth()
                            .graphicsLayer {
                                scaleX = currentFill().coerceIn(0f, 1f)
                                transformOrigin = TransformOrigin(0f, 0.5f)
                            }.background(played),
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
            S2WorkRibbon(listOf(480_000, 560_000, 300_000, 420_000), 1, { 0.4f }, "II of IV · 24:10 left in work", {}, {})
            S2WorkRibbon(List(30) { 120_000L }, 11, { 0.5f }, "12 of 30 · 58:00 left in work", {}, {}, continuous = true)
        }
    }
}
