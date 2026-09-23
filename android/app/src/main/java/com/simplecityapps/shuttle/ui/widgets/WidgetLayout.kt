package com.simplecityapps.shuttle.ui.widgets

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max

enum class WidgetButton {
    Shuffle,
    Previous,
    PlayPause,
    Next,
    Repeat
}

/**
 * How the now playing widget arranges itself at a given size. Every layout insets its content by the same
 * padding on all four sides, and wherever the artwork sits against an edge it fills the space up to that
 * padding, so the gap to the top, side and bottom edges is always the same.
 *
 * - [Row]: one short row: art filling the height, then text, then buttons.
 * - [Card]: art filling the height on the left; text above the buttons on the right. One row tall and wide
 *   enough for every button beside the art, it's [WidgetLayout.compact].
 * - [Split]: two rows: art in the top left filling the height above a full-width button row, text beside it.
 * - [Hero]: three rows or more, or two rows too narrow for [Split]: the art fills the whole widget to its
 *   rounded edges, with the text and buttons along its bottom on a scrim, inset by the padding.
 */
enum class WidgetMode {
    Row,
    Card,
    Split,
    Hero
}

data class WidgetLayout(
    val mode: WidgetMode,
    /** The inset on every side of the widget, and the gap between the artwork and the text. */
    val padding: Dp,
    /** The artwork's size, or [DpSize.Unspecified] when there's no room for art. Square except in [WidgetMode.Hero]. */
    val art: DpSize,
    /**
     * Lines of track text: title and artist, plus the album when it's 3. 1 runs the title and artist together
     * on one line, for a [compact] card too short for two. 0 when only the buttons fit.
     */
    val textLines: Int,
    /** How many lines the title may wrap to. */
    val titleLines: Int = 1,
    val largeText: Boolean = false,
    val buttons: List<WidgetButton>,
    /**
     * A [WidgetMode.Card] one row tall: the play button's circle shrinks to [WidgetDimens.compactPlay] inside its
     * 48dp target, and the button row reaches into the bottom padding by the difference, so the circle still
     * stops at the padding, level with the art, and the text above gets the room it needs.
     */
    val compact: Boolean = false
) {
    val showArt: Boolean get() = art != DpSize.Unspecified

    /** The container's inset at the bottom: [padding], less what a [compact] button row reaches into it. */
    val bottomPadding: Dp get() = if (compact) padding - WidgetDimens.compactPlaySlack else padding
}

object WidgetDimens {
    val buttonSize = 48.dp
    val gap = 8.dp

    /** The one padding token, used on every side of every layout. */
    val padding = 12.dp

    /** The padding on launchers whose single row is too short for [padding] around a 48dp button. */
    val minPadding = 8.dp
    val minArt = 48.dp

    /** The narrowest text in a [WidgetMode.Row], including the [gap] between it and the buttons. */
    val minRowText = 64.dp
    val minColumnText = 96.dp

    /** The narrowest text column beside the artwork in [WidgetMode.Split]; narrower widgets use [WidgetMode.Hero]. */
    val minSplitText = 112.dp

    /** From this text width, [WidgetMode.Split] has room for the album. */
    val splitAlbumText = 120.dp
    val titleLineHeight = 20.dp
    val subtitleLineHeight = 18.dp
    val minInnerRadius = 6.dp

    /** The fade from clear into the scrim above text over artwork. */
    val scrimFade = 32.dp

    /** From this height, text stacked over buttons fits beside the art. */
    val cardMinHeight = padding * 2 + titleLineHeight + subtitleLineHeight + buttonSize

    /** The play button's circle in a [WidgetLayout.compact] card. Its touch target stays [buttonSize]. */
    val compactPlay = 40.dp

    /** How far a [WidgetLayout.compact] card's button row reaches into the bottom padding: the play circle's slack. */
    val compactPlaySlack = (buttonSize - compactPlay) / 2

    /**
     * From this height, a one-row widget wide enough for every button beside the art stacks text over them as
     * a [WidgetLayout.compact] card: the top padding, one line of title and artist, the 48dp button row, and
     * the bottom padding less the play circle's slack. 12 + 20 + 48 + 8 = 88dp, so a 4x1 launcher row of
     * 96-100dp gets it. The artist gets its own line once there's another [subtitleLineHeight] to spare.
     */
    val compactCardMinHeight = padding + titleLineHeight + buttonSize + padding - compactPlaySlack

    /** From this height, the art sits above a full-width button row. */
    val splitMinHeight = 160.dp

    /** From this height (about three launcher rows), the art fills the widget. */
    val heroMinHeight = 250.dp
}

/**
 * The largest artwork edge any layout draws on a phone, so one saved file stays sharp at every size: a
 * four-by-four [WidgetMode.Hero] fills the widget, about 388 by 430dp, cropped from the square file.
 */
val maxWidgetArtSize: Dp = 440.dp

/**
 * Button sets in the order they're given up as the widget narrows: shuffle and repeat go first, then previous.
 * Play/pause and next always remain.
 */
private val buttonSets =
    listOf(
        listOf(WidgetButton.Shuffle, WidgetButton.Previous, WidgetButton.PlayPause, WidgetButton.Next, WidgetButton.Repeat),
        listOf(WidgetButton.Previous, WidgetButton.PlayPause, WidgetButton.Next),
        listOf(WidgetButton.PlayPause, WidgetButton.Next)
    )

private fun List<WidgetButton>.width(): Dp = WidgetDimens.buttonSize * size

