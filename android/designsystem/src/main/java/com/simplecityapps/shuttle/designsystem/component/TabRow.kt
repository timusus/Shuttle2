package com.simplecityapps.shuttle.designsystem.component

import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabIndicatorScope
import androidx.compose.material3.TabPosition
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.lerp
import com.simplecityapps.shuttle.designsystem.preview.S2Preview
import com.simplecityapps.shuttle.designsystem.theme.S2Spacing

const val TabIndicatorTestTag = "s2-tab-indicator"

/**
 * Peer sections of one screen, one selected (Library's Songs/Albums/...): a scrolling primary tab row that keeps the
 * selected tab in view. Tabs are single-line [labels]. The indicator slides as [selectedIndex] changes, or, when
 * [indicatorPosition] is given, follows that fractional tab position (a pager's `currentPage + currentPageOffsetFraction`)
 * so it tracks a drag.
 */
@Composable
fun S2TabRow(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    indicatorPosition: (() -> Float)? = null,
) {
    PrimaryScrollableTabRow(
        selectedTabIndex = selectedIndex,
        modifier = modifier,
        edgePadding = S2Spacing.medium,
        indicator = {
            val offset = if (indicatorPosition == null) {
                Modifier.tabIndicatorOffset(selectedIndex, matchContentSize = true)
            } else {
                fractionalTabIndicator(indicatorPosition)
            }
            TabRowDefaults.PrimaryIndicator(modifier = offset.testTag(TabIndicatorTestTag), width = Dp.Unspecified)
        },
    ) {
        labels.forEachIndexed { index, label ->
            Tab(
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                text = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        }
    }
}

/** Sizes and places the indicator on the content span interpolated between the two tabs [position] sits between. */
private fun TabIndicatorScope.fractionalTabIndicator(position: () -> Float): Modifier = Modifier.tabIndicatorLayout { measurable, constraints, tabPositions ->
    val last = tabPositions.lastIndex
    if (last < 0) return@tabIndicatorLayout layout(constraints.maxWidth, constraints.maxHeight) {}
    val at = position().coerceIn(0f, last.toFloat())
    val from = tabPositions[at.toInt()]
    val to = tabPositions[minOf(at.toInt() + 1, last)]
    val fraction = at - at.toInt()
    val left = lerp(from.left, to.left, fraction).roundToPx()
    val width = lerp(from.contentWidth, to.contentWidth, fraction).roundToPx()
    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
    layout(constraints.maxWidth, constraints.maxHeight) {
        placeable.placeRelative(left, constraints.maxHeight - placeable.height)
    }
}

@Preview
@Composable
private fun TabRowPreview() {
    S2Preview {
        S2TabRow(labels = listOf("Songs", "Albums", "Artists", "Playlists", "Genres"), selectedIndex = 1, onSelect = {})
    }
}
