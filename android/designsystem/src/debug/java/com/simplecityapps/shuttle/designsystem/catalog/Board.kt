package com.simplecityapps.shuttle.designsystem.catalog

import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.simplecityapps.shuttle.fixtures.SampleLibrary

/** The two widths every board records at; a board is as tall as its content. */
enum class BoardWidth(val widthDp: Int) {
    Compact(412),
    Expanded(1000),
}

/** A labelled group of states on a board. */
class BoardSection(val title: String, val content: @Composable () -> Unit)

/**
 * Lays [sections] out down one column at compact width, and across two at expanded, filling the
 * left column first.
 */
@Composable
fun Board(width: BoardWidth, sections: List<BoardSection>, modifier: Modifier = Modifier) {
    val columns = if (width == BoardWidth.Expanded) sections.chunked((sections.size + 1) / 2) else listOf(sections)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        columns.forEach { column ->
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                column.forEach { section ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(section.title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        section.content()
                    }
                }
            }
        }
    }
}

/** A small caption under a sample on a board. */
@Composable
fun Caption(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * An interaction source held in [interaction] (pressed, focused), so a static board can show the
 * state. The interaction goes out after the first frame, once the component is collecting.
 */
@Composable
fun rememberHeldInteraction(interaction: () -> Interaction): MutableInteractionSource {
    val source = remember { MutableInteractionSource() }
    LaunchedEffect(source) {
        withFrameNanos { }
        source.emit(interaction())
    }
    return source
}

@Composable
fun rememberPressed(): MutableInteractionSource = rememberHeldInteraction { PressInteraction.Press(Offset.Zero) }

@Composable
fun rememberFocused(): MutableInteractionSource = rememberHeldInteraction { FocusInteraction.Focus() }

/** Three sample covers per scheme column, picked so their colours sit with the column's seed. */
private fun CatalogScheme.sampleCovers(): List<String> = when (this) {
    CatalogScheme.Brand -> listOf("night-bus-frequencies", "phase-garden", "signal-room")
    CatalogScheme.Warm -> listOf("cassette-summer", "undertow", "lighthouse-ferry")
    CatalogScheme.Cool -> listOf("blue-hours", "loose-change", "lantern-hours")
    CatalogScheme.LowChroma, CatalogScheme.Dynamic -> listOf("estuary", "smoke-rings", "weather-systems")
}

/** Sample album art for a board with no particular album in mind: one of the scheme column's covers. */
@Composable
fun SampleArt(variant: Int = 0) {
    val covers = LocalCatalogScheme.current.sampleCovers()
    SampleArt(covers[variant % covers.size])
}

/** The generated cover of sample album [albumId] (see `SampleLibrary`), for a row that names that album. */
@Composable
fun SampleArt(albumId: String) {
    Image(SampleLibrary.cover(albumId), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
}
