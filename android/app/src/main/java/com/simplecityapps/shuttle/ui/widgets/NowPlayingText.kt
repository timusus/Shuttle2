package com.simplecityapps.shuttle.ui.widgets

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

@Composable
internal fun TrackText(
    state: NowPlayingWidgetState,
    layout: WidgetLayout,
    modifier: GlanceModifier,
    colors: ContentColors = ContentColors.default()
) {
    // Beside art that fills the height the text centres on it; everywhere else it starts at the top.
    val alignment = if (layout.mode == WidgetMode.Row) Alignment.CenterVertically else Alignment.Top
    Column(modifier = modifier, verticalAlignment = alignment) {
        if (layout.textLines == 1) {
            // A compact card too short for two lines runs the artist on after the title.
            val text = listOf(state.title, state.artist).filter { it.isNotEmpty() }.joinToString(" · ")
            Text(text = text, maxLines = 1, style = titleStyle(layout.largeText, colors.title))
        } else {
            Text(text = state.title, maxLines = layout.titleLines, style = titleStyle(layout.largeText, colors.title))
            if (state.artist.isNotEmpty()) {
                Text(text = state.artist, maxLines = 1, style = subtitleStyle(layout.largeText, colors.subtitle))
            }
        }
        if (layout.textLines >= 3 && state.album.isNotEmpty()) {
            Text(text = state.album, maxLines = 1, style = subtitleStyle(layout.largeText, colors.subtitle))
        }
    }
}

internal fun titleStyle(
    large: Boolean,
    color: ColorProvider
) = TextStyle(
    color = color,
    fontSize = if (large) 16.sp else 14.sp,
    fontWeight = FontWeight.Medium
)

internal fun subtitleStyle(
    large: Boolean,
    color: ColorProvider
) = TextStyle(
    color = color,
    fontSize = if (large) 14.sp else 12.sp
)
