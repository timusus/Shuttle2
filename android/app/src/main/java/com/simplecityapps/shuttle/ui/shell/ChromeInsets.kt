package com.simplecityapps.shuttle.ui.shell

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.MutableIntState
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * The px the shell's chrome covers at the [start] edge (the rail) and the [end] edge (the player pane), read as each frame
 * is laid out. Side insets that exclude it pad only what the chrome leaves bare, so the padding moves with the chrome as it
 * slides in or out rather than switching at once.
 */
internal class ChromeInsets(
    private val start: () -> Int = { 0 },
    private val end: () -> Int = { 0 },
) : WindowInsets {
    override fun getLeft(
        density: Density,
        layoutDirection: LayoutDirection,
    ): Int = if (layoutDirection == LayoutDirection.Ltr) start() else end()

    override fun getTop(density: Density): Int = 0

    override fun getRight(
        density: Density,
        layoutDirection: LayoutDirection,
    ): Int = if (layoutDirection == LayoutDirection.Ltr) end() else start()

    override fun getBottom(density: Density): Int = 0
}

/** Records the width this lays out at, each frame, before siblings measured after it read it through [ChromeInsets]. */
internal fun Modifier.recordWidth(width: MutableIntState): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    width.intValue = placeable.width
    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
}
