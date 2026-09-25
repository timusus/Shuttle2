package com.simplecityapps.shuttle.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.simplecityapps.shuttle.designsystem.preview.S2Preview
import com.simplecityapps.shuttle.fixtures.SampleLibrary

/**
 * The one actions sheet for a song, album, artist or playlist: a `ModalBottomSheet` headed by the
 * target's [artwork], [title] and [subtitle], then its [actions]. Choosing an action runs it and
 * dismisses the sheet; a long list scrolls under the header.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun S2ActionsSheet(
    title: String,
    actions: List<S2Action>,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    artwork: (@Composable () -> Unit)? = null,
    sheetState: SheetState = rememberModalBottomSheetState(),
) {
    ModalBottomSheet(onDismissRequest = onDismissRequest, modifier = modifier, sheetState = sheetState) {
        ActionsSheetContent(title, actions, onDismissRequest, subtitle = subtitle, artwork = artwork)
    }
}

/** The body of an [S2ActionsSheet] without the sheet, for laying one out in place. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ActionsSheetContent(
    title: String,
    actions: List<S2Action>,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    artwork: (@Composable () -> Unit)? = null,
) {
    Column(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            artwork?.invoke()
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        HorizontalDivider(Modifier.padding(top = 8.dp))
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
        ) {
            actions.forEach { action ->
                val tint = if (action.destructive) MaterialTheme.colorScheme.error else Color.Unspecified
                ListItem(
                    onClick = {
                        action.onClick()
                        onDismissRequest()
                    },
                    leadingContent = action.icon?.let { icon -> { Icon(icon, contentDescription = null, tint = if (action.destructive) tint else MaterialTheme.colorScheme.onSurfaceVariant) } },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                ) {
                    Text(action.label, color = tint)
                }
            }
        }
    }
}

@Preview
@Composable
private fun ActionsSheetContentPreview() {
    val song = SampleLibrary.queue(1).single()
    S2Preview {
        Surface {
            ActionsSheetContent(
                title = song.title,
                subtitle = "${song.artist} · ${song.album}",
                artwork = { Artwork(ArtworkPlaceholder.Song, model = song) },
                actions = listOf(
                    S2Action("Play next", {}, Icons.Rounded.PlayArrow),
                    S2Action("Add to playlist", {}, Icons.AutoMirrored.Rounded.PlaylistAdd),
                    S2Action("Delete", {}, Icons.Rounded.Delete, destructive = true),
                ),
                onDismissRequest = {},
            )
        }
    }
}