/** The largest button set that fits in [width]. Never fewer than play/pause and next. */
fun buttonsFor(width: Dp): List<WidgetButton> = buttonSets.firstOrNull { it.width() <= width } ?: buttonSets.last()

/**
 * The padding for a widget [height]: [WidgetDimens.padding], unless the launcher's rows are too short to fit
 * that around a button, when it shrinks towards [WidgetDimens.minPadding]. Either way it's the same on all sides.
 */
fun widgetPadding(height: Dp): Dp = ((height - WidgetDimens.buttonSize) / 2).coerceIn(WidgetDimens.minPadding, WidgetDimens.padding)

/**
 * The artwork's corner radius, concentric with the widget's: the widget's radius less the padding between
 * them, so the two curves stay parallel. Clamped so small launcher radii don't square the art off.
 */
fun innerCornerRadius(
    outerRadius: Dp,
    padding: Dp
): Dp = max(outerRadius - padding, WidgetDimens.minInnerRadius)

/** Maps the widget background opacity setting, a percentage, to an alpha. */
fun widgetBackgroundAlpha(opacityPercent: Int): Float = opacityPercent.coerceIn(0, 100) / 100f

fun widgetLayoutFor(size: DpSize): WidgetLayout = when {
    size.height < WidgetDimens.cardMinHeight -> rowLayout(size)
    size.height < WidgetDimens.splitMinHeight -> cardLayout(size)
    size.height < WidgetDimens.heroMinHeight -> splitLayout(size) ?: heroLayout(size)
    else -> heroLayout(size)
}

private fun rowLayout(size: DpSize): WidgetLayout {
    compactCardLayout(size)?.let { return it }
    val padding = widgetPadding(size.height)
    val inner = size.width - padding * 2
    val art = max(size.height - padding * 2, 0.dp)
    val fewestButtons = buttonSets.last().width()
    // Art first, then text, then as many buttons as the rest allows. Play/pause and next always stay.
    val showArt = art >= WidgetDimens.minArt / 2 && inner - art - padding - WidgetDimens.minRowText >= fewestButtons
    val showText = showArt || inner - WidgetDimens.minRowText >= fewestButtons
    val textSpace = if (showArt) art + padding + WidgetDimens.minRowText else WidgetDimens.minRowText
    return WidgetLayout(
        mode = WidgetMode.Row,
        padding = padding,
        art = if (showArt) DpSize(art, art) else DpSize.Unspecified,
        textLines = if (showText) 2 else 0,
        buttons = if (showText) buttonsFor(inner - textSpace) else buttonsFor(inner)
    )
}

private fun cardLayout(size: DpSize): WidgetLayout {
    val padding = WidgetDimens.padding
    val inner = size.width - padding * 2
    // The art always fills the height, so it meets the top and bottom padding; if the column beside it would
    // be too narrow, the art goes rather than shrinking away from the edges.
    val art = size.height - padding * 2
    val column = inner - art - padding
    val showArt = column >= WidgetDimens.minColumnText
    return WidgetLayout(
        mode = WidgetMode.Card,
        padding = padding,
        art = if (showArt) DpSize(art, art) else DpSize.Unspecified,
        textLines = 2,
        buttons = buttonsFor(if (showArt) column else inner)
    )
}

/**
 * The card's arrangement in one row, when the column beside the art has room for every button: shuffle and
 * repeat stay rather than the row dropping to three buttons. Null when it's too short or too narrow.
 */
private fun compactCardLayout(size: DpSize): WidgetLayout? {
    if (size.height < WidgetDimens.compactCardMinHeight) return null
    if (size.width < compactCardMinWidth(size.height)) return null
    val padding = WidgetDimens.padding
    val art = size.height - padding * 2
    // What's left above the button row, between the top padding and the button row's reduced bottom padding.
    val text = size.height - padding * 2 + WidgetDimens.compactPlaySlack - WidgetDimens.buttonSize
    return WidgetLayout(
        mode = WidgetMode.Card,
        padding = padding,
        art = DpSize(art, art),
        textLines = if (text >= WidgetDimens.titleLineHeight + WidgetDimens.subtitleLineHeight) 2 else 1,
        buttons = buttonSets.first(),
        compact = true
    )
}

/** The narrowest widget of [height] that a [WidgetLayout.compact] card fits: the art, then every button beside it. */
fun compactCardMinWidth(height: Dp): Dp = WidgetDimens.padding * 3 + (height - WidgetDimens.padding * 2) + buttonSets.first().width()

private fun splitLayout(size: DpSize): WidgetLayout? {
    val padding = WidgetDimens.padding
    val inner = size.width - padding * 2
    val art = size.height - padding * 2 - WidgetDimens.buttonSize - WidgetDimens.gap
    val text = inner - art - padding
    if (text < WidgetDimens.minSplitText) return null
    return WidgetLayout(
        mode = WidgetMode.Split,
        padding = padding,
        art = DpSize(art, art),
        textLines = if (text >= WidgetDimens.splitAlbumText) 3 else 2,
        titleLines = 2,
        largeText = true,
        buttons = buttonsFor(inner)
    )
}

private fun heroLayout(size: DpSize): WidgetLayout {
    val padding = WidgetDimens.padding
    return WidgetLayout(
        mode = WidgetMode.Hero,
        padding = padding,
        art = size,
        textLines = 2,
        largeText = true,
        buttons = buttonsFor(size.width - padding * 2)
    )
}
