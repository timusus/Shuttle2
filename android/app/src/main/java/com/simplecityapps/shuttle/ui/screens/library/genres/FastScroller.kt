package com.simplecityapps.shuttle.ui.screens.library.genres

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.simplecityapps.shuttle.compose.ui.theme.AppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun FastScroller(
    getPopupText: (index: Int) -> String?,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    track: @Composable BoxScope.() -> Unit = {
        DefaultTrack(modifier = Modifier.fillMaxHeight())
    },
    thumb: @Composable () -> Unit = {
        DefaultThumb()
    },
    popup: @Composable (index: Int) -> Unit = { currentItemIndex ->
        DefaultPopup(text = getPopupText(currentItemIndex))
    }
) {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    // Drag-related state.
    var isDragging by remember { mutableStateOf(false) }
    var dragThumbOffsetPx by remember { mutableFloatStateOf(0f) }
    var initialThumbOffset by remember { mutableFloatStateOf(0f) }
    var cumulativeDrag by remember { mutableFloatStateOf(0f) }
    var measuredThumbY by remember { mutableFloatStateOf(0f) }
    var measuredThumbSize by remember { mutableStateOf(IntSize.Zero) }
    var isVisible by remember { mutableStateOf(true) }

    // Auto-hide the scroller when not scrolling or dragging.
    LaunchedEffect(state.isScrollInProgress, isDragging) {
        if (!state.isScrollInProgress && !isDragging) {
            delay(1500)
            if (!state.isScrollInProgress && !isDragging) {
                isVisible = false
            }
        } else {
            isVisible = true
        }
    }
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInHorizontally(initialOffsetX = { measuredThumbSize.width }),
        exit = slideOutHorizontally(targetOffsetX = { measuredThumbSize.width })
    ) {
        BoxWithConstraints(
            modifier = modifier.wrapContentWidth(Alignment.End),
            contentAlignment = Alignment.TopEnd
        ) {
            // Use the available maxHeight as the viewport height.
            val viewportHeightPx = with(density) { maxHeight.toPx() }
            val totalItemsCount by remember {
                derivedStateOf { state.layoutInfo.totalItemsCount }
            }

            // Compute the thumb scroll state using an average item height.
            val thumbScrollState = computeThumbScrollState(
                state = state,
                totalItemsCount = totalItemsCount,
                viewportHeightPx = viewportHeightPx,
                thumbHeight = measuredThumbSize.height
            )
            val computedThumbOffsetPx = thumbScrollState.computedThumbOffsetPx

            // When dragging, use the user-controlled offset; otherwise, use the computed value.
            val thumbOffsetPx = if (isDragging) dragThumbOffsetPx else computedThumbOffsetPx

            // Calculate the thumb center and current item index.
            val thumbCenter = thumbOffsetPx + measuredThumbSize.height / 2
            val thumbCenterFraction = ((thumbCenter - measuredThumbSize.height / 2) / (viewportHeightPx - measuredThumbSize.height)).coerceIn(0f, 1f)
            val currentItemIndex = if (isDragging) {
                (thumbCenterFraction * (totalItemsCount - 1)).roundToInt()
            } else {
                thumbScrollState.currentItemIndex
            }

            // Draw the track.
            track()

            // Draw the thumb with drag gesture handling.
            Box(
                modifier = Modifier
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .offset { IntOffset(x = 0, y = thumbOffsetPx.roundToInt()) }
                    .onGloballyPositioned { coordinates ->
                        measuredThumbSize = coordinates.size
                        measuredThumbY = coordinates.positionInParent().y
                    }
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragStart = { _: Offset ->
                                isDragging = true
                                initialThumbOffset = measuredThumbY
                                cumulativeDrag = 0f
                                dragThumbOffsetPx = measuredThumbY
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                cumulativeDrag += dragAmount
                                val (newDragOffset, scrollTarget) = computeDragScrollOffset(
                                    initialThumbOffset = initialThumbOffset,
                                    cumulativeDrag = cumulativeDrag,
                                    viewportHeightPx = viewportHeightPx,
                                    thumbHeight = measuredThumbSize.height,
                                    totalScrollRangePx = thumbScrollState.totalScrollRangePx,
                                    totalItemsCount = totalItemsCount,
                                    averageItemHeight = thumbScrollState.averageItemHeight
                                )
                                dragThumbOffsetPx = newDragOffset
                                coroutineScope.launch {
                                    state.scrollToItem(scrollTarget.first, scrollTarget.second)
                                }
                            },
                            onDragEnd = { isDragging = false },
                            onDragCancel = { isDragging = false }
                        )
                    },
                contentAlignment = Alignment.TopEnd
            ) {
                thumb()
            }

            // Position the popup so its bottom aligns with the thumb center.
            var popupHeight by remember { mutableFloatStateOf(0f) }
            Box(
                modifier = Modifier
                    .offset {
                        val desiredPopupY = thumbCenter - popupHeight
                        val finalPopupY = desiredPopupY.coerceAtLeast(0f)
                        IntOffset(0, finalPopupY.roundToInt())
                    }
                    .onGloballyPositioned { coordinates ->
                        popupHeight = coordinates.size.height.toFloat()
                    }
            ) {
                AnimatedVisibility(
                    visible = isDragging || LocalInspectionMode.current,
                    enter = fadeIn(tween(durationMillis = 150)),
                    exit = fadeOut(tween(durationMillis = 200))
                ) {
                    popup(currentItemIndex)
                }
            }
        }
    }
}

