package com.simplecityapps.shuttle.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Downloading
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.simplecityapps.shuttle.designsystem.R
import com.simplecityapps.shuttle.designsystem.preview.S2Preview
import com.simplecityapps.shuttle.designsystem.theme.S2TouchTarget
import com.simplecityapps.shuttle.fixtures.SampleLibrary
import com.simplecityapps.shuttle.ui.shell.player.QueuePosition

/**
 * A song in the queue with a trailing drag handle. The caller's reorder library attaches its
 * gesture through [dragHandleModifier]. [QueuePosition.Current] marks the playing song like
 * [SongRow] does and also sits on a `secondaryContainer` card, so it stands out from the rows around
 * it; [QueuePosition.Played] dims songs already heard. [dragging] lifts the row onto a
 * `surfaceContainerHigh` card with a shadow while it moves.
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
    offlineState: SongOfflineState = SongOfflineState.None,
    onLongClick: (() -> Unit)? = null,
) {
    val current = position == QueuePosition.Current
    Surface(
        modifier = modifier,
        color = when {
            dragging -> MaterialTheme.colorScheme.surfaceContainerHigh
            current -> MaterialTheme.colorScheme.secondaryContainer
            else -> Color.Transparent
        },
        shadowElevation = if (dragging) 6.dp else 0.dp,
        shape = if (dragging || current) MaterialTheme.shapes.medium else RectangleShape,
    ) {
        MediaRow(
            title = AnnotatedString(title),
            onClick = onClick,
            modifier = if (position == QueuePosition.Played && !dragging) Modifier.alpha(0.6f) else Modifier,
            supporting = AnnotatedString(subtitle),
            meta = duration,
            leading = artwork,
            // On the current row's secondaryContainer, primary isn't guaranteed 3:1 under every dynamic scheme.
            supportingLeading = if (current || offlineState != SongOfflineState.None) {
                {
                    if (current) SupportingIcon(Icons.Rounded.GraphicEq, stringResource(R.string.ds_now_playing), MaterialTheme.colorScheme.onSecondaryContainer)
                    when (offlineState) {
                        SongOfflineState.None -> Unit
                        SongOfflineState.Downloading -> SupportingIcon(Icons.Rounded.Downloading, stringResource(R.string.ds_downloading))
                        SongOfflineState.Offline -> SupportingIcon(Icons.Rounded.DownloadDone, stringResource(R.string.ds_available_offline))
                    }
                }
            } else {
                null
            },
            titleEmphasis = current,
            onLongClick = onLongClick,
            dragHandle = {
                Box(dragHandleModifier.size(S2TouchTarget.minimum), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.DragHandle, contentDescription = stringResource(R.string.ds_reorder))
                }
            },
        )
    }
}

@Preview
@Composable
private fun QueueRowPreview() {
    val song = SampleLibrary.queue(1).single()
    S2Preview {
        QueueRow(song.title, song.artist, onClick = {}, position = QueuePosition.Current, duration = song.duration, artwork = { Artwork(ArtworkPlaceholder.Song, model = song) })
    }
}
