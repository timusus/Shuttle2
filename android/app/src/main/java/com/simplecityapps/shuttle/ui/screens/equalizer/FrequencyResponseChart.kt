package com.simplecityapps.shuttle.ui.screens.equalizer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.log10
import kotlinx.collections.immutable.ImmutableList

/** One point of an equalizer's frequency-response curve. */
data class FrequencyResponsePoint(val frequencyHz: Float, val gainDb: Float)

private const val MIN_DB = -20f
private const val MAX_DB = 20f

/** The plotted frequency range, shared with [com.simplecityapps.shuttle.ui.screens.settings.equalizer.ComputeFrequencyResponse]. */
internal const val MIN_FREQUENCY_HZ = 20f
internal const val MAX_FREQUENCY_HZ = 20_500f

private val DB_GRIDLINES = listOf(-20f, -10f, 0f, 10f, 20f)
private val FREQUENCY_TICKS_HZ = listOf(20f, 50f, 100f, 200f, 500f, 1_000f, 2_000f, 5_000f, 10_000f, 20_000f)

// A dialog-width chart can't fit a label at every gridline without them overlapping, so only
// these three (spread evenly across the log scale) get text; the rest still draw as unlabelled
// gridlines.
private val LABELED_FREQUENCY_TICKS_HZ = listOf(20f, 1_000f, 20_000f)

private fun xFraction(frequencyHz: Float): Float = ((log10(frequencyHz) - log10(MIN_FREQUENCY_HZ)) / (log10(MAX_FREQUENCY_HZ) - log10(MIN_FREQUENCY_HZ))).coerceIn(0f, 1f)

private fun yFraction(gainDb: Float): Float = ((MAX_DB - gainDb) / (MAX_DB - MIN_DB)).coerceIn(0f, 1f)

private fun frequencyLabel(hz: Float): String = if (hz >= 1000f) "%.0f kHz".format(hz / 1000f) else "%.0f Hz".format(hz)

/**
 * Draws an equalizer frequency-response curve: log-frequency x axis, dB y axis, gridlines at
 * standard EQ ticks. Axis labels are real [Text] composables (not baked into the canvas) so they
 * stay themed, accessible and assertable in Compose UI tests; the curve and gridlines are drawn on
 * a [Canvas] beneath them, positioned from the same fraction math so everything lines up. The
 * frequency-axis labels live in their own row below the plot, sized to their own content, so they
 * never collide with or get clipped by the dB-axis labels overlaid on the plot itself.
 */
@Composable
fun FrequencyResponseChart(
    points: ImmutableList<FrequencyResponsePoint>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val lineColor = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val visibleTicks = FREQUENCY_TICKS_HZ.filter { hz -> hz in MIN_FREQUENCY_HZ..MAX_FREQUENCY_HZ }

    Column(modifier.testTag("frequencyResponseChart")) {
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            Canvas(Modifier.fillMaxSize()) {
                DB_GRIDLINES.forEach { db ->
                    val y = yFraction(db) * size.height
                    drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                }
                visibleTicks.forEach { hz ->
                    val x = xFraction(hz) * size.width
                    drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
                }

                if (points.size >= 2) {
                    val path = Path()
                    points.forEachIndexed { index, point ->
                        val x = xFraction(point.frequencyHz) * size.width
                        val y = yFraction(point.gainDb.coerceIn(MIN_DB, MAX_DB)) * size.height
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path, color = lineColor, style = Stroke(width = 2.dp.toPx()))
                }
            }

            DB_GRIDLINES.forEach { db ->
                Text(
                    text = "%.0f dB".format(db),
                    color = labelColor,
                    fontSize = 10.sp,
                    modifier = Modifier.offset(x = 2.dp, y = ((maxHeight * yFraction(db)) - 8.dp).coerceIn(0.dp, maxHeight - 14.dp)),
                )
            }
        }

        BoxWithConstraints(Modifier.fillMaxWidth()) {
            visibleTicks.filter { hz -> hz in LABELED_FREQUENCY_TICKS_HZ }.forEach { hz ->
                Text(
                    text = frequencyLabel(hz),
                    color = labelColor,
                    fontSize = 10.sp,
                    modifier = Modifier.offset(x = ((maxWidth * xFraction(hz)) - 14.dp).coerceIn(0.dp, maxWidth - 40.dp)),
                )
            }
        }
    }
}
