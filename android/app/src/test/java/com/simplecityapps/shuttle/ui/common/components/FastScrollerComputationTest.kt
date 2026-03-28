package com.simplecityapps.shuttle.ui.common.components

import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import org.junit.Test

/**
 * Unit tests for [computeThumbScrollState] and [computeDragScrollTarget].
 *
 * These verify the scroll-fraction computation is correct for both lists
 * and grids, and that it handles edge cases (top, bottom, content padding,
 * varying item sizes) without jumpiness or incorrect bounds.
 */
class FastScrollerComputationTest {

    // ---------- List: basic scroll fraction ----------

    @Test
    fun `list at top has thumb at top`() {
        val state = fakeListState(
            totalItems = 100,
            itemHeight = 200,
            spacing = 16,
            viewportHeight = 1000,
            firstVisibleIndex = 0,
            scrollOffset = 0,
            beforePadding = 0,
            afterPadding = 0,
        )
        val result = computeThumbScrollState(state, 1000f, THUMB_HEIGHT)
        result.thumbOffsetPx shouldBe 0f
    }

    @Test
    fun `list at bottom has thumb at bottom of track`() {
        // 100 items × 200px + 99 spacings × 16px = 21584
        // maxScroll = 21584 - 1000 = 20584
        // At bottom: items 95–99 visible. Item 95 starts at 95 * 216 = 20520.
        // To see items 95-99 + fit viewport: scrollPx = maxScroll = 20584.
        // item 95 offset in viewport = 20520 - 20584 = -64 (64px above viewport top)
        // But firstVisibleItemIndex might be 95 with offset 64.
        // Let's just place firstVisibleIndex at a position where fraction should be ~1.
        val totalItems = 100
        val itemHeight = 200
        val spacing = 16
        val stride = itemHeight + spacing // 216
        val totalContent = totalItems * itemHeight + (totalItems - 1) * spacing // 21584
        val viewport = 1000
        val maxScroll = totalContent - viewport // 20584

        // At max scroll, which row is at the top?
        // item at top: floor(maxScroll / stride) = floor(20584 / 216) = 95
        // offset within that item: 20584 - 95*216 = 20584 - 20520 = 64
        val state = fakeListState(
            totalItems = totalItems,
            itemHeight = itemHeight,
            spacing = spacing,
            viewportHeight = viewport,
            firstVisibleIndex = 95,
            scrollOffset = 64,
            beforePadding = 0,
            afterPadding = 0,
        )
        val result = computeThumbScrollState(state, viewport.toFloat(), THUMB_HEIGHT)
        val trackRange = viewport - THUMB_HEIGHT
        // Thumb should be at/near bottom of track
        result.thumbOffsetPx shouldBeGreaterThan (trackRange * 0.98f)
    }

    @Test
    fun `list scroll fraction increases monotonically`() {
        val totalItems = 100
        val itemHeight = 200
        val spacing = 16
        val stride = itemHeight + spacing
        val viewport = 1000

        var prevFraction = -1f
        // Walk through scroll positions: index 0..95, offset stepping through each item
        for (index in 0..90 step 10) {
            for (offset in listOf(0, stride.toInt() / 2)) {
                val state = fakeListState(
                    totalItems = totalItems,
                    itemHeight = itemHeight,
                    spacing = spacing,
                    viewportHeight = viewport,
                    firstVisibleIndex = index,
                    scrollOffset = offset,
                    beforePadding = 0,
                    afterPadding = 0,
                )
                val result = computeThumbScrollState(state, viewport.toFloat(), THUMB_HEIGHT)
                val fraction = result.thumbOffsetPx / (viewport - THUMB_HEIGHT)
                fraction shouldBeGreaterThan prevFraction
                prevFraction = fraction
            }
        }
    }

    // ---------- List: content padding ----------

    @Test
    fun `list with content padding at top has thumb at top`() {
        val state = fakeListState(
            totalItems = 100,
            itemHeight = 200,
            spacing = 16,
            viewportHeight = 1000,
            firstVisibleIndex = 0,
            scrollOffset = 0,
            beforePadding = 48, // 48px top content padding
            afterPadding = 48,
        )
        val result = computeThumbScrollState(state, 1000f, THUMB_HEIGHT)
        result.thumbOffsetPx shouldBe 0f
    }

    @Test
    fun `list with content padding at bottom reaches end of track`() {
        val totalItems = 50
        val itemHeight = 200
        val spacing = 16
        val stride = itemHeight + spacing
        val viewport = 1000
        val beforePadding = 48
        val afterPadding = 48
        val totalContent = beforePadding + totalItems * itemHeight + (totalItems - 1) * spacing + afterPadding
        val maxScroll = totalContent - viewport

        // Compute what firstVisibleIndex/offset would be at maxScroll
        val scrollPastPadding = maxScroll - beforePadding
        val topRow = scrollPastPadding / stride
        val topOffset = scrollPastPadding - topRow * stride

        val state = fakeListState(
            totalItems = totalItems,
            itemHeight = itemHeight,
            spacing = spacing,
            viewportHeight = viewport,
            firstVisibleIndex = topRow,
            scrollOffset = topOffset,
            beforePadding = beforePadding,
            afterPadding = afterPadding,
        )
        val result = computeThumbScrollState(state, viewport.toFloat(), THUMB_HEIGHT)
        val trackRange = viewport - THUMB_HEIGHT
        result.thumbOffsetPx shouldBeGreaterThan (trackRange * 0.95f)
    }

