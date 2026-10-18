package com.simplecityapps.shuttle.ui.common.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.simplecityapps.shuttle.designsystem.component.LocalDetailHeroTitleBottom
import com.simplecityapps.shuttle.designsystem.component.S2DetailTopBar
import com.simplecityapps.shuttle.designsystem.component.S2Scaffold
import com.simplecityapps.shuttle.ui.shell.RequestStatusBarOverImage
import com.simplecityapps.shuttle.ui.shell.adaptive.LocalShellLayout

/** With a [hero] the list starts behind the bar, so the bar's title appears only once the hero scrolls under it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScaffold(
    title: String,
    subtitle: String?,
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    hero: (@Composable (topInset: Dp, columnHeight: Dp) -> Unit)? = null,
    heroBleeds: Boolean = false,
    actions: (@Composable RowScope.() -> Unit)? = null,
    overlay: (@Composable BoxScope.(topInset: Dp) -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    var topBarHeightPx by remember { mutableIntStateOf(0) }
    // At compact height the hero sits beside the list, so the bar is opaque above both and nothing is behind the status bar.
    val twoColumns = hero != null && LocalShellLayout.current.compactHeight
    var topBarBottomPx by remember { mutableFloatStateOf(0f) }
    var heroTitleBottomPx by remember { mutableFloatStateOf(Float.MAX_VALUE) }
    val collapsed by remember(listState, hero != null, twoColumns) {
        derivedStateOf {
            // The bar covers the top of the list itself rather than sitting above it; its title takes over once the hero's has gone under.
            hero == null || twoColumns || heroTitleBottomPx <= topBarBottomPx || listState.isScrolledPast(0, obscuredPx = topBarHeightPx)
        }
    }
    // Opaque from the first scrolled pixel, so the hero is clipped below the bar and status bar rather than drawn through them.
    val filled by remember(listState) { derivedStateOf { collapsed || listState.canScrollBackward } }

    RequestStatusBarOverImage(heroBleeds && !filled)

    S2Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            S2DetailTopBar(
                title = title,
                subtitle = subtitle,
                collapsed = collapsed,
                onBack = onNavigateUp,
                actions = actions,
                modifier = Modifier.onGloballyPositioned {
                    topBarHeightPx = it.size.height
                    topBarBottomPx = it.boundsInWindow().bottom
                },
                scrollBehavior = scrollBehavior,
                overImage = heroBleeds,
                filled = filled,
            )
        },
    ) { innerPadding ->
        val layoutDirection = LocalLayoutDirection.current
        if (hero != null && twoColumns) {
            Row(Modifier.fillMaxSize().padding(innerPadding)) {
                BoxWithConstraints(Modifier.weight(HeroColumnWeight).fillMaxHeight()) {
                    val columnHeight = maxHeight
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { hero(0.dp, columnHeight) }
                }
                Box(Modifier.weight(1f - HeroColumnWeight).fillMaxHeight()) {
                    LazyColumn(modifier = Modifier.fillMaxSize(), state = listState) { content() }
                    overlay?.invoke(this, 0.dp)
                }
            }
            return@S2Scaffold
        }
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
                    item(key = "detail-hero", contentType = "hero") {
                        CompositionLocalProvider(LocalDetailHeroTitleBottom provides { heroTitleBottomPx = it }) {
                            hero(innerPadding.calculateTopPadding(), Dp.Unspecified)
                        }
                    }
                }
                content()
            }
            overlay?.invoke(this, innerPadding.calculateTopPadding())
        }
    }
}

private const val HeroColumnWeight = 0.4f

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
