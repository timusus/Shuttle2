package com.simplecityapps.shuttle.ui.widgets

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min

enum class WidgetButton {
    Shuffle,
    Previous,
    PlayPause,
    Next,
    Repeat
}

/**
 * How the now playing widget arranges itself at a given size.
 *
 * - [Row]: a single line, used when the widget is one short row: art, text, buttons.
 * - [Card]: art on the left filling the height, text above the buttons on the right.
 * - [Large]: art beside three lines of text, with the button row along the bottom.
 * - [Tile]: tall but narrow: art on top, text beneath, then the buttons.
 */
enum class WidgetMode {
    Row,
    Card,
    Large,
    Tile
}

data class WidgetLayout(
    val mode: WidgetMode,
    val padding: Dp,
    /** Edge length of the square artwork, or [Dp.Unspecified] when there's no room for art. */
    val artSize: Dp,
    /** Lines of track text; 0 when only the buttons fit. */
    val textLines: Int,
    val buttons: List<WidgetButton>
) {
    val showArt: Boolean get() = artSize != Dp.Unspecified
}

object WidgetDimens {
    val buttonSize = 48.dp
    val iconSize = 24.dp
    val gap = 8.dp
    val compactPadding = 8.dp
    val padding = 12.dp
    val minArt = 48.dp
    val maxArt = 160.dp
    val minRowText = 64.dp
    val minColumnText = 96.dp

    /** The narrowest text column beside large art, so a 2x2 widget still shows its artwork. */
    val minLargeText = 80.dp
    val minLargeArt = 56.dp
    val titleLineHeight = 20.dp
    val subtitleLineHeight = 18.dp

    /** Below this height the widget is a single short row. */
    val cardMinHeight = 96.dp

    /** From this height the buttons get their own row along the bottom. */
    val largeMinHeight = 160.dp
}

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
 * The widget sizes we lay out for. Glance renders each one and the launcher shows the largest that fits,
 * so these are the thresholds where the layout changes: two cells wide up to full width, one row up to
 * roughly three rows tall. Measured on a Pixel 9 Pro's launcher, a cell is about 97dp wide and a row about
 * 106dp tall; launchers with denser grids come in lower, hence a single row starting at 64dp.
 */
val widgetBreakpoints: Set<DpSize> =
    listOf(120.dp, 180.dp, 250.dp, 340.dp)
        .flatMap { width -> listOf(64.dp, 96.dp, 170.dp, 250.dp).map { height -> DpSize(width, height) } }
        .toSet()

/** The largest artwork edge any breakpoint renders, so one bitmap stays sharp at every size. */
val maxWidgetArtSize: Dp = widgetBreakpoints.maxOf { widgetLayoutFor(it).artSize.takeIf { size -> size != Dp.Unspecified } ?: 0.dp }

fun widgetLayoutFor(size: DpSize): WidgetLayout = when {
    size.height < WidgetDimens.cardMinHeight -> rowLayout(size)
    size.height < WidgetDimens.largeMinHeight -> cardLayout(size)
    // Whichever of art beside the text or art above it draws the bigger picture.
    else -> listOfNotNull(largeLayout(size), tileLayout(size)).maxByOrNull { it.artSize.value } ?: cardLayout(size)
}

private fun rowLayout(size: DpSize): WidgetLayout {
    val padding = WidgetDimens.compactPadding
    val inner = size.width - padding * 2
    val art = max(size.height - padding * 2, 0.dp)
    val fewestButtons = buttonSets.last().width()
    // Art first, then text, then as many buttons as the rest allows. Play/pause and next always stay.
    val showArt = art >= WidgetDimens.minArt / 2 && inner - art - WidgetDimens.gap - WidgetDimens.minRowText >= fewestButtons
    val showText = showArt || inner - WidgetDimens.minRowText >= fewestButtons
    val textSpace = if (showArt) art + WidgetDimens.gap + WidgetDimens.minRowText else WidgetDimens.minRowText
    return WidgetLayout(
        mode = WidgetMode.Row,
        padding = padding,
        artSize = if (showArt) art else Dp.Unspecified,
        textLines = if (showText) 2 else 0,
        buttons = if (showText) buttonsFor(inner - textSpace) else buttonsFor(inner)
    )
}

private fun cardLayout(size: DpSize): WidgetLayout {
    val padding = if (size.height < WidgetDimens.largeMinHeight) WidgetDimens.compactPadding else WidgetDimens.padding
    val inner = size.width - padding * 2
    val maxArt = min(size.height - padding * 2, WidgetDimens.maxArt)
    // Prefer more buttons over a bigger picture, as long as the art stays a reasonable size; art beats buttons.
    val withArt =
        buttonSets.firstNotNullOfOrNull { buttons ->
            val column = max(buttons.width(), WidgetDimens.minColumnText)
            val art = min(maxArt, inner - WidgetDimens.gap - column)
            if (art >= WidgetDimens.minLargeArt) buttons to art else null
        }
    return WidgetLayout(
        mode = WidgetMode.Card,
        padding = padding,
        artSize = withArt?.second ?: Dp.Unspecified,
        textLines = if (size.height >= WidgetDimens.largeMinHeight) 3 else 2,
        buttons = withArt?.first ?: buttonsFor(inner)
    )
}

private fun largeLayout(size: DpSize): WidgetLayout? {
    val padding = WidgetDimens.padding
    val inner = size.width - padding * 2
    val art =
        minOf(
            size.height - padding * 2 - WidgetDimens.buttonSize - WidgetDimens.gap,
            inner - WidgetDimens.gap - WidgetDimens.minLargeText,
            WidgetDimens.maxArt
        )
    if (art < WidgetDimens.minLargeArt) return null
    val textWidth = inner - WidgetDimens.gap - art
    return WidgetLayout(
        mode = WidgetMode.Large,
        padding = padding,
        artSize = art,
        // The album only earns its line when there's room to read it.
        textLines = if (textWidth >= WidgetDimens.minColumnText + 24.dp) 3 else 2,
        buttons = buttonsFor(inner)
    )
}

private fun tileLayout(size: DpSize): WidgetLayout? {
    val padding = WidgetDimens.padding
    val inner = size.width - padding * 2
    val text = WidgetDimens.titleLineHeight + WidgetDimens.subtitleLineHeight
    val art =
        minOf(
            size.height - padding * 2 - text - WidgetDimens.buttonSize - WidgetDimens.gap * 2,
            inner,
            WidgetDimens.maxArt
        )
    if (art < 72.dp) return null
    return WidgetLayout(
        mode = WidgetMode.Tile,
        padding = padding,
        artSize = art,
        textLines = 2,
        buttons = buttonsFor(inner)
    )
}
