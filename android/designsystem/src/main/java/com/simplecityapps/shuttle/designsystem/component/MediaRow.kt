package com.simplecityapps.shuttle.designsystem.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.simplecityapps.shuttle.designsystem.theme.rowMeta

/**
 * The `ListItem` every library row shares: title `rowTitle` (`bodyLarge`) on `onSurface`, secondary
 * `rowSubtitle` (`bodyMedium`) on `onSurfaceVariant` (both the M3 defaults), meta `rowMeta`, selection
 * on `secondaryContainer`, and an optional overflow button. The unselected container is
 * transparent, so a row takes the colour of what it sits on: a screen, a sheet or the search view.
 * The text is styled so a row can show spans, such as a search query's matches in bold.
 * [edgeInset] is how far the row sits in from the screen edge (an inset selection container), so its overflow
 * button still lands on the shared keyline ([RowMoreButton]).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MediaRow(
    title: AnnotatedString,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: AnnotatedString? = null,
    meta: String? = null,
    leading: (@Composable () -> Unit)? = null,
    supportingLeading: (@Composable RowScope.() -> Unit)? = null,
    titleEmphasis: Boolean = false,
    selected: Boolean = false,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onMore: (() -> Unit)? = null,
    dragHandle: (@Composable () -> Unit)? = null,
    edgeInset: Dp = 0.dp,
    tabularMeta: Boolean = false,
) {
    ListItem(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        onLongClick = onLongClick,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = leading,
        supportingContent = supporting?.let {
            {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    supportingLeading?.invoke(this)
                    Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        },
        trailingContent = if (meta != null || onMore != null || dragHandle != null) {
            {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    meta?.let {
                        val style = MaterialTheme.typography.rowMeta
                        Text(it, style = if (tabularMeta) style.copy(fontFeatureSettings = "tnum") else style)
                    }
                    onMore?.let {
                        RowMoreButton(onClick = it, enabled = enabled, edgeInset = edgeInset)
                    }
                    dragHandle?.invoke()
                }
            }
        } else {
            null
        },
    ) {
        Text(
            text = title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (titleEmphasis) MaterialTheme.colorScheme.primary else Color.Unspecified,
        )
    }
}
