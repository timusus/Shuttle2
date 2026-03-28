package com.simplecityapps.shuttle.ui.common.components

import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Computed scroll state for positioning the fast-scroller thumb.
 */
data class ThumbScrollState(
    /** Thumb offset in pixels from the top of the track. */
    val thumbOffsetPx: Float,
    /** Flat item index under the thumb centre (for popup text). */
    val currentItemIndex: Int,
    /** Estimated total scroll range in pixels (for drag calculations). */
    val totalScrollRangePx: Float,
    /** Estimated row stride in pixels, including spacing. */
    val estimatedStride: Float,
    /** Number of items per row (1 for lists, column count for grids). */
    val itemsPerRow: Int,
)

/**
 * Computes the thumb position from the current scroll state.
 *
 * The algorithm:
 * 1. Groups visible items by offset to discover row boundaries and items-per-row.
 * 2. Computes **stride** (row height + spacing) from consecutive row offsets —
 *    this naturally includes `Arrangement.spacedBy` spacing, unlike summing item sizes.
 * 3. Uses the first visible item's viewport offset to determine the exact scroll
 *    position (no estimation or averaging of sizes needed).
 * 4. Maps scroll position to thumb offset via scroll fraction.
 */
fun computeThumbScrollState(
    state: FastScrollableState,
    viewportHeightPx: Float,
    thumbHeightPx: Int,
): ThumbScrollState {
    val visibleItems = state.visibleItems
    val totalItemsCount = state.totalItemsCount

    if (visibleItems.isEmpty() || totalItemsCount == 0) {
        return ThumbScrollState(0f, 0, 1f, 1f, 1)
    }

    // --- 1. Discover row structure from visible items ---
    // Group visible items by their main-axis offset → each group is one row.
    val rowsByOffset = visibleItems.groupBy { it.offset }
    val sortedRowOffsets = rowsByOffset.keys.sorted()
    val itemsPerRow = rowsByOffset.values.first().size.coerceAtLeast(1)
    val totalRows = ceil(totalItemsCount.toFloat() / itemsPerRow).toInt()

    // --- 2. Compute stride (row height + spacing) ---
    // Using offsets between consecutive visible rows is exact and stable,
    // because it naturally includes Arrangement spacing.
    val stride: Float = if (sortedRowOffsets.size >= 2) {
        val totalSpan = sortedRowOffsets.last() - sortedRowOffsets.first()
        totalSpan.toFloat() / (sortedRowOffsets.size - 1)
    } else {
        // Only one visible row — fall back to item height (no spacing info).
        visibleItems.first().mainAxisSize.toFloat()
    }

    // Average row height (for estimating the last row's contribution to content height).
    val avgRowHeight = rowsByOffset.values
        .map { row -> row.maxOf { it.mainAxisSize } }
        .average()
        .toFloat()

    // --- 3. Compute total content height and max scroll range ---
    val totalContentHeight = state.beforeContentPadding +
        (if (totalRows > 1) (totalRows - 1) * stride + avgRowHeight else avgRowHeight) +
        state.afterContentPadding
    val maxScroll = (totalContentHeight - viewportHeightPx).coerceAtLeast(1f)

    // --- 4. Compute current scroll position ---
    // The first visible item's expected content-coordinate position vs its actual
    // viewport-coordinate position tells us exactly how far we've scrolled.
    val firstItem = visibleItems.first()
    val firstItemRow = firstItem.index / itemsPerRow
    val expectedContentPosition = state.beforeContentPadding + firstItemRow * stride
    val currentScrollPx = (expectedContentPosition - firstItem.offset).coerceAtLeast(0f)

    // --- 5. Map to thumb position ---
    val scrollFraction = (currentScrollPx / maxScroll).coerceIn(0f, 1f)
    val trackRange = (viewportHeightPx - thumbHeightPx).coerceAtLeast(1f)
    val thumbOffsetPx = scrollFraction * trackRange

    // Item index under the thumb (for popup text).
    val currentItemIndex = (scrollFraction * (totalItemsCount - 1))
        .roundToInt()
        .coerceIn(0, totalItemsCount - 1)

    return ThumbScrollState(
        thumbOffsetPx = thumbOffsetPx,
        currentItemIndex = currentItemIndex,
        totalScrollRangePx = maxScroll,
        estimatedStride = stride,
        itemsPerRow = itemsPerRow,
    )
}

/**
 * Converts a thumb drag offset to a scroll target (item index + pixel offset).
 *
 * @return `(targetItemIndex, targetScrollOffset)` suitable for [FastScrollableState.scrollToItem].
 */
fun computeDragScrollTarget(
    thumbOffsetPx: Float,
    viewportHeightPx: Float,
    thumbHeightPx: Int,
    totalScrollRangePx: Float,
    estimatedStride: Float,
    itemsPerRow: Int,
    totalItemsCount: Int,
    beforeContentPadding: Int,
): Pair<Int, Int> {
    val trackRange = (viewportHeightPx - thumbHeightPx).coerceAtLeast(1f)
    val thumbFraction = (thumbOffsetPx / trackRange).coerceIn(0f, 1f)
    val targetScrollPx = thumbFraction * totalScrollRangePx

    // Convert absolute scroll position to item index + offset within that item's row.
    val scrollPastPadding = targetScrollPx - beforeContentPadding
    if (scrollPastPadding <= 0) return 0 to 0

    val targetRow = (scrollPastPadding / estimatedStride).toInt()
    val targetItemIndex = (targetRow * itemsPerRow).coerceIn(0, totalItemsCount - 1)
    val targetOffset = (scrollPastPadding - targetRow * estimatedStride).toInt().coerceAtLeast(0)

    return targetItemIndex to targetOffset
}
