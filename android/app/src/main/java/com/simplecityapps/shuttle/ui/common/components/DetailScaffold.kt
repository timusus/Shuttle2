package com.simplecityapps.shuttle.ui.common.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import com.simplecityapps.shuttle.designsystem.component.S2DetailTopBar
import com.simplecityapps.shuttle.designsystem.component.S2Scaffold

/**
 * A detail screen: a single LazyColumn under a pinned [S2DetailTopBar] with back navigation and the overflow [actions].
 *
 * With a [hero] (a `DetailHero` carrying the page title), the list starts at the top of the screen, behind the bar,
 * and the hero is its first item, given the bar's height as its top inset so its wash runs up behind the bar's icons.
 * It scrolls with the list: no parallax, no scrim. The bar shows [title] and [subtitle] once the hero has scrolled
 * under it. Without one (loading, not found), the list sits below the bar and the bar shows the title throughout.
 * [heroBleeds]: the hero is a `DetailBleedHero`, whose image runs up behind the bar, so the bar's icons are white over it.
 * [overlay] draws over the list, under the bar, given the bar's height (an artist page's pinned album header, #631).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScaffold(
    title: String,
    subtitle: String?,
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    hero: (@Composable (topInset: Dp) -> Unit)? = null,
    heroBleeds: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    overlay: (@Composable BoxScope.(topInset: Dp) -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    var topBarHeightPx by remember { mutableIntStateOf(0) }
    val collapsed by remember(listState, hero != null) {
        derivedStateOf {
            // The bar covers the top of the list itself rather than sitting above it.
            hero == null || listState.isScrolledPast(0, obscuredPx = topBarHeightPx)
        }
    }

    S2Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            S2DetailTopBar(
                title = title,
                subtitle = subtitle,
                collapsed = collapsed,
                onBack = onNavigateUp,
                actions = actions,
                modifier = Modifier.onSizeChanged { topBarHeightPx = it.height },
                scrollBehavior = scrollBehavior,
                overImage = heroBleeds,
            )
        },
    ) { innerPadding ->
        val layoutDirection = LocalLayoutDirection.current
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = if (hero != null) {
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
                    item(key = "detail-hero", contentType = "hero") { hero(innerPadding.calculateTopPadding()) }
                }
                content()
            }
            overlay?.invoke(this, innerPadding.calculateTopPadding())
        }
    }
}

/**
 * True once the item at [index] has scrolled entirely above [obscuredPx] from the top of the list's content area: the
 * bar's height, since the bar overlays the list.
 */
private fun LazyListState.isScrolledPast(
    index: Int,
    obscuredPx: Int,
): Boolean {
    if (firstVisibleItemIndex > index) return true
    val item = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return false
    return item.offset + item.size <= obscuredPx
}
