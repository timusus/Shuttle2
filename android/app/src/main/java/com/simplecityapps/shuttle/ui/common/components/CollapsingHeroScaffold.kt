package com.simplecityapps.shuttle.ui.common.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Detail screen scaffold with a collapsing hero image.
 *
 * All content is in a single LazyColumn:
 * - Item 0: Hero image (with parallax)
 * - Item 1: Metadata bar (back arrow, title, subtitle, overflow)
 * - Item 2+: Screen content (songs, albums, etc.)
 *
 * A pinned TopAppBar overlay appears when the metadata bar scrolls off screen.
 */
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
    val context = LocalContext.current
    val windowBackground = remember(context) {
        val typedArray = context.obtainStyledAttributes(intArrayOf(android.R.attr.windowBackground))
        val color = typedArray.getColor(0, android.graphics.Color.BLACK)
        typedArray.recycle()
        Color(color)
    }

    val density = LocalDensity.current
    val heroHeightPx = with(density) { heroHeight.toPx() }

    val lazyListState = rememberLazyListState()

    // Hero collapse progress for parallax/fade
    val collapseProgress by remember {
        derivedStateOf {
            if (lazyListState.firstVisibleItemIndex > 0) {
                1f
            } else {
                (lazyListState.firstVisibleItemScrollOffset / heroHeightPx).coerceIn(0f, 1f)
            }
        }
    }

    // Pinned toolbar shows when the metadata bar (item 1) has scrolled off
    val showPinnedToolbar by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex >= 2
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize(),
        ) {
            // Item 0: Hero image
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(heroHeight)
                        .graphicsLayer {
                            alpha = 1f - collapseProgress
                            if (lazyListState.firstVisibleItemIndex == 0) {
                                translationY = lazyListState.firstVisibleItemScrollOffset * 0.5f
                            }
                        },
                ) {
                    heroContent(collapseProgress)
                }
            }

            // Item 1: Metadata bar (back, title, subtitle, overflow)
            item {
                MetadataBar(
                    title = title,
                    subtitle = subtitle,
                    onNavigateUp = onNavigateUp,
                    actions = actions,
                )
            }

            // Item 2+: Screen content
            content()
        }

        // Pinned toolbar — only when metadata bar has scrolled off
        if (showPinnedToolbar) {
            TopAppBar(
                title = {
                    Column {
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = windowBackground,
                    scrolledContainerColor = windowBackground,
                ),
            )
        }
    }
}

@Composable
private fun MetadataBar(
    title: String,
    subtitle: String?,
    onNavigateUp: () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
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
