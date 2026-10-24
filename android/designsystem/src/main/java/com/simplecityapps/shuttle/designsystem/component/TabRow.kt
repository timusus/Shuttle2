package com.simplecityapps.shuttle.designsystem.component

import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.simplecityapps.shuttle.designsystem.preview.S2Preview
import com.simplecityapps.shuttle.designsystem.theme.S2Spacing

/**
 * Peer sections of one screen, one selected (Library's Songs/Albums/...): a scrolling primary tab row that keeps the
 * selected tab in view and slides its indicator as [selectedIndex] changes. Tabs are single-line [labels].
 */
@Composable
fun S2TabRow(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    PrimaryScrollableTabRow(
        selectedTabIndex = selectedIndex,
        modifier = modifier,
        edgePadding = S2Spacing.medium,
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

@Preview
@Composable
private fun TabRowPreview() {
    S2Preview {
        S2TabRow(labels = listOf("Songs", "Albums", "Artists", "Playlists", "Genres"), selectedIndex = 1, onSelect = {})
    }
}
