package com.simplecityapps.shuttle.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simplecityapps.shuttle.designsystem.R
import com.simplecityapps.shuttle.designsystem.preview.S2Preview
import com.simplecityapps.shuttle.designsystem.theme.S2IconSize
import com.simplecityapps.shuttle.designsystem.theme.S2Spacing
import com.simplecityapps.shuttle.designsystem.theme.artworkRole
import com.simplecityapps.shuttle.fixtures.SampleLibrary

/** The mini player's height at the default text size: the shell docks the player sheet's Mini level this far above the nav bar. */
private val MiniPlayerBaseHeight = 72.dp

/** The two text lines' height at the default text size (title and subtitle line heights, 20sp and 16sp). */
private val MiniPlayerTextLines = 36.sp

/**
 * The mini player's height: [MiniPlayerBaseHeight], plus whatever its two text lines grow by at the
 * user's text size, so the artist line isn't cut at 200% text. The shell docks the player sheet's Mini
 * level this far above the nav bar.
 */
@Composable
@ReadOnlyComposable
fun s2MiniPlayerHeight(): Dp {
    val textGrowth = with(LocalDensity.current) { MiniPlayerTextLines.toDp() - MiniPlayerTextLines.value.dp }
    return MiniPlayerBaseHeight + textGrowth.coerceAtLeast(0.dp)
}

/**
 * The collapsed player above the nav bar (compact) or docked under the content (expanded): the
 * song's [artwork], [title] and [subtitle] ("artist • album"), a plain [S2PlayPauseIconButton]
 * (#738), skip next, and the [S2PlaybackProgress] wave along the bottom edge. [buffering] shows the
 * play button's `LoadingIndicator` and an indeterminate wave. While [castingTo] names a Cast device,
 * the subtitle says the song is playing there; the app doesn't pass it yet (#795). Tapping the rest
 * of the bar ([onClick]) expands the player.
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
    castingTo: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    // Under artwork, the cover's secondaryContainer: the tone Now Playing's ground starts from.
    Surface(
        onClick = onClick,
        modifier = modifier,
        color = artworkRole(colors.surfaceContainerHigh, colors.secondaryContainer),
        contentColor = artworkRole(colors.onSurface, colors.onSecondaryContainer),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = S2Spacing.smallMedium, end = S2Spacing.small, top = S2Spacing.xsmall, bottom = S2Spacing.xsmall),
                horizontalArrangement = Arrangement.spacedBy(S2Spacing.smallMedium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                artwork?.invoke()
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (castingTo != null) {
                        // primary is too light for text on an artwork container.
                        val castColor = artworkRole(colors.primary, colors.onSecondaryContainer)
                        Row(horizontalArrangement = Arrangement.spacedBy(S2Spacing.xsmall), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Cast, contentDescription = null, tint = castColor, modifier = Modifier.size(S2IconSize.small))
                            Text(
                                stringResource(R.string.ds_playing_on, castingTo),
                                style = MaterialTheme.typography.bodySmall,
                                color = castColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    } else {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                S2PlayPauseIconButton(playing, onPlayPause, buffering = buffering)
                S2TransportButton(nextIcon, nextContentDescription, onNext, onHold = onNextHold)
            }
            S2PlaybackProgress(
                progress = if (buffering) null else progress,
                playing = playing,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = S2Spacing.smallMedium)
                    .padding(bottom = S2Spacing.xsmall),
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
