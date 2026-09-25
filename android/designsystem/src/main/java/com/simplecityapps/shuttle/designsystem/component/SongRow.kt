package com.simplecityapps.shuttle.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.simplecityapps.shuttle.designsystem.R
import com.simplecityapps.shuttle.designsystem.preview.S2Preview
import com.simplecityapps.shuttle.fixtures.SampleLibrary

/** Offline state of a remote song, shown ahead of the secondary line. */
enum class SongOfflineState { None, Downloading, Offline }

/**
 * A song in a list. Leading is the [artwork] slot, or the [trackNumber] on an album's track list.
 * [playing] marks the current song; [enabled] false greys out a song whose file is missing.
 */
@Composable
fun SongRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    artwork: (@Composable () -> Unit)? = null,
    trackNumber: Int? = null,
    duration: String? = null,
    playing: Boolean = false,
    selected: Boolean = false,
    enabled: Boolean = true,
    offlineState: SongOfflineState = SongOfflineState.None,
    onLongClick: (() -> Unit)? = null,
    onMore: (() -> Unit)? = null,
) {
    MediaRow(
        title = title,
        onClick = onClick,
        modifier = modifier,
        supporting = subtitle,
        meta = duration,
        leading = when {
            artwork != null -> artwork

            trackNumber != null -> {
                { TrackNumber(trackNumber, playing) }
            }

            else -> null
        },
        supportingLeading = if (playing || offlineState != SongOfflineState.None) {
            {
                if (playing) SupportingIcon(Icons.Rounded.GraphicEq, stringResource(R.string.ds_now_playing), MaterialTheme.colorScheme.primary)
                when (offlineState) {
                    SongOfflineState.None -> Unit
                    SongOfflineState.Downloading -> SupportingIcon(Icons.Rounded.Downloading, stringResource(R.string.ds_downloading))
                    SongOfflineState.Offline -> SupportingIcon(Icons.Rounded.DownloadDone, stringResource(R.string.ds_available_offline))
                }
            }
        } else {
            null
        },
        titleEmphasis = playing,
        selected = selected,
        enabled = enabled,
        onLongClick = onLongClick,
        onMore = onMore,
    )
}

@Composable
private fun TrackNumber(trackNumber: Int, playing: Boolean) {
    Box(Modifier.size(ArtworkSize.Small.dp), contentAlignment = Alignment.Center) {
        Text(
            text = trackNumber.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun SupportingIcon(icon: ImageVector, contentDescription: String, tint: Color = LocalContentColor.current) {
    Icon(icon, contentDescription, Modifier.size(16.dp), tint = tint)
    Spacer(Modifier.width(4.dp))
}

@Preview
@Composable
private fun SongRowPreview() {
    val song = SampleLibrary.queue(1).single()
    S2Preview {
        SongRow(
            title = song.title,
            subtitle = "${song.artist} · ${song.album}",
            onClick = {},
            artwork = { Artwork(ArtworkPlaceholder.Song, size = ArtworkSize.Small, model = song) },
            duration = song.duration,
            playing = true,
            onMore = {},
        )
    }
}
