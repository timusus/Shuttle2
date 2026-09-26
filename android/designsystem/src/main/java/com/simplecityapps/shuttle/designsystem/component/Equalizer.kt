package com.simplecityapps.shuttle.designsystem.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalSlider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.simplecityapps.shuttle.designsystem.preview.S2Preview
import kotlin.math.abs
import kotlin.math.roundToInt

/** The gain range of an EQ band, in dB. */
val EqGainRange = -12f..12f

/** "+3", "0", "−4.5": whole numbers without a decimal, a true minus sign for cuts, no unit (the screen states it once). */
internal fun formatGain(db: Float): String {
    val tenths = (db * 10).roundToInt()
    val magnitude = abs(tenths).let { if (it % 10 == 0) "${it / 10}" else "${it / 10}.${it % 10}" }
    val sign = when {
        tenths > 0 -> "+"
        tenths < 0 -> "−"
        else -> ""
    }
    return "$sign$magnitude"
}

// Slim enough that eleven sliders (the preamp and ten bands) sit apart at a 360dp phone width.
private val EqTrackThickness = 8.dp
private val EqThumbSize = DpSize(width = 20.dp, height = 4.dp)

/**
 * One equalizer band: the gain above a slim `VerticalSlider` (boost up, cut down) and the [frequency]
 * label below. [contentDescription] names the slider for accessibility and tests (the frequency
 * label by default). [enabled] false greys it out while the equalizer is off.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EqBand(
    frequency: String,
    gainDb: Float,
    onGainChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onGainChangeFinished: (() -> Unit)? = null,
    contentDescription: String = frequency,
) {
    val state = remember { SliderState(gainDb, trackRange = EqGainRange) }
    state.value = gainDb
    val interactionSource = remember { MutableInteractionSource() }
    val labelColor = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(formatGain(gainDb), style = MaterialTheme.typography.labelSmall, color = labelColor, maxLines = 1)
        VerticalSlider(
            state = state,
            onValueChange = onGainChange,
            modifier = Modifier
                .height(200.dp)
                .semantics { this.contentDescription = contentDescription },
            enabled = enabled,
            onValueChangeFinished = onGainChangeFinished,
            topToBottom = false,
            interactionSource = interactionSource,
            thumb = { SliderDefaults.Thumb(interactionSource = interactionSource, isVertical = true, enabled = enabled, thumbSize = EqThumbSize) },
            track = { SliderDefaults.Track(sliderState = it, modifier = Modifier.width(EqTrackThickness), enabled = enabled) },
        )
        Text(frequency, style = MaterialTheme.typography.labelSmall, color = labelColor, maxLines = 1)
    }
}

@Preview
@Composable
private fun EqualizerPreview() {
    S2Preview {
        val gains = listOf(4f, 2f, 0f, -2f, 3f)
        Row(Modifier.fillMaxWidth()) {
            listOf("60 Hz", "230 Hz", "910 Hz", "3.6 kHz", "14 kHz").forEachIndexed { i, frequency ->
                EqBand(frequency, gains[i], {}, Modifier.weight(1f))
            }
        }
    }
}
