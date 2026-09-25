package com.simplecityapps.shuttle.designsystem.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.simplecityapps.shuttle.designsystem.R
import com.simplecityapps.shuttle.designsystem.preview.S2Preview

/**
 * The now-playing seek bar: an M3 `Slider` (thumb, drag, semantics) whose track is the
 * [S2PlaybackProgress] wave, wavy while [playing] and flat when paused or while dragging. The
 * elapsed and total times sit underneath; while dragging, the elapsed time follows the thumb in
 * `primary`. [onSeek] runs once, when the drag ends.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun S2SeekBar(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    playing: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val dragging by interactionSource.collectIsDraggedAsState()
    val fraction = dragFraction ?: if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val state = remember { SliderState(fraction) }
    state.value = fraction
    val seekDescription = stringResource(R.string.ds_seek)
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
            track = { sliderState ->
                S2PlaybackProgress(
                    progress = { sliderState.coercedValueAsFraction },
                    playing = playing && !dragging,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
        )
        Row(Modifier.padding(horizontal = 4.dp)) {
            Text(
                formatDuration(if (dragging) (fraction * durationMs).toLong() else positionMs),
                style = MaterialTheme.typography.labelMedium,
                color = if (dragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(formatDuration(durationMs), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** "m:ss", or "h:mm:ss" from an hour: how the seek bar and queue rows show a time. */
fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = totalSeconds % 3600 / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

@Preview
@Composable
private fun S2SeekBarPreview() {
    S2Preview {
        S2SeekBar(positionMs = 83_000, durationMs = 245_000, onSeek = {}, playing = true)
    }
}
