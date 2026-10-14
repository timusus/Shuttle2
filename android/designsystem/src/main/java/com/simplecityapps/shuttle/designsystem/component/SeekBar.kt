package com.simplecityapps.shuttle.designsystem.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.simplecityapps.shuttle.designsystem.R
import com.simplecityapps.shuttle.designsystem.preview.S2Preview
import com.simplecityapps.shuttle.designsystem.theme.S2IconSize
import com.simplecityapps.shuttle.designsystem.theme.S2Spacing
import com.simplecityapps.shuttle.designsystem.theme.time
import com.simplecityapps.shuttle.format.formatDuration

/**
 * The now-playing seek bar: an M3 Expressive `Slider` with a thick track, which grows thicker, its
 * thumb with it, while it's dragged. The elapsed and total times sit underneath; while dragging, the
 * elapsed time follows the thumb in `primary`. [onSeek] runs once, when the drag ends. While
 * [buffering], a small loading indicator and "Buffering" sit between the times; the bar stays usable.
 * A stream's [bufferedMs] shows as a faint tonal stretch of the track ahead of the thumb, how far it can be
 * sought without stalling; 0, or the whole duration, draws nothing.
 * With [showRemaining] the end label counts down the time left ("-3:12"); [onToggleRemaining], when
 * given, makes it tappable to switch between that and the total.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun S2SeekBar(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    buffering: Boolean = false,
    bufferedMs: Long = 0,
    showRemaining: Boolean = false,
    onToggleRemaining: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val dragging by interactionSource.collectIsDraggedAsState()
    val fraction = dragFraction ?: if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val state = remember { SliderState(fraction) }
    state.value = fraction
    val haptics = LocalHapticFeedback.current
    val seekDescription = stringResource(R.string.ds_seek)
    val trackHeight by animateDpAsState(if (dragging) SeekTrackDraggingHeight else SeekTrackHeight, label = "seekTrack")
    Column(modifier) {
        Slider(
            state = state,
            onValueChange = {
                if (dragFraction == null) haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                dragFraction = it
            },
            modifier = Modifier.semantics { contentDescription = seekDescription },
            enabled = enabled,
            onValueChangeFinished = {
                dragFraction?.let { onSeek((it * durationMs).toLong()) }
                if (dragFraction != null) haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
                dragFraction = null
            },
            interactionSource = interactionSource,
            thumb = {
                SliderDefaults.Thumb(
                    interactionSource = interactionSource,
                    enabled = enabled,
                    thumbSize = DpSize(SeekThumbWidth, trackHeight + SeekThumbOverhang),
                )
            },
            track = { sliderState ->
                val colors = SliderDefaults.colors()
                val activeColor = if (enabled) colors.activeTrackColor else colors.disabledActiveTrackColor
                val inactiveColor = if (enabled) colors.inactiveTrackColor else colors.disabledInactiveTrackColor
                val bufferedColor = MaterialTheme.colorScheme.primary.copy(alpha = SeekBufferedAlpha)
                // One clipped pill painted in stretches, so the track stays whole at any progress (the stock track
                // splits into rounded pieces with a stop dot).
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(trackHeight)
                        .clip(CircleShape)
                        .drawBehind {
                            val rtl = layoutDirection == LayoutDirection.Rtl
                            fun stretch(from: Float, to: Float, color: Color) {
                                val left = if (rtl) size.width * (1 - to) else size.width * from
                                drawRect(color, Offset(left, 0f), Size(size.width * (to - from), size.height))
                            }
                            val played = sliderState.value.coerceIn(0f, 1f)
                            stretch(0f, 1f, inactiveColor)
                            // Nothing is drawn for a local file, or once the thumb has caught up with what has loaded.
                            val bufferedFraction = if (durationMs > 0) (bufferedMs.toFloat() / durationMs).coerceIn(played, 1f) else 0f
                            if (enabled && bufferedMs in 1 until durationMs) stretch(played, bufferedFraction, bufferedColor)
                            stretch(0f, played, activeColor)
                        },
                )
            },
        )
        Row(
            Modifier.padding(horizontal = S2Spacing.xsmall).pullUp(SeekTimesPullUp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                formatDuration(if (dragging) (fraction * durationMs).toLong() else positionMs),
                style = MaterialTheme.typography.time,
                color = if (dragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            if (buffering) {
                Row(horizontalArrangement = Arrangement.spacedBy(S2Spacing.xsmall), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(S2IconSize.small),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        strokeWidth = SeekBufferingStroke,
                    )
                    Text(stringResource(R.string.ds_buffering), style = MaterialTheme.typography.time, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.weight(1f))
            }
            val shownMs = if (dragging) (fraction * durationMs).toLong() else positionMs
            Text(
                text = when {
                    durationMs <= 0 -> UnknownDurationLabel
                    showRemaining -> "-" + formatDuration((durationMs - shownMs).coerceAtLeast(0L))
                    else -> formatDuration(durationMs)
                },
                style = MaterialTheme.typography.time,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = if (onToggleRemaining != null && durationMs > 0) {
                    Modifier
                        .minimumInteractiveComponentSize()
                        .clickable(onClickLabel = stringResource(if (showRemaining) R.string.ds_show_total_time else R.string.ds_show_remaining_time), role = Role.Button, onClick = onToggleRemaining)
                } else {
                    Modifier
                },
            )
        }
    }
}

/**
 * The slider and the tappable end label each pad their content out to a 48dp touch target, which leaves the
 * times about 32dp under the track. Pulling the row up by this much leaves one [S2Spacing.small] step.
 */
private val SeekTimesPullUp = S2Spacing.medium + S2Spacing.small

private fun Modifier.pullUp(by: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val shift = by.roundToPx().coerceAtMost(placeable.height)
    layout(placeable.width, placeable.height - shift) { placeable.place(0, -shift) }
}

/** The end label while the duration is unknown or the stream is live. */
private const val UnknownDurationLabel = "--:--"

/** The track's thickness at rest, and while dragged. */
private val SeekTrackHeight = 16.dp
private val SeekTrackDraggingHeight = 24.dp

/** The thumb is a bar this wide, standing this much taller than the track. */
private val SeekThumbWidth = 4.dp
private val SeekThumbOverhang = 20.dp

/** The loaded stretch is the primary colour at this opacity, starting this far past the thumb's gap. */
private const val SeekBufferedAlpha = 0.24f

/** The buffering indicator's stroke, in proportion to its small icon size. */
private val SeekBufferingStroke = 2.dp

@Preview
@Composable
private fun S2SeekBarPreview() {
    S2Preview {
        S2SeekBar(positionMs = 83_000, durationMs = 245_000, onSeek = {})
    }
}
