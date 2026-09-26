package com.simplecityapps.shuttle.ui.common.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The thumb's touch target, which the 52dp thumb fills the height of. */
private val ThumbTouchSize = 52.dp

/**
 * A fast scroller that jumps by first letter (#491): the [sections] sit evenly along the track, so dragging the thumb
 * steps through the letters and scrolls to the first item of each, with the letter in a bubble beside it. At rest the
 * thumb marks the section of the first visible item. [itemOffset] counts the items that lead the list, such as a
 * header, which [LetterSection.firstIndex] doesn't.
 *
 * It only scrolls when the letter changes, so a drag across an 18k-song library costs a few dozen jumps, not one per
 * pixel (#155).
 */
@Composable
fun AlphabetFastScroller(
    sections: List<LetterSection>,
    scrollableState: FastScrollableState,
    modifier: Modifier = Modifier,
    itemOffset: Int = 0,
) {
    if (sections.isEmpty()) return
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val currentSections by rememberUpdatedState(sections)
    val scrolledSection by remember(scrollableState, itemOffset) {
        derivedStateOf { sectionIndexOf(currentSections, scrollableState.firstVisibleItemIndex - itemOffset) }
    }
    var isDragging by remember { mutableStateOf(false) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    var dragSection by remember { mutableIntStateOf(0) }
    var isVisible by remember { mutableStateOf(true) }

    // Auto-hide when neither scrolling nor dragging, as FastScroller does.
    LaunchedEffect(scrollableState.isScrollInProgress, isDragging) {
        if (!scrollableState.isScrollInProgress && !isDragging) {
            delay(1500)
            isVisible = false
        } else {
            isVisible = true
        }
    }

    AnimatedVisibility(visible = isVisible, enter = fadeIn(), exit = fadeOut()) {
        BoxWithConstraints(modifier.wrapContentWidth(Alignment.End), contentAlignment = Alignment.TopEnd) {
            val trackRangePx = (constraints.maxHeight - with(density) { ThumbTouchSize.toPx() }).coerceAtLeast(1f)
            val section = (if (isDragging) dragSection else scrolledSection).coerceIn(0, sections.lastIndex)
            val restingOffsetPx by animateFloatAsState(sectionFraction(section, sections.size) * trackRangePx, label = "alphabet-thumb")
            val thumbOffsetPx = if (isDragging) dragOffsetPx else restingOffsetPx
            val currentTrackRangePx by rememberUpdatedState(trackRangePx)
            val currentRestingOffsetPx by rememberUpdatedState(restingOffsetPx)
            val currentSection by rememberUpdatedState(section)

            DefaultTrack(Modifier.fillMaxHeight())

            Box(
                modifier = Modifier
                    .offset { IntOffset(0, thumbOffsetPx.roundToInt()) }
                    .size(ThumbTouchSize)
                    .pointerInput(scrollableState, itemOffset) {
                        detectVerticalDragGestures(
                            onDragStart = {
                                isDragging = true
                                dragOffsetPx = currentRestingOffsetPx
                                dragSection = currentSection
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                dragOffsetPx = (dragOffsetPx + dragAmount).coerceIn(0f, currentTrackRangePx)
                                val target = sectionIndexAt(currentSections.size, dragOffsetPx / currentTrackRangePx)
                                if (target != dragSection) {
                                    dragSection = target
                                    // The first section scrolls to the very top, so a leading header shows again.
                                    val index = if (target == 0) 0 else currentSections[target].firstIndex + itemOffset
                                    coroutineScope.launch { scrollableState.scrollToItem(index, 0) }
                                }
                            },
                            onDragEnd = { isDragging = false },
                            onDragCancel = { isDragging = false },
                        )
                    },
                contentAlignment = Alignment.TopEnd,
            ) {
                DefaultThumb()
            }

            if (isDragging) {
                // The bubble's bottom sits at the thumb's centre, as FastScroller's popup does.
                var bubbleHeightPx by remember { mutableFloatStateOf(0f) }
                val thumbCentrePx = thumbOffsetPx + with(density) { ThumbTouchSize.toPx() } / 2
                Box(
                    Modifier
                        .offset { IntOffset(0, (thumbCentrePx - bubbleHeightPx).coerceAtLeast(0f).roundToInt()) }
                        .onSizeChanged { bubbleHeightPx = it.height.toFloat() },
                ) {
                    DefaultPopup(text = sections[section].letter)
                }
            }
        }
    }
}
