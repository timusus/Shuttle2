package com.simplecityapps.shuttle.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.simplecityapps.shuttle.designsystem.R
import com.simplecityapps.shuttle.designsystem.preview.S2Preview
import com.simplecityapps.shuttle.designsystem.theme.S2IconSize
import com.simplecityapps.shuttle.designsystem.theme.S2TouchTarget
import com.simplecityapps.shuttle.ui.shell.player.S2RepeatMode
import kotlin.math.roundToInt

/**
 * The transport's scale. [Regular] fits the 360 dp pane; [Large] is the phone's Now Playing, with a
 * bigger play button and hit targets. [width] is the natural width: the buttons plus the group's gaps.
 */
enum class S2PlayerControlsSize(
    internal val toggle: Dp,
    internal val toggleIcon: Dp,
    internal val skip: S2IconButtonSize,
    internal val skipContainer: Dp?,
    internal val playPause: Dp,
    internal val width: Dp,
) {
    Regular(toggle = 48.dp, toggleIcon = 24.dp, skip = S2IconButtonSize.Medium, skipContainer = null, playPause = 80.dp, width = 336.dp),

    // The Large icon button's 32 dp icon in a 64 dp container, rather than its 96 dp one, so the row fits a 411 dp phone.
    Large(toggle = 56.dp, toggleIcon = 28.dp, skip = S2IconButtonSize.Large, skipContainer = 64.dp, playPause = 96.dp, width = 384.dp),
}

