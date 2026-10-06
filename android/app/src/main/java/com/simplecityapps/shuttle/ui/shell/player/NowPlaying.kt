package com.simplecityapps.shuttle.ui.shell.player

import android.view.ContextThemeWrapper
import android.view.View
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Forward30
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.SpeakerGroup
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.mediarouter.app.MediaRouteButton
import androidx.mediarouter.app.SystemOutputSwitcherDialogController
import com.google.android.gms.cast.framework.CastButtonFactory
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.R as DesignR
import com.simplecityapps.shuttle.designsystem.component.ArtworkSize
import com.simplecityapps.shuttle.designsystem.component.S2IconButton
import com.simplecityapps.shuttle.designsystem.component.S2IconToggleButton
import com.simplecityapps.shuttle.designsystem.component.S2PlayerControls
import com.simplecityapps.shuttle.designsystem.component.S2PlayerControlsSize
import com.simplecityapps.shuttle.designsystem.component.S2PlayingOn
import com.simplecityapps.shuttle.designsystem.component.S2SeekBar
import com.simplecityapps.shuttle.designsystem.theme.S2Spacing
import com.simplecityapps.shuttle.designsystem.theme.S2TouchTarget
import com.simplecityapps.shuttle.designsystem.theme.artworkRole

/** The name of the Cast device playback plays on, or null while it plays on this one. */
@Composable
internal fun PlayerUiState.castingTo(): String? = castDevice?.let { it.name ?: stringResource(R.string.player_cast_device) }

/**
 * The Cast framework's route button, themed to the player's scheme. Only shown where Cast can start. [onView] hands
 * over the View for a label that clicks it, and leaves it out of accessibility for the label's node.
 */
@Composable
internal fun CastButton(
    modifier: Modifier = Modifier,
    onView: ((View) -> Unit)? = null,
) {
    val description = stringResource(R.string.player_cast)
    val tint = artworkRole(MaterialTheme.colorScheme.onSurfaceVariant, PlayerControlsColor).toArgb()
    AndroidView(
        factory = { context ->
            // The route button reads AppCompat colours from its context; don't depend on the host activity's theme for them.
            MediaRouteButton(ContextThemeWrapper(context, androidx.appcompat.R.style.Theme_AppCompat_DayNight_NoActionBar)).also { button ->
                CastButtonFactory.setUpMediaRouteButton(context.applicationContext, button)
                button.contentDescription = description
                if (onView != null) {
                    button.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    onView(button)
                }
            }
        },
        update = { button ->
            ContextCompat.getDrawable(button.context, androidx.mediarouter.R.drawable.mr_button_light)?.mutate()?.let { drawable ->
                drawable.setTint(tint)
                button.setRemoteIndicatorDrawable(drawable)
            }
        },
        modifier = modifier.size(S2TouchTarget.minimum),
    )
}

/** Where Cast can't start (no Play services): a plain button that opens Android's output switcher for Bluetooth and wired outputs. */
@Composable
internal fun OutputButton(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    S2IconButton(
        icon = Icons.Rounded.SpeakerGroup,
        contentDescription = stringResource(R.string.player_output),
        onClick = { SystemOutputSwitcherDialogController.showDialog(context) },
        modifier = modifier,
    )
}

/** The artwork, square and as large as its slot allows, up to [MaxArtworkSize], with [gap] above and below it. Swiping it sideways skips. */
@Composable
internal fun NowPlayingArtwork(
    player: PlayerUiState,
    actions: PlayerActions,
    gap: Dp,
    modifier: Modifier = Modifier,
) {
    var boxModifier = modifier.fillMaxWidth().padding(horizontal = S2Spacing.medium, vertical = gap).testTag(PlayerTestTags.NowPlayingArtwork)
    if (player.current != null) boxModifier = boxModifier.skipSwipe(onNext = actions::skipToNext, onPrevious = actions::skipToPrevious)
    Box(boxModifier, contentAlignment = Alignment.Center) {
        player.current?.let { current ->
            SongArtwork(current.song, Modifier.widthIn(max = MaxArtworkSize).aspectRatio(1f, matchHeightConstraintsFirst = true), size = ArtworkSize.Hero, image = player.nowPlayingImage)
        }
    }
}

/**
 * The title, the artist and album, and the quality line beside the favourite toggle, sitting a small step above the
 * seek bar. While casting, the Cast device playback plays on takes the quality line's place.
 */
