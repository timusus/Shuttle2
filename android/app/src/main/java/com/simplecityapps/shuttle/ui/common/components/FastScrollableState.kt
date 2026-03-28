package com.simplecityapps.shuttle.ui.common.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember

/**
 * Abstraction over [LazyListState] and [LazyGridState] so [FastScroller]
 * can drive either layout without duplication.
 */
interface FastScrollableState {
    val isScrollInProgress: Boolean
    val firstVisibleItemIndex: Int
    val firstVisibleItemScrollOffset: Int
    val totalItemsCount: Int
    val visibleItemMainAxisSizes: List<Int>
    suspend fun scrollToItem(index: Int, scrollOffset: Int)
}

/** Adapts a [LazyListState] for use with [FastScroller]. */
class LazyListFastScrollableState(private val state: LazyListState) : FastScrollableState {
    override val isScrollInProgress: Boolean get() = state.isScrollInProgress
    override val firstVisibleItemIndex: Int get() = state.firstVisibleItemIndex
    override val firstVisibleItemScrollOffset: Int get() = state.firstVisibleItemScrollOffset
    override val totalItemsCount: Int get() = state.layoutInfo.totalItemsCount
    override val visibleItemMainAxisSizes: List<Int> get() = state.layoutInfo.visibleItemsInfo.map { it.size }
    override suspend fun scrollToItem(index: Int, scrollOffset: Int) = state.scrollToItem(index, scrollOffset)
}

/** Adapts a [LazyGridState] for use with [FastScroller]. */
class LazyGridFastScrollableState(private val state: LazyGridState) : FastScrollableState {
    override val isScrollInProgress: Boolean get() = state.isScrollInProgress
    override val firstVisibleItemIndex: Int get() = state.firstVisibleItemIndex
    override val firstVisibleItemScrollOffset: Int get() = state.firstVisibleItemScrollOffset
    override val totalItemsCount: Int get() = state.layoutInfo.totalItemsCount
    override val visibleItemMainAxisSizes: List<Int> get() = state.layoutInfo.visibleItemsInfo.map { it.size.height }
    override suspend fun scrollToItem(index: Int, scrollOffset: Int) = state.scrollToItem(index, scrollOffset)
}

@Composable
fun rememberFastScrollableState(state: LazyListState): FastScrollableState = remember(state) { LazyListFastScrollableState(state) }

@Composable
fun rememberFastScrollableState(state: LazyGridState): FastScrollableState = remember(state) { LazyGridFastScrollableState(state) }
