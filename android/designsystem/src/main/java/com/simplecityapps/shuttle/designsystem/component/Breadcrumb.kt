package com.simplecityapps.shuttle.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.simplecityapps.shuttle.designsystem.preview.S2Preview
import com.simplecityapps.shuttle.designsystem.theme.S2Spacing
import com.simplecityapps.shuttle.designsystem.theme.S2TouchTarget
import com.simplecityapps.shuttle.designsystem.theme.rowSubtitle

/**
 * A path as a row of [segments], first to last, separated by chevrons. Every segment but the last is tappable
 * ([onSegmentClick] gets its index); the last is the current location, emphasised. It scrolls sideways, parked at
 * the end so the current segment is always in view.
 */
@Composable
fun S2Breadcrumb(
    segments: List<String>,
    onSegmentClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    LaunchedEffect(segments) { scrollState.scrollTo(scrollState.maxValue) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .horizontalScroll(scrollState)
            .padding(horizontal = S2Spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        segments.forEachIndexed { index, segment ->
            val current = index == segments.lastIndex
            if (index > 0) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                text = segment,
                style = MaterialTheme.typography.rowSubtitle,
                fontWeight = if (current) FontWeight.Bold else null,
                color = if (current) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .heightIn(min = S2TouchTarget.minimum)
                    .then(if (current) Modifier.semantics { heading() } else Modifier.clickable { onSegmentClick(index) })
                    .padding(horizontal = S2Spacing.small)
                    .wrapContentHeight(Alignment.CenterVertically),
            )
        }
    }
}

@Preview
@Composable
private fun S2BreadcrumbPreview() {
    S2Preview {
        S2Breadcrumb(segments = listOf("Folders", "Music", "Juniper Static"), onSegmentClick = {})
    }
}