@Composable
internal fun NowPlayingTitle(
    player: PlayerUiState,
    actions: PlayerActions,
    modifier: Modifier = Modifier,
) {
    val current = player.current
    Row(modifier.fillMaxWidth().padding(start = S2Spacing.medium + S2Spacing.xsmall, end = S2Spacing.small, bottom = S2Spacing.small), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(text = current?.title.orEmpty(), style = MaterialTheme.typography.headlineMediumEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = listOfNotNull(current?.artist, current?.album).joinToString(" • "),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val castingTo = player.castingTo()
            if (castingTo != null) {
                S2PlayingOn(castingTo, style = MaterialTheme.typography.bodyMedium)
            } else {
                current?.song?.qualityLine(player.delivered)?.let { quality ->
                    Text(text = quality, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        S2IconToggleButton(
            icon = Icons.Rounded.FavoriteBorder,
            checkedIcon = Icons.Rounded.Favorite,
            contentDescription = stringResource(R.string.menu_title_favorite),
            checked = player.favourite,
            onCheckedChange = { actions.toggleFavourite() },
            enabled = current != null,
        )
    }
}

/**
 * The artwork over the title, at least [gap] from the edges and from each other. The artwork takes
 * the height the title leaves, up to its square; whatever is spare is shared evenly above the artwork,
 * between it and the title, and below the title, rather than left as an empty band at either end.
 */
@Composable
internal fun NowPlayingSong(
    player: PlayerUiState,
    actions: PlayerActions,
    gap: Dp,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.SpaceEvenly) {
        NowPlayingArtwork(player, actions, gap, Modifier.weight(1f, fill = false))
        NowPlayingTitle(player, actions)
    }
}

/**
 * The seek bar over the transport controls, [gap] below them. The Large controls sit closer to the
 * edges than the seek bar, and scale down where even that doesn't fit.
 */
@Composable
internal fun Transport(
    player: PlayerUiState,
    progress: () -> PlayerProgress,
    actions: PlayerActions,
    gap: Dp,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(bottom = gap).testTag(PlayerTestTags.Transport),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SeekBar(player, progress, actions)
        Box(Modifier.padding(horizontal = S2Spacing.small), contentAlignment = Alignment.Center) {
            val seekable = player.current?.song?.type?.isSeekable == true
            // The play disc keeps primary; the skips and toggles take the artwork's controls colour.
            CompositionLocalProvider(LocalContentColor provides PlayerControlsColor) {
                S2PlayerControls(
                    playing = player.playing,
                    onPlayPause = actions::togglePlayback,
                    onPrevious = if (seekable) {
                        { actions.seekBy(progress(), -SeekBackwardSeconds) }
                    } else {
                        actions::skipToPrevious
                    },
                    onNext = if (seekable) {
                        { actions.seekBy(progress(), SeekForwardSeconds) }
                    } else {
                        actions::skipToNext
                    },
                    shuffle = player.shuffle,
                    onShuffleChange = { actions.toggleShuffle() },
                    repeatMode = player.repeatMode,
                    onRepeatClick = actions::cycleRepeatMode,
                    buffering = player.buffering,
                    size = S2PlayerControlsSize.Large,
                    previousIcon = if (seekable) Icons.Rounded.Replay10 else Icons.Rounded.SkipPrevious,
                    previousContentDescription = if (seekable) stringResource(R.string.player_seek_backward) else stringResource(DesignR.string.ds_previous),
                    onPreviousHold = if (seekable) {
                        null
                    } else {
                        { actions.seekBy(progress(), -SkipHoldSeekSeconds) }
                    },
                    nextIcon = if (seekable) Icons.Rounded.Forward30 else Icons.Rounded.SkipNext,
                    nextContentDescription = if (seekable) stringResource(R.string.player_seek_forward) else stringResource(DesignR.string.ds_next),
                    onNextHold = if (seekable) {
                        null
                    } else {
                        { actions.seekBy(progress(), SkipHoldSeekSeconds) }
                    },
                )
            }
        }
    }
}

/** Reads the ticking position in its own scope, so only the seek bar recomposes with it. */
@Composable
private fun SeekBar(
    player: PlayerUiState,
    progress: () -> PlayerProgress,
    actions: PlayerActions,
) {
    val current = progress()
    S2SeekBar(
        positionMs = current.positionMs,
        durationMs = current.durationMs,
        onSeek = actions::seekTo,
        enabled = player.current != null,
        buffering = player.buffering,
        showRemaining = player.showRemainingTime,
        onToggleRemaining = { actions.setShowRemainingTime(!player.showRemainingTime) },
        modifier = Modifier.fillMaxWidth().padding(horizontal = S2Spacing.medium),
    )
}

/** The largest the Now Playing artwork grows, however much room there is. */
internal val MaxArtworkSize = 480.dp
