package com.simplecityapps.shuttle.designsystem.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
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
    showRemaining: Boolean = false,
    onToggleRemaining: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val dragging by interactionSource.collectIsDraggedAsState()
    val fraction = dragFraction ?: if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val state = remember { SliderState(fraction) }
    state.value = fraction
    val seekDescription = stringResource(R.string.ds_seek)
    val trackHeight by animateDpAsState(if (dragging) SeekTrackDraggingHeight else SeekTrackHeight, label = "seekTrack")
    Column(modifier) {
        Slider(
            state = state,
            onValueChange = { dragFraction = it },
            modifier = Modifier.semantics { contentDescription = seekDescription },
            enabled = enabled,
            onValueChangeFinished = {
                dragFraction?.let { onSeek((it * durationMs).toLong()) }
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
                SliderDefaults.Track(sliderState = sliderState, modifier = Modifier.height(trackHeight), enabled = enabled)
            },
        )
        Row(Modifier.padding(horizontal = S2Spacing.xsmall), verticalAlignment = Alignment.CenterVertically) {
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

/** The end label while the duration is unknown or the stream is live. */
private const val UnknownDurationLabel = "--:--"

/** The track's thickness at rest, and while dragged. */
private val SeekTrackHeight = 16.dp
private val SeekTrackDraggingHeight = 24.dp

/** The thumb is a bar this wide, standing this much taller than the track. */
private val SeekThumbWidth = 4.dp
private val SeekThumbOverhang = 20.dp

/** The buffering indicator's stroke, in proportion to its small icon size. */
private val SeekBufferingStroke = 2.dp

@Preview
@Composable
private fun S2SeekBarPreview() {
    S2Preview {
        S2SeekBar(positionMs = 83_000, durationMs = 245_000, onSeek = {})
    }
}