/**
 * The full player's play/pause button: a filled `primary` circle, the one transport button with a
 * container (#783). [buffering] swaps the icon for a `LoadingIndicator`. Elsewhere (the mini player)
 * play/pause is a plain [S2PlayPauseIconButton].
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun S2PlayPauseButton(
    playing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    buffering: Boolean = false,
    size: Dp = S2PlayerControlsSize.Regular.playPause,
    interactionSource: MutableInteractionSource? = null,
) {
    val label = stringResource(if (playing) R.string.ds_pause else R.string.ds_play)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .clickable(interactionSource = interactionSource, indication = ripple(), role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        if (buffering) {
            LoadingIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(size * 0.6f))
        } else {
            Icon(
                if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(size * 0.45f),
            )
        }
    }
}

/**
 * Play/pause as a standard icon button, a [S2TouchTarget.minimum] square with no container: the mini
 * player's (#738). [buffering] swaps the icon for a `LoadingIndicator`; the button stays enabled, so
 * a tap while it spins pauses.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun S2PlayPauseIconButton(
    playing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    buffering: Boolean = false,
) {
    val label = stringResource(if (playing) R.string.ds_pause else R.string.ds_play)
    IconButton(onClick = onClick, modifier = modifier.size(S2TouchTarget.minimum).semantics { contentDescription = label }) {
        if (buffering) {
            LoadingIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(S2IconSize.medium))
        } else {
            Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = null)
        }
    }
}

/**
 * The now-playing transport as a `ButtonGroup`: shuffle and repeat toggles either side of
 * previous, the [S2PlayPauseButton] and next. Pressing a button widens it and squeezes
 * its neighbours; the toggles morph round to square when on.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun S2PlayerControls(
    playing: Boolean,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    shuffle: Boolean,
    onShuffleChange: (Boolean) -> Unit,
    repeatMode: S2RepeatMode,
    onRepeatClick: () -> Unit,
    modifier: Modifier = Modifier,
    buffering: Boolean = false,
    size: S2PlayerControlsSize = S2PlayerControlsSize.Regular,
    previousIcon: ImageVector = Icons.Rounded.SkipPrevious,
    previousContentDescription: String = stringResource(R.string.ds_previous),
    /** Holding the previous button repeats this instead of calling [onPrevious] (1.0.10's `SkipButton`). */
    onPreviousHold: (() -> Unit)? = null,
    nextIcon: ImageVector = Icons.Rounded.SkipNext,
    nextContentDescription: String = stringResource(R.string.ds_next),
    /** Holding the next button repeats this instead of calling [onNext] (1.0.10's `SkipButton`). */
    onNextHold: (() -> Unit)? = null,
) {
    val sources = remember { List(5) { MutableInteractionSource() } }
    val skipModifier = size.skipContainer?.let { Modifier.size(it) } ?: Modifier
    ButtonGroup(overflowIndicator = {}, modifier = modifier.scaleDownToFit(size.width), verticalAlignment = Alignment.CenterVertically) {
        customItem(
            buttonGroupContent = {
                ToggleIcon(Icons.Rounded.Shuffle, stringResource(R.string.ds_shuffle), shuffle, onShuffleChange, Modifier.animateWidth(sources[0]), sources[0], size)
            },
            menuContent = {},
        )
        customItem(
            buttonGroupContent = {
                S2TransportButton(
                    previousIcon,
                    previousContentDescription,
                    onPrevious,
                    Modifier.animateWidth(sources[1]).then(skipModifier),
                    onHold = onPreviousHold,
                    size = size.skip,
                    interactionSource = sources[1],
                )
            },
            menuContent = {},
        )
        customItem(
            buttonGroupContent = {
                S2PlayPauseButton(playing, onPlayPause, Modifier.animateWidth(sources[2]), buffering = buffering, size = size.playPause, interactionSource = sources[2])
            },
            menuContent = {},
        )
        customItem(
            buttonGroupContent = {
                S2TransportButton(
                    nextIcon,
                    nextContentDescription,
                    onNext,
                    Modifier.animateWidth(sources[3]).then(skipModifier),
                    onHold = onNextHold,
                    size = size.skip,
                    interactionSource = sources[3],
                )
            },
            menuContent = {},
        )
        customItem(
            buttonGroupContent = {
                ToggleIcon(
                    icon = if (repeatMode == S2RepeatMode.One) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                    contentDescription = stringResource(
                        when (repeatMode) {
                            S2RepeatMode.Off -> R.string.ds_repeat_off
                            S2RepeatMode.All -> R.string.ds_repeat_all
                            S2RepeatMode.One -> R.string.ds_repeat_one
                        },
                    ),
                    checked = repeatMode != S2RepeatMode.Off,
                    onCheckedChange = { onRepeatClick() },
                    modifier = Modifier.animateWidth(sources[4]),
                    interactionSource = sources[4],
                    size = size,
                )
            },
            menuContent = {},
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ToggleIcon(
    icon: ImageVector,
    contentDescription: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier,
    interactionSource: MutableInteractionSource,
    size: S2PlayerControlsSize,
) {
    IconToggleButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        shapes = IconButtonDefaults.toggleableShapes(),
        modifier = modifier.size(size.toggle),
        colors = IconButtonDefaults.iconToggleButtonColors(
            checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        interactionSource = interactionSource,
    ) {
        Icon(icon, contentDescription, Modifier.size(size.toggleIcon))
    }
}

/**
 * Below [natural] width, lays the content out at [natural] and scales it down to fit. `ButtonGroup`
 * otherwise drops the buttons that don't fit (clipping Repeat), and throws once it can't fit its
 * first three; a narrow pane or split screen can get there.
 */
private fun Modifier.scaleDownToFit(natural: Dp): Modifier = layout { measurable, constraints ->
    val naturalPx = natural.roundToPx()
    if (constraints.maxWidth >= naturalPx) {
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    } else {
        val scale = constraints.maxWidth.toFloat() / naturalPx
        val placeable = measurable.measure(Constraints.fixedWidth(naturalPx))
        val height = (placeable.height * scale).roundToInt().coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(constraints.maxWidth, height) {
            placeable.placeWithLayer(0, 0) {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0f)
            }
        }
    }
}

@Preview
@Composable
private fun S2PlayerControlsPreview() {
    S2Preview {
        S2PlayerControls(
            playing = true,
            onPlayPause = {},
            onPrevious = {},
            onNext = {},
            shuffle = true,
            onShuffleChange = {},
            repeatMode = S2RepeatMode.All,
            onRepeatClick = {},
        )
    }
}
