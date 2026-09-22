package com.simplecityapps.shuttle.ui.common.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Matches the shipped CollapsingToolbarLayout's `layout_collapseParallaxMultiplier`. */
private const val ParallaxMultiplier = 0.5f

/** Tall enough to sit behind the bar's icons with room to fade out. */
private val HeroScrimHeight = 112.dp

/**
 * Simple detail screen scaffold with a pinned small top app bar.
 *
 * Content is a single LazyColumn. The [hero] image, metadata, action buttons,
 * and track list are all regular list items that scroll naturally; the hero drifts at half the
 * scroll speed as it leaves, like the shipped collapsing toolbar's parallax.
 * The top app bar stays pinned with back navigation and overflow actions, and shows [title] and
 * [subtitle] once the item at [headerItemIndex] within [content] (the one carrying the page
 * title) has scrolled under it.
 *
 * With [heroBehindTopBar], the hero starts at the very top of the screen, behind a transparent
 * bar whose icons sit on a dark scrim; the bar takes its container colour when the title appears.
 * The status bar is only covered if window insets reach the composition; today the activity
 * root fits system windows, so the hero runs up to the bottom of the status bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScaffold(
    title: String,
    subtitle: String?,
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    headerItemIndex: Int = 0,
    hero: (@Composable () -> Unit)? = null,
    heroBehindTopBar: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    val headerListIndex = if (hero != null) headerItemIndex + 1 else headerItemIndex
    val overlayHero = heroBehindTopBar && hero != null
    var topBarHeightPx by remember { mutableIntStateOf(0) }
    val collapsed by remember(listState, headerListIndex, overlayHero) {
        derivedStateOf {
            // Over the hero the bar covers the top of the list itself rather than sitting above it.
            listState.isScrolledPast(headerListIndex, obscuredPx = if (overlayHero) topBarHeightPx else 0)
        }
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    AnimatedVisibility(
                        visible = collapsed,
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        DetailTopBarTitle(title = title, subtitle = subtitle)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate up",
                        )
                    }
                },
                actions = actions,
                modifier = Modifier.onSizeChanged { topBarHeightPx = it.height },
                colors = if (overlayHero) overlayTopBarColors(collapsed) else TopAppBarDefaults.topAppBarColors(),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        val layoutDirection = LocalLayoutDirection.current
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = if (overlayHero) {
                PaddingValues(
                    start = innerPadding.calculateStartPadding(layoutDirection),
                    end = innerPadding.calculateEndPadding(layoutDirection),
                    bottom = innerPadding.calculateBottomPadding(),
                )
            } else {
                innerPadding
            },
        ) {
            if (hero != null) {
                item(contentType = "hero") {
                    ParallaxHero(listState = listState, showScrim = overlayHero, content = hero)
                }
            }
            content()
        }
    }
}

/**
 * Translates [content] down by half of the list's scroll offset while it is the first visible
 * item, clipped to its own bounds.
 *
 * The scroll offset is read inside the graphics layer block, so scrolling only re-runs the layer
 * rather than recomposing the hero.
 */
@Composable
private fun ParallaxHero(
    listState: LazyListState,
    showScrim: Boolean,
    content: @Composable () -> Unit,
) {
    Box(modifier = Modifier.clipToBounds()) {
        Box(
            modifier = Modifier.graphicsLayer {
                translationY = if (listState.firstVisibleItemIndex == 0) {
                    listState.firstVisibleItemScrollOffset * ParallaxMultiplier
                } else {
                    0f
                }
            },
        ) {
            content()
        }
        if (showScrim) {
            // Held at the top of the viewport, behind the pinned bar icons, until the hero scrolls out.
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        translationY = if (listState.firstVisibleItemIndex == 0) {
                            listState.firstVisibleItemScrollOffset.toFloat()
                        } else {
                            0f
                        }
                    }.fillMaxWidth()
                    .height(HeroScrimHeight)
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent))),
            )
        }
    }
}

/**
 * Transparent with light icons over the hero's scrim, easing to the regular bar colours once the
 * title shows.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun overlayTopBarColors(collapsed: Boolean): TopAppBarColors {
    val defaults = TopAppBarDefaults.topAppBarColors()
    val containerColor by animateColorAsState(
        targetValue = if (collapsed) defaults.containerColor else Color.Transparent,
        label = "topBarContainer",
    )
    val navigationIconColor by animateColorAsState(
        targetValue = if (collapsed) defaults.navigationIconContentColor else Color.White,
        label = "topBarNavigationIcon",
    )
    val actionIconColor by animateColorAsState(
        targetValue = if (collapsed) defaults.actionIconContentColor else Color.White,
        label = "topBarActionIcon",
    )
    return TopAppBarDefaults.topAppBarColors(
        containerColor = containerColor,
        // The pinned scroll behaviour would otherwise swap in its own colour as soon as the list moves.
        scrolledContainerColor = containerColor,
        navigationIconContentColor = navigationIconColor,
        actionIconContentColor = actionIconColor,
    )
}

@Composable
private fun DetailTopBarTitle(
    title: String,
    subtitle: String?,
) {
    Column(modifier = Modifier.testTag("detail-top-bar-title")) {
        Text(
            text = title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * True once the item at [index] has scrolled entirely above [obscuredPx] from the top of the list's
 * content area: zero when the top bar's height is applied as content padding, the bar's height when
 * the bar overlays the list.
 */
private fun LazyListState.isScrolledPast(
    index: Int,
    obscuredPx: Int,
): Boolean {
    if (firstVisibleItemIndex > index) return true
    val item = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return false
    return item.offset + item.size <= obscuredPx
}