    // ---------- Grid ----------

    @Test
    fun `grid at top has thumb at top`() {
        val state = fakeGridState(
            totalItems = 30,
            columns = 3,
            rowHeight = 300,
            spacing = 8,
            viewportHeight = 1000,
            firstVisibleIndex = 0,
            scrollOffset = 0,
            beforePadding = 0,
            afterPadding = 0,
        )
        val result = computeThumbScrollState(state, 1000f, THUMB_HEIGHT)
        result.thumbOffsetPx shouldBe 0f
        result.itemsPerRow shouldBe 3
    }

    @Test
    fun `grid at bottom reaches end of track`() {
        val totalItems = 30
        val columns = 3
        val rowHeight = 300
        val spacing = 8
        val stride = rowHeight + spacing
        val totalRows = 10
        val viewport = 1000
        val totalContent = totalRows * rowHeight + (totalRows - 1) * spacing // 3000 + 72 = 3072
        val maxScroll = totalContent - viewport // 2072

        val scrollPastItems = maxScroll
        val topRow = scrollPastItems / stride
        val topOffset = scrollPastItems - topRow * stride
        val firstVisibleIndex = topRow * columns

        val state = fakeGridState(
            totalItems = totalItems,
            columns = columns,
            rowHeight = rowHeight,
            spacing = spacing,
            viewportHeight = viewport,
            firstVisibleIndex = firstVisibleIndex,
            scrollOffset = topOffset,
            beforePadding = 0,
            afterPadding = 0,
        )
        val result = computeThumbScrollState(state, viewport.toFloat(), THUMB_HEIGHT)
        val trackRange = viewport - THUMB_HEIGHT
        result.thumbOffsetPx shouldBeGreaterThan (trackRange * 0.95f)
    }

    @Test
    fun `grid scroll fraction increases monotonically`() {
        val totalItems = 90
        val columns = 3
        val rowHeight = 300
        val spacing = 8
        val stride = rowHeight + spacing
        val viewport = 1000
        val totalRows = 30

        var prevFraction = -1f
        // Stop well before the bottom to avoid the clamped 1.0 region.
        for (row in 0 until totalRows - 5) {
            for (offset in listOf(0, stride.toInt() / 2)) {
                val state = fakeGridState(
                    totalItems = totalItems,
                    columns = columns,
                    rowHeight = rowHeight,
                    spacing = spacing,
                    viewportHeight = viewport,
                    firstVisibleIndex = row * columns,
                    scrollOffset = offset,
                    beforePadding = 0,
                    afterPadding = 0,
                )
                val result = computeThumbScrollState(state, viewport.toFloat(), THUMB_HEIGHT)
                val fraction = result.thumbOffsetPx / (viewport - THUMB_HEIGHT)
                fraction shouldBeGreaterThan prevFraction
                prevFraction = fraction
            }
        }
    }

    // ---------- Drag: round-trip ----------

    @Test
    fun `drag to top scrolls to item 0`() {
        val (index, offset) = computeDragScrollTarget(
            thumbOffsetPx = 0f,
            viewportHeightPx = 1000f,
            thumbHeightPx = THUMB_HEIGHT,
            totalScrollRangePx = 20000f,
            estimatedStride = 216f,
            itemsPerRow = 1,
            totalItemsCount = 100,
            beforeContentPadding = 0,
        )
        index shouldBe 0
        offset shouldBe 0
    }

    @Test
    fun `drag to bottom scrolls near last item`() {
        val trackRange = 1000f - THUMB_HEIGHT
        val (index, _) = computeDragScrollTarget(
            thumbOffsetPx = trackRange,
            viewportHeightPx = 1000f,
            thumbHeightPx = THUMB_HEIGHT,
            totalScrollRangePx = 20000f,
            estimatedStride = 216f,
            itemsPerRow = 1,
            totalItemsCount = 100,
            beforeContentPadding = 0,
        )
        index shouldBeGreaterThanOrEqual 90
        index shouldBeLessThanOrEqual 99
    }

    @Test
    fun `grid drag targets correct row-aligned indices`() {
        val columns = 3
        val stride = 308f
        val (index, _) = computeDragScrollTarget(
            thumbOffsetPx = 500f,
            viewportHeightPx = 1000f,
            thumbHeightPx = THUMB_HEIGHT,
            totalScrollRangePx = 3000f,
            estimatedStride = stride,
            itemsPerRow = columns,
            totalItemsCount = 30,
            beforeContentPadding = 0,
        )
        // Index should be a multiple of columns (row-aligned)
        (index % columns) shouldBe 0
    }

