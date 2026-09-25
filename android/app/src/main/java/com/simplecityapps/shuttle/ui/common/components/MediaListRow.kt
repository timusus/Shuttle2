package com.simplecityapps.shuttle.ui.common.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The row shared by every library list screen: an optional leading slot (artwork or an icon), a
 * title/subtitle column that carries the click handling, and a trailing slot (the overflow menu).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaListRow(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    subtitle: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(start = 8.dp),
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        Column(
            modifier = Modifier
                .padding(contentPadding)
                .weight(1f)
                .let {
                    when {
                        onLongClick != null -> it.combinedClickable(onClick = onClick ?: {}, onLongClick = onLongClick)
                        onClick != null -> it.clickable(onClick = onClick)
                        else -> it
                    }
                },
        ) {
            title()
            subtitle?.invoke()
        }
        trailing?.invoke()
    }
}
