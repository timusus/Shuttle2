package com.simplecityapps.shuttle.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.simplecityapps.shuttle.designsystem.preview.S2Preview
import com.simplecityapps.shuttle.designsystem.theme.S2Spacing
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

/** The width of one band's touch target; the screen scrolls the bands sideways rather than squeezing them below it. */
val EqBandWidth = 48.dp
private val EqTrackThickness = 8.dp
private val EqThumbSize = DpSize(width = 32.dp, height = 8.dp)

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
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(S2Spacing.small)) {
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
            // The slider is as wide as its thumb, so the invisible box is what makes the touch target 48dp.
            thumb = {
                Box(Modifier.width(EqBandWidth), contentAlignment = Alignment.Center) {
                    SliderDefaults.Thumb(interactionSource = interactionSource, isVertical = true, enabled = enabled, thumbSize = EqThumbSize)
                }
            },
            track = { EqTrack(it, enabled) },
        )
        Text(frequency, style = MaterialTheme.typography.labelSmall, color = labelColor, maxLines = 1)
    }
}

/** A vertical track filled from 0 dB to the thumb: up for a boost, down for a cut, empty at 0 dB. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EqTrack(state: SliderState, enabled: Boolean) {
    val colors = SliderDefaults.colors()
    val activeColor = if (enabled) colors.activeTrackColor else colors.disabledActiveTrackColor
    val inactiveColor = if (enabled) colors.inactiveTrackColor else colors.disabledInactiveTrackColor
    val range = EqGainRange
    Canvas(Modifier.width(EqTrackThickness).fillMaxHeight()) {
        val radius = CornerRadius(size.width / 2f)
        drawRoundRect(inactiveColor, cornerRadius = radius)
        fun yOf(db: Float) = size.height * (1f - ((db - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f))
        val thumbY = yOf(state.value)
        val centreY = yOf(0f)
        val top = minOf(thumbY, centreY)
        val bottom = maxOf(thumbY, centreY)
        if (bottom > top) {
            drawRoundRect(activeColor, topLeft = Offset(0f, top), size = Size(size.width, bottom - top), cornerRadius = radius)
        }
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
