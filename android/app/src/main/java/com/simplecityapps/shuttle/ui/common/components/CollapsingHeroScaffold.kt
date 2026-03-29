package com.simplecityapps.shuttle.ui.common.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val ToolbarHeight = 64.dp

@OptIn(ExperimentalMaterial3Api::class)
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
    val density = LocalDensity.current
    val heroHeightPx = with(density) { heroHeight.toPx() }
    val toolbarHeightPx = with(density) { ToolbarHeight.toPx() }
    // Hero collapses down to the toolbar height, not to zero
    val maxCollapsePx = heroHeightPx - toolbarHeightPx

    var heroOffset by remember { mutableFloatStateOf(0f) }

    val nestedScrollConnection = remember(maxCollapsePx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                val newOffset = (heroOffset + delta).coerceIn(-maxCollapsePx, 0f)
                val consumed = newOffset - heroOffset
                heroOffset = newOffset
                return Offset(0f, consumed)
            }
        }
    }

    val collapseProgress = if (maxCollapsePx > 0f) {
        (-heroOffset / maxCollapsePx).coerceIn(0f, 1f)
    } else {
        0f
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection),
    ) {
        // 1. Hero image — parallax and fade
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(heroHeight)
                .graphicsLayer {
                    translationY = heroOffset * 0.5f
                    alpha = 1f - collapseProgress
                },
        ) {
            heroContent(collapseProgress)
            // Gradient scrim at top for toolbar icon visibility
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ToolbarHeight * 1.5f)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.5f),
                                Color.Transparent,
                            ),
                        ),
                    ),
            )
        }

        // 2. Content — padded below hero + toolbar, moves up with hero
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = heroOffset },
            contentPadding = PaddingValues(top = heroHeight),
        ) {
            content()
        }

        // 3. Pinned toolbar — background fades in
        TopAppBar(
            title = {
                Text(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.graphicsLayer { alpha = collapseProgress },
                )
            },
            navigationIcon = {
                IconButton(onClick = onNavigateUp) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Navigate up",
                        tint = Color.White,
                    )
                }
            },
            actions = actions,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = if (collapseProgress > 0.9f) {
                    MaterialTheme.colorScheme.surface
                } else {
                    Color.Transparent
                },
                scrolledContainerColor = MaterialTheme.colorScheme.surface,
                navigationIconContentColor = Color.White,
                actionIconContentColor = Color.White,
            ),
        )
    }
}
