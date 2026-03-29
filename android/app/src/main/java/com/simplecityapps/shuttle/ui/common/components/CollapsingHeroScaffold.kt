package com.simplecityapps.shuttle.ui.common.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val ToolbarHeight = 56.dp

/**
 * A scaffold that replicates the old CoordinatorLayout collapsing toolbar pattern.
 *
 * Three overlay layers in a Box:
 * 1. LazyColumn (bottom) — content with top padding = heroHeight + toolbarHeight
 * 2. Hero image (middle) — fixed at y=0, parallax via graphicsLayer, fades out
 * 3. Metadata toolbar (top) — starts at y=heroHeight, moves up with scroll, pins at y=0
 *
 * The LazyColumn scrolls freely. No NestedScrollConnection. The hero and toolbar
 * positions are derived from observing LazyListState.
 */
@Composable
fun CollapsingHeroScaffold(
    heroContent: @Composable (collapseProgress: Float) -> Unit,
    title: String,
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    heroHeight: Dp = 300.dp,
    content: LazyListScope.() -> Unit,
) {
    val context = LocalContext.current
    val windowBackground = remember(context) {
        val typedArray = context.obtainStyledAttributes(intArrayOf(android.R.attr.windowBackground))
        val color = typedArray.getColor(0, android.graphics.Color.BLACK)
        typedArray.recycle()
        Color(color)
    }

    val density = LocalDensity.current
    val heroHeightPx = with(density) { heroHeight.toPx() }
    val toolbarHeightPx = with(density) { ToolbarHeight.toPx() }
    val totalHeaderPx = heroHeightPx + toolbarHeightPx

    val lazyListState = rememberLazyListState()

    // Total scroll in pixels, derived from the first visible item's offset.
    // contentPadding puts item 0 at y=totalHeaderPx initially; as user scrolls,
    // item 0 moves up and its offset decreases.
    val scrollPx by remember {
        derivedStateOf {
            val firstItem = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull()
            if (firstItem != null && firstItem.index == 0) {
                (totalHeaderPx - firstItem.offset).coerceAtLeast(0f)
            } else {
                // All content padding scrolled off — hero + toolbar fully collapsed
                totalHeaderPx
            }
        }
    }

    val collapseProgress by remember {
        derivedStateOf {
            (scrollPx / heroHeightPx).coerceIn(0f, 1f)
        }
    }

    // Toolbar Y: starts at heroHeight, pins at 0
    val toolbarYPx by remember {
        derivedStateOf {
            (heroHeightPx - scrollPx).coerceAtLeast(0f)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Layer 1: LazyColumn — content padded below hero + toolbar
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = heroHeight + ToolbarHeight),
        ) {
            content()
        }

        // Layer 2: Hero image — fixed at top, parallax + fade
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(heroHeight)
                .graphicsLayer {
                    translationY = -scrollPx * 0.5f
                    alpha = 1f - collapseProgress
                },
        ) {
            heroContent(collapseProgress)
        }

        // Layer 3: Metadata toolbar — tracks bottom of hero, pins at y=0
        MetadataBar(
            title = title,
            subtitle = subtitle,
            onNavigateUp = onNavigateUp,
            actions = actions,
            backgroundColor = windowBackground,
            modifier = Modifier.graphicsLayer {
                translationY = toolbarYPx
            },
        )
    }
}

@Composable
private fun MetadataBar(
    title: String,
    subtitle: String?,
    onNavigateUp: () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(ToolbarHeight)
            .drawBehind { drawRect(backgroundColor) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onNavigateUp) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Navigate up",
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
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

        actions()
    }
}
