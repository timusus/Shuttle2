package com.simplecityapps.shuttle.ui.common.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Layout information for a single visible item, used by [FastScroller]
 * to compute scroll position and stride.
 */
data class VisibleItemInfo(
    val index: Int,
    /** Main-axis offset relative to the viewport start (negative = above viewport). */
    val offset: Int,
    /** Main-axis size of the item in pixels. */
    val mainAxisSize: Int,
)

/**
 * Abstraction over [LazyListState] and [LazyGridState] so [FastScroller]
 * can drive either layout without duplication.
 *
 * Exposes the raw layout information needed to compute scroll position,
 * stride (including spacing), and items-per-row for grids.
 */
interface FastScrollableState {
    val isScrollInProgress: Boolean
    val firstVisibleItemIndex: Int
    val firstVisibleItemScrollOffset: Int
    val totalItemsCount: Int
    val visibleItems: List<VisibleItemInfo>
    val beforeContentPadding: Int
    val afterContentPadding: Int
    suspend fun scrollToItem(index: Int, scrollOffset: Int)
}

/** Adapts a [LazyListState] for use with [FastScroller]. */
class LazyListFastScrollableState(private val state: LazyListState) : FastScrollableState {
    override val isScrollInProgress: Boolean get() = state.isScrollInProgress
    override val firstVisibleItemIndex: Int get() = state.firstVisibleItemIndex
    override val firstVisibleItemScrollOffset: Int get() = state.firstVisibleItemScrollOffset
    override val totalItemsCount: Int get() = state.layoutInfo.totalItemsCount
    override val visibleItems: List<VisibleItemInfo>
        get() = state.layoutInfo.visibleItemsInfo.map {
            VisibleItemInfo(index = it.index, offset = it.offset, mainAxisSize = it.size)
        }
    override val beforeContentPadding: Int get() = state.layoutInfo.beforeContentPadding
    override val afterContentPadding: Int get() = state.layoutInfo.afterContentPadding
    override suspend fun scrollToItem(index: Int, scrollOffset: Int) = state.scrollToItem(index, scrollOffset)
}

/** Adapts a [LazyGridState] for use with [FastScroller]. */
class LazyGridFastScrollableState(private val state: LazyGridState) : FastScrollableState {
    override val isScrollInProgress: Boolean get() = state.isScrollInProgress
    override val firstVisibleItemIndex: Int get() = state.firstVisibleItemIndex
    override val firstVisibleItemScrollOffset: Int get() = state.firstVisibleItemScrollOffset
    override val totalItemsCount: Int get() = state.layoutInfo.totalItemsCount
    override val visibleItems: List<VisibleItemInfo>
        get() = state.layoutInfo.visibleItemsInfo.map {
            VisibleItemInfo(index = it.index, offset = it.offset.y, mainAxisSize = it.size.height)
        }
    override val beforeContentPadding: Int get() = state.layoutInfo.beforeContentPadding
    override val afterContentPadding: Int get() = state.layoutInfo.afterContentPadding
    override suspend fun scrollToItem(index: Int, scrollOffset: Int) = state.scrollToItem(index, scrollOffset)
}

@Composable
fun rememberFastScrollableState(state: LazyListState): FastScrollableState = remember(state) { LazyListFastScrollableState(state) }

@Composable
fun rememberFastScrollableState(state: LazyGridState): FastScrollableState = remember(state) { LazyGridFastScrollableState(state) }