    // ---------- Edge cases ----------

    @Test
    fun `empty list returns zero thumb offset`() {
        val state = object : FastScrollableState {
            override val isScrollInProgress = false
            override val firstVisibleItemIndex = 0
            override val firstVisibleItemScrollOffset = 0
            override val totalItemsCount = 0
            override val visibleItems = emptyList<VisibleItemInfo>()
            override val beforeContentPadding = 0
            override val afterContentPadding = 0
            override suspend fun scrollToItem(index: Int, scrollOffset: Int) {}
        }
        val result = computeThumbScrollState(state, 1000f, THUMB_HEIGHT)
        result.thumbOffsetPx shouldBe 0f
    }

    @Test
    fun `single item that fits in viewport returns zero`() {
        val state = fakeListState(
            totalItems = 1,
            itemHeight = 200,
            spacing = 0,
            viewportHeight = 1000,
            firstVisibleIndex = 0,
            scrollOffset = 0,
            beforePadding = 0,
            afterPadding = 0,
        )
        val result = computeThumbScrollState(state, 1000f, THUMB_HEIGHT)
        result.thumbOffsetPx shouldBe 0f
    }

    // ---------- Helpers ----------

    companion object {
        private const val THUMB_HEIGHT = 52

        /**
         * Creates a fake [FastScrollableState] simulating a [LazyColumn] with uniform items.
         */
        fun fakeListState(
            totalItems: Int,
            itemHeight: Int,
            spacing: Int,
            viewportHeight: Int,
            firstVisibleIndex: Int,
            scrollOffset: Int,
            beforePadding: Int,
            afterPadding: Int,
        ): FastScrollableState {
            val stride = itemHeight + spacing

            // Build visible items: starting from firstVisibleIndex, fill the viewport.
            // The first item's offset = beforePadding - scrollPosition_within_padded_content
            // When firstVisibleIndex=0 and scrollOffset=0, first item offset = beforePadding.
            val firstItemViewportOffset = beforePadding - (firstVisibleIndex * stride + scrollOffset) +
                firstVisibleIndex * stride // simplifies to: beforePadding - scrollOffset

            val visible = mutableListOf<VisibleItemInfo>()
            var offset = beforePadding - scrollOffset
            if (firstVisibleIndex > 0) {
                // If we're past item 0, the offset accounts for items scrolled past.
                // First visible item's offset = -(scrollOffset within that item)
                offset = -scrollOffset
            }
            for (i in firstVisibleIndex until totalItems) {
                if (offset >= viewportHeight) break
                visible.add(VisibleItemInfo(index = i, offset = offset, mainAxisSize = itemHeight))
                offset += stride
            }

            return object : FastScrollableState {
                override val isScrollInProgress = true
                override val firstVisibleItemIndex = firstVisibleIndex
                override val firstVisibleItemScrollOffset = scrollOffset
                override val totalItemsCount = totalItems
                override val visibleItems = visible
                override val beforeContentPadding = beforePadding
                override val afterContentPadding = afterPadding
                override suspend fun scrollToItem(index: Int, scrollOffset: Int) {}
            }
        }

        /**
         * Creates a fake [FastScrollableState] simulating a [LazyVerticalGrid].
         */
        fun fakeGridState(
            totalItems: Int,
            columns: Int,
            rowHeight: Int,
            spacing: Int,
            viewportHeight: Int,
            firstVisibleIndex: Int,
            scrollOffset: Int,
            beforePadding: Int,
            afterPadding: Int,
        ): FastScrollableState {
            val stride = rowHeight + spacing
            val firstRow = firstVisibleIndex / columns

            val visible = mutableListOf<VisibleItemInfo>()
            var rowOffset = if (firstRow == 0) beforePadding - scrollOffset else -scrollOffset
            var row = firstRow
            while (rowOffset < viewportHeight && row * columns < totalItems) {
                val itemsInRow = minOf(columns, totalItems - row * columns)
                for (col in 0 until itemsInRow) {
                    visible.add(
                        VisibleItemInfo(
                            index = row * columns + col,
                            offset = rowOffset,
                            mainAxisSize = rowHeight,
                        )
                    )
                }
                rowOffset += stride
                row++
            }

            return object : FastScrollableState {
                override val isScrollInProgress = true
                override val firstVisibleItemIndex = firstVisibleIndex
                override val firstVisibleItemScrollOffset = scrollOffset
                override val totalItemsCount = totalItems
                override val visibleItems = visible
                override val beforeContentPadding = beforePadding
                override val afterContentPadding = afterPadding
                override suspend fun scrollToItem(index: Int, scrollOffset: Int) {}
            }
        }
    }
}