@Composable
fun DefaultTrack(
    modifier: Modifier = Modifier,
    trackWidth: Dp = 7.dp,
    trackColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
) {
    Box(
        modifier = modifier
            .padding(top = 16.dp, bottom = 16.dp)
            .clip(RoundedCornerShape(percent = 50))
            .width(trackWidth)
            .background(trackColor)
    )
}

@Composable
fun DefaultThumb(
    modifier: Modifier = Modifier,
    thumbWidth: Dp = 8.dp,
    thumbHeight: Dp = 52.dp,
    thumbColor: Color = MaterialTheme.colorScheme.primary
) {
    Box(
        modifier = modifier
            .size(width = thumbWidth, height = thumbHeight)
            .clip(RoundedCornerShape(percent = 50))
            .background(thumbColor)
    )
}

@Composable
fun DefaultPopup(
    text: String?,
    modifier: Modifier = Modifier
) {
    text?.let {
        Box(
            modifier = modifier
                .padding(end = 16.dp)
                .sizeIn(minWidth = 64.dp, minHeight = 64.dp)
                .background(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(
                        topStartPercent = 50,
                        topEndPercent = 50,
                        bottomStartPercent = 50,
                        bottomEndPercent = 0
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Data class holding computed thumb scroll state, including an estimated average item height.
 */
private data class ThumbScrollState(
    val computedThumbOffsetPx: Float,
    val currentItemIndex: Int,
    val totalScrollRangePx: Float,
    val averageItemHeight: Float
)

/**
 * Computes the thumb scroll state using the average height of visible items.
 *
 * This makes the fast scroller agnostic to fixed item heights.
 */
private fun computeThumbScrollState(
    state: LazyListState,
    totalItemsCount: Int,
    viewportHeightPx: Float,
    thumbHeight: Int
): ThumbScrollState {
    val visibleItems = state.layoutInfo.visibleItemsInfo
    val averageItemHeight = if (visibleItems.isNotEmpty()) {
        visibleItems.sumOf { it.size.toLong() }.toFloat() / visibleItems.size
    } else {
        1f
    }
    val totalContentHeightPx = averageItemHeight * totalItemsCount
    val totalScrollRangePx = (totalContentHeightPx - viewportHeightPx).coerceAtLeast(1f)
    val currentScrollOffsetPx = state.firstVisibleItemIndex * averageItemHeight + state.firstVisibleItemScrollOffset
    val scrollFraction = (currentScrollOffsetPx / totalScrollRangePx).coerceIn(0f, 1f)
    val computedThumbOffsetPx = scrollFraction * (viewportHeightPx - thumbHeight)
    val thumbCenter = computedThumbOffsetPx + thumbHeight / 2
    val thumbCenterFraction = ((thumbCenter - thumbHeight / 2) / (viewportHeightPx - thumbHeight)).coerceIn(0f, 1f)
    val currentItemIndex = (thumbCenterFraction * (totalItemsCount - 1)).roundToInt()
    return ThumbScrollState(
        computedThumbOffsetPx = computedThumbOffsetPx,
        currentItemIndex = currentItemIndex,
        totalScrollRangePx = totalScrollRangePx,
        averageItemHeight = averageItemHeight
    )
}

/**
 * Computes a new drag offset and corresponding target scroll position based on the average item height.
 */
private fun computeDragScrollOffset(
    initialThumbOffset: Float,
    cumulativeDrag: Float,
    viewportHeightPx: Float,
    thumbHeight: Int,
    totalScrollRangePx: Float,
    totalItemsCount: Int,
    averageItemHeight: Float
): Pair<Float, Pair<Int, Int>> {
    val newDragOffset = (initialThumbOffset + cumulativeDrag).coerceIn(0f, viewportHeightPx - thumbHeight)
    val newFraction = newDragOffset / (viewportHeightPx - thumbHeight)
    val newScrollOffsetPx = newFraction * totalScrollRangePx
    val targetIndex = (newScrollOffsetPx / averageItemHeight).toInt().coerceIn(0, totalItemsCount - 1)
    val targetItemOffset = (newScrollOffsetPx % averageItemHeight).toInt()
    return newDragOffset to (targetIndex to targetItemOffset)
}

@Preview(showBackground = true)
@Composable
private fun FastScrollPreview() {
    AppTheme {
        val state = rememberLazyListState(initialFirstVisibleItemIndex = 2)
        Box(modifier = Modifier.padding(vertical = 16.dp)) {
            LazyColumn(
                state = state
            ) {
                items(20) {
                    Text(
                        modifier = Modifier
                            .sizeIn(minHeight = 56.dp)
                            .padding(horizontal = 16.dp),
                        text = "Item $it"
                    )
                }
            }
            FastScroller(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 16.dp),
                state = state,
                getPopupText = { (it).toString() }
            )
        }
    }
}
