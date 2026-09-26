package com.simplecityapps.shuttle.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.simplecityapps.shuttle.designsystem.R
import com.simplecityapps.shuttle.designsystem.preview.S2Preview
import com.simplecityapps.shuttle.fixtures.SampleLibrary

/**
 * The collapsed player above the nav bar (compact) or docked under the content (expanded): the
 * song's [artwork], [title] and [subtitle] ("artist • album"), a small morphing
 * [S2PlayPauseButton], skip next, and the [S2PlaybackProgress] wave along the bottom edge.
 * [buffering] shows the play button's `LoadingIndicator` and an indeterminate wave. Tapping the
 * rest of the bar ([onClick]) expands the player.
 */
@Composable
fun S2MiniPlayer(
    title: String,
    subtitle: String,
    playing: Boolean,
    progress: () -> Float,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    artwork: (@Composable () -> Unit)? = null,
    buffering: Boolean = false,
    nextIcon: ImageVector = Icons.Rounded.SkipNext,
    nextContentDescription: String = stringResource(R.string.ds_next),
    /** Holding the next button repeats this instead of calling [onNext] (1.0.10's `SkipButton`). */
    onNextHold: (() -> Unit)? = null,
) {
    Surface(onClick = onClick, modifier = modifier, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                artwork?.invoke()
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                S2PlayPauseButton(playing, onPlayPause, buffering = buffering, size = 44.dp)
                S2TransportButton(nextIcon, nextContentDescription, onNext, onHold = onNextHold)
            }
            S2PlaybackProgress(
                progress = if (buffering) null else progress,
                playing = playing,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 6.dp),
            )
        }
    }
}

@Preview
@Composable
private fun S2MiniPlayerPreview() {
    val song = SampleLibrary.queue(1).single()
    S2Preview {
        S2MiniPlayer(
            title = song.title,
            subtitle = "${song.artist} • ${song.album}",
            playing = true,
            progress = { 0.35f },
            onPlayPause = {},
            onNext = {},
            onClick = {},
            artwork = { Artwork(ArtworkPlaceholder.Song, model = song) },
        )
    }
}
