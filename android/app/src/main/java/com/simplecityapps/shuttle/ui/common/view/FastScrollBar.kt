package com.simplecityapps.shuttle.ui.common.view

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simplecityapps.shuttle.R
import io.github.oikvpqya.compose.fastscroller.ThumbStyle
import io.github.oikvpqya.compose.fastscroller.TrackStyle
import io.github.oikvpqya.compose.fastscroller.VerticalScrollbar
import io.github.oikvpqya.compose.fastscroller.indicator.IndicatorConstants
import io.github.oikvpqya.compose.fastscroller.material3.defaultMaterialScrollbarStyle
import io.github.oikvpqya.compose.fastscroller.rememberScrollbarAdapter
import kotlinx.coroutines.delay

@Composable
fun FastScrollableListContainer(
    listState: LazyListState,
    indicatorTextProvider: (currentElementIndex: Int) -> String,
    modifier: Modifier = Modifier,
    content: @Composable (BoxScope.() -> Unit),
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(start = 16.dp),
    ) {
        content()

        Row(Modifier.align(Alignment.TopEnd)) {
            FastScrollbar(
                listState = listState,
                indicatorTextProvider = indicatorTextProvider,
                modifier = Modifier
                    .fillMaxHeight(),
            )
        }
    }
}

@Composable
fun FastScrollbar(
    listState: LazyListState,
    indicatorTextProvider: (currentElementIndex: Int) -> String,
    modifier: Modifier = Modifier,
) {
    val scrollbarInteractionSource = remember { MutableInteractionSource() }
    val isDragging by scrollbarInteractionSource.collectIsDraggedAsState()

    AnimatedScrollbarVisibility(
        isScrolling = listState.isScrollInProgress,
        isDragging = isDragging,
        modifier = modifier,
    ) {
        VerticalScrollbar(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .background(colorResource(R.color.fast_scrollbar_track)),
            adapter = rememberScrollbarAdapter(listState),
            interactionSource = scrollbarInteractionSource,
            style = scrollbarStyle(),
            enablePressToScroll = false,
            indicator = { position, isVisible ->
                ScrollBarIndicator(
                    listState = listState,
                    position = position,
                    isVisible = isVisible,
                    scrollbarInteractionSource = scrollbarInteractionSource,
                    textProvider = indicatorTextProvider,
                )
            },
        )
    }
}

@Composable
fun AnimatedScrollbarVisibility(
    isScrolling: Boolean,
    isDragging: Boolean,
    modifier: Modifier = Modifier,
    scrollbarContent: @Composable (BoxScope.() -> Unit),
) {
    var isVisible by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        isVisible = false
    }

    LaunchedEffect(isScrolling, isDragging) {
        if (isVisible) {
            // Delay hiding after scrolling or dragging has stopped
            delay(1000)
        }

        isVisible = isScrolling || isDragging
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInHorizontally { fullWidth -> fullWidth / 2 },
        exit = slideOutHorizontally(
            animationSpec = tween(durationMillis = 500, delayMillis = 1000)
        ) { fullWidth -> fullWidth / 2 },
        modifier = modifier,
    ) {
        Box {
            scrollbarContent()
        }
    }
}

private val INDICATOR_SIZE = 64.dp

@Composable
private fun BoxScope.ScrollBarIndicator(
    listState: LazyListState,
    position: Float,
    isVisible: Boolean,
    scrollbarInteractionSource: InteractionSource,
    textProvider: (currentElementIndex: Int) -> String,
    modifier: Modifier = Modifier,
) {
    val isDragging by scrollbarInteractionSource.collectIsDraggedAsState()
    val isHovered by scrollbarInteractionSource.collectIsHoveredAsState()
    val indicatorAlpha by animateFloatAsState(
        if (isDragging || isHovered) 1f else 0f,
        label = "indicator-alpha-animation",
    )
    val listPositionState = remember { derivedStateOf { listState.firstVisibleItemIndex } }

    Box(
        modifier = modifier
            .align(Alignment.TopEnd)
            .padding(end = 5.dp)
            .graphicsLayer {
                val y = -((INDICATOR_SIZE / 2).toPx())
                translationY = (y + position).coerceAtLeast(0f)
                alpha = indicatorAlpha
            },
    ) {
        val backgroundColor = if (isVisible) colorResource(R.color.colorPrimary) else Color.Transparent
        val textColor = if (isVisible) colorResource(R.color.fast_scrollbar_text) else Color.Transparent

        Box(
            modifier = Modifier
                .defaultMinSize(
                    minHeight = INDICATOR_SIZE,
                    minWidth = INDICATOR_SIZE,
                )
                .graphicsLayer {
                    clip = true
                    shape = RoundedCornerShape(
                        topStartPercent = 50,
                        topEndPercent = 50,
                        bottomStartPercent = 50,
                        bottomEndPercent = 0,
                    )
                }
                .drawBehind { drawRect(backgroundColor) },
        )
        Text(
            text = textProvider(listPositionState.value),
            color = textColor,
            fontSize = 32.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .wrapContentHeight()
                .padding(end = IndicatorConstants.Default.PADDING)
                .width(IndicatorConstants.Default.MIN_HEIGHT),
        )
    }
}

@Composable
fun scrollbarStyle() = defaultMaterialScrollbarStyle().copy(
    thickness = 7.dp,
    trackStyle = TrackStyle(
        shape = RoundedCornerShape(4.dp),
        unhoverColor = Color.Transparent,
        hoverColor = Color.Transparent,
    ),
    thumbStyle = ThumbStyle(
        shape = RoundedCornerShape(4.dp),
        unhoverColor = colorResource(id = R.color.colorPrimary),
        hoverColor = colorResource(id = R.color.colorPrimary),
    ),
)
