package com.simplecityapps.shuttle.ui.common.components

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A scaffold with a collapsing hero image area. As the user scrolls content up,
 * the hero image collapses with a parallax effect and fades out. A toolbar pins
 * at the top, with its title fading in as the hero collapses.
 *
 * @param heroContent Content rendered in the hero area. Receives collapse progress (0f = expanded, 1f = collapsed).
 * @param title Toolbar title — fades in as the hero collapses.
 * @param subtitle Optional toolbar subtitle — fades in with the title.
 * @param onNavigateUp Back button callback.
 * @param actions Toolbar action buttons (overflow menu, etc.).
 * @param heroHeight Expanded height of the hero area.
 * @param content LazyListScope for the scrollable body below the hero.
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
    val density = LocalDensity.current
    val heroHeightPx = with(density) { heroHeight.toPx() }

    // How far the hero has collapsed. 0f = fully expanded, -heroHeightPx = fully collapsed.
    var heroOffset by remember { mutableFloatStateOf(0f) }

    val nestedScrollConnection = remember(heroHeightPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                val newOffset = (heroOffset + delta).coerceIn(-heroHeightPx, 0f)
                val consumed = newOffset - heroOffset
                heroOffset = newOffset
                return Offset(0f, consumed)
            }
        }
    }

    val collapseProgress = if (heroHeightPx > 0f) {
        (-heroOffset / heroHeightPx).coerceIn(0f, 1f)
    } else {
        0f
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection),
    ) {
        // 1. Hero image — parallax (scrolls at half speed) and fades out
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
        }

        // 2. Scrollable content — padded below the hero
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = heroHeight),
        ) {
            content()
        }

        // 3. Pinned toolbar — background fades in as hero collapses
        TopAppBar(
            title = {
                if (subtitle != null) {
                    // Two-line title + subtitle
                    Box {
                        Text(
                            text = title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.graphicsLayer { alpha = collapseProgress },
                        )
                    }
                } else {
                    Text(
                        text = title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.graphicsLayer { alpha = collapseProgress },
                    )
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
                containerColor = MaterialTheme.colorScheme.surface.copy(
                    alpha = collapseProgress,
                ),
                navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                actionIconContentColor = MaterialTheme.colorScheme.onSurface,
            ),
        )
    }
}
