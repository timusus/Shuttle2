package com.simplecityapps.shuttle.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.simplecityapps.shuttle.designsystem.R
import com.simplecityapps.shuttle.designsystem.theme.S2Theme

/** Where a song sits in the queue relative to the current one. */
enum class QueuePosition { Played, Current, Upcoming }

/**
 * A song in the queue with a trailing drag handle. The caller's reorder library attaches its
 * gesture through [dragHandleModifier]. [QueuePosition.Current] marks the playing song like
 * [SongRow] does; [QueuePosition.Played] dims songs already heard. [dragging] lifts the row onto
 * a `surfaceContainerHigh` card with a shadow while it moves.
 */
@Composable
fun QueueRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    position: QueuePosition = QueuePosition.Upcoming,
    artwork: (@Composable () -> Unit)? = null,
    duration: String? = null,
    dragging: Boolean = false,
    dragHandleModifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    val current = position == QueuePosition.Current
    Surface(
        modifier = modifier,
        color = if (dragging) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
        shadowElevation = if (dragging) 6.dp else 0.dp,
        shape = if (dragging) MaterialTheme.shapes.medium else RectangleShape,
    ) {
        MediaRow(
            title = title,
            onClick = onClick,
            modifier = if (position == QueuePosition.Played && !dragging) Modifier.alpha(0.6f) else Modifier,
            supporting = subtitle,
            meta = duration,
            leading = artwork,
            supportingLeading = if (current) {
                { SupportingIcon(Icons.Rounded.GraphicEq, stringResource(R.string.ds_now_playing), MaterialTheme.colorScheme.primary) }
            } else {
                null
            },
            titleEmphasis = current,
            onLongClick = onLongClick,
            dragHandle = {
                Box(dragHandleModifier.size(48.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.DragHandle, contentDescription = stringResource(R.string.ds_reorder))
                }
            },
        )
    }
}

@Preview
@Composable
private fun QueueRowPreview() {
    S2Theme {
        QueueRow("Route 29, Outbound", "Juniper Static", onClick = {}, position = QueuePosition.Current, duration = "4:44", artwork = { Artwork(ArtworkPlaceholder.Song) })
    }
}
