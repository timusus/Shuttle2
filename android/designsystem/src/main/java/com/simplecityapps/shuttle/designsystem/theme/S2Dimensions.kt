package com.simplecityapps.shuttle.designsystem.theme

import androidx.compose.ui.unit.dp

/**
 * Icon glyph sizes, after the iOS app's `IconSize` (ios/S2/Theme/Spacing.swift). An icon inside an M3 component
 * (icon button, button, list item, navigation) takes that component's default size instead.
 */
object S2IconSize {
    /** A glyph inline with text: a row's downloaded mark, a tile's kind, a selection check. */
    val small = 16.dp

    /** The standard M3 icon: a bare icon beside a row or setting, a glyph in a 40 dp tonal container. */
    val medium = 24.dp

    /** An empty or error state's glyph. */
    val hero = 40.dp

    /** The tonal rounded-square a settings row sets its leading icon in. */
    val container = 40.dp
}

/** Hit areas, after the iOS app's `TouchTarget`. Every tappable thing is at least [minimum] in both directions. */
object S2TouchTarget {
    /** The M3 minimum interactive size: a drag handle, a section header row, a dialog option. */
    val minimum = 48.dp
}

/** Width limits for content, after the iOS app's `AdaptiveLayout`. Screens read these rather than their own constants. */
object S2ContentWidth {
    /** A centred block of prose, such as an empty state's message, never grows wider than this. */
    val readable = 360.dp

    /** A dialog's minimum width (M3). */
    val dialogMinimum = 280.dp

    /** A dialog's maximum width (M3). */
    val dialogMaximum = 560.dp

    /** Scrolling content in a single pane (lists, details) never grows wider than this; centre it beyond. */
    val maximum = 1000.dp
}

/** A tile's width on a horizontal shelf, so a compact phone shows two and a peek of the third; wider windows take [wide]. */
object S2ShelfTileWidth {
    val compact = 140.dp

    val wide = 170.dp
}

/** The narrowest a catalogue grid tile gets; a grid fits as many columns of at least this width as the window allows. */
object S2GridTile {
    val minimumWidth = 140.dp
}
