package com.simplecityapps.shuttle.ui.common.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * Debug overlay that draws a Material 3 column grid with keylines.
 *
 * Compact: 4 columns, 16dp margins, 8dp gutters
 */
@Composable
fun MaterialGridOverlay(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current

    val columns = 4
    val margin = 16.dp
    val gutter = 8.dp

    val columnColor = Color(0x28E91E63)
    val keylineColor = Color(0xCCE91E63)
    val gutterColor = Color(0x70E91E63)
    val gridColor = Color(0x60E91E63)

    val dashEffect = PathEffect.dashPathEffect(
        floatArrayOf(
            with(density) { 4.dp.toPx() },
            with(density) { 4.dp.toPx() },
        ),
    )

    Box(modifier = modifier.fillMaxSize()) {
        content()

        Canvas(modifier = Modifier.fillMaxSize()) {
            val marginPx = with(density) { margin.toPx() }
            val gutterPx = with(density) { gutter.toPx() }
            val totalGutterWidth = gutterPx * (columns - 1)
            val availableWidth = size.width - (marginPx * 2) - totalGutterWidth
            val columnWidth = availableWidth / columns

            // Margin keylines
            drawLine(
                color = keylineColor,
                start = Offset(marginPx, 0f),
                end = Offset(marginPx, size.height),
                strokeWidth = with(density) { 1.dp.toPx() },
            )
            drawLine(
                color = keylineColor,
                start = Offset(size.width - marginPx, 0f),
                end = Offset(size.width - marginPx, size.height),
                strokeWidth = with(density) { 1.dp.toPx() },
            )

            // Columns and gutters
            var x = marginPx
            for (col in 0 until columns) {
                drawRect(
                    color = columnColor,
                    topLeft = Offset(x, 0f),
                    size = Size(columnWidth, size.height),
                )
                x += columnWidth
                if (col < columns - 1) {
                    val gutterCenter = x + gutterPx / 2
                    drawLine(
                        color = gutterColor,
                        start = Offset(gutterCenter, 0f),
                        end = Offset(gutterCenter, size.height),
                        strokeWidth = with(density) { 0.5.dp.toPx() },
                        pathEffect = dashEffect,
                    )
                    x += gutterPx
                }
            }
        }
    }
}
