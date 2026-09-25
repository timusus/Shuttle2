package com.simplecityapps.shuttle.designsystem.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
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
import androidx.compose.ui.text.style.TextOverflow
import com.simplecityapps.shuttle.designsystem.R

/**
 * The `ListItem` every library row shares: title `bodyLarge` on `onSurface`, secondary
 * `bodyMedium` on `onSurfaceVariant` (both from the M3 defaults), meta `labelMedium`, selection
 * on `secondaryContainer`, and an optional overflow button. The unselected container is
 * transparent, so a row takes the colour of what it sits on: a screen, a sheet or the search view.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MediaRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    meta: String? = null,
    leading: (@Composable () -> Unit)? = null,
    supportingLeading: (@Composable RowScope.() -> Unit)? = null,
    titleEmphasis: Boolean = false,
    selected: Boolean = false,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onMore: (() -> Unit)? = null,
    dragHandle: (@Composable () -> Unit)? = null,
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
                    meta?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
                    onMore?.let {
                        S2IconButton(
                            icon = Icons.Rounded.MoreVert,
                            contentDescription = stringResource(R.string.ds_more_options),
                            onClick = it,
                            enabled = enabled,
                        )
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
