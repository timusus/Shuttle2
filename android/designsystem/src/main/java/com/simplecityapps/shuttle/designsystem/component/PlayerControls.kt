package com.simplecityapps.shuttle.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import com.simplecityapps.shuttle.designsystem.R
import com.simplecityapps.shuttle.designsystem.theme.S2Theme
import kotlin.math.roundToInt

enum class S2RepeatMode { Off, All, One }

/** A [Morph] between two `MaterialShapes` at [progress], scaled to fill the bounds. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal class MorphShape(private val morph: Morph, private val progress: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = morph.toPath(progress)
        path.transform(Matrix().apply { scale(x = size.width, y = size.height) })
        path.translate(size.center - path.getBounds().center)
        return Outline.Generic(path)
    }
}

/** Paused (showing Play) is the scalloped cookie, playing (showing Pause) the rounded square. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal fun playPauseMorph() = Morph(MaterialShapes.Cookie9Sided, MaterialShapes.Square)

/**
 * The play/pause button: a `primary` shape that morphs from `MaterialShapes.Cookie9Sided`
 * (paused) to `MaterialShapes.Square` (playing) on the fast spatial spring. [buffering] swaps the
 * icon for a `LoadingIndicator`, itself a `MaterialShapes` morph.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun S2PlayPauseButton(
    playing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    buffering: Boolean = false,
    size: Dp = 80.dp,
    interactionSource: MutableInteractionSource? = null,
) {
    val morph = remember { playPauseMorph() }
    val progress by animateFloatAsState(if (playing) 1f else 0f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "playPause")
    val label = stringResource(if (playing) R.string.ds_pause else R.string.ds_play)
    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                shape = MorphShape(morph, progress.coerceIn(0f, 1f))
                clip = true
            }
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
 * The now-playing transport as a `ButtonGroup`: shuffle and repeat toggles either side of
 * previous, the morphing [S2PlayPauseButton] and next. Pressing a button widens it and squeezes
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
) {
    val sources = remember { List(5) { MutableInteractionSource() } }
    ButtonGroup(overflowIndicator = {}, modifier = modifier.scaleDownToFit(PlayerControlsWidth), verticalAlignment = Alignment.CenterVertically) {
        customItem(
            buttonGroupContent = {
                ToggleIcon(Icons.Rounded.Shuffle, stringResource(R.string.ds_shuffle), shuffle, onShuffleChange, Modifier.animateWidth(sources[0]), sources[0])
            },
            menuContent = {},
        )
        customItem(
            buttonGroupContent = {
                S2IconButton(
                    Icons.Rounded.SkipPrevious,
                    stringResource(R.string.ds_previous),
                    onPrevious,
                    Modifier.animateWidth(sources[1]),
                    size = S2IconButtonSize.Medium,
                    interactionSource = sources[1],
                )
            },
            menuContent = {},
        )
        customItem(
            buttonGroupContent = {
                S2PlayPauseButton(playing, onPlayPause, Modifier.animateWidth(sources[2]), buffering = buffering, interactionSource = sources[2])
            },
            menuContent = {},
        )
        customItem(
            buttonGroupContent = {
                S2IconButton(
                    Icons.Rounded.SkipNext,
                    stringResource(R.string.ds_next),
                    onNext,
                    Modifier.animateWidth(sources[3]),
                    size = S2IconButtonSize.Medium,
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
) {
    IconToggleButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        shapes = IconButtonDefaults.toggleableShapes(),
        modifier = modifier.size(48.dp),
        colors = IconButtonDefaults.iconToggleButtonColors(
            checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        interactionSource = interactionSource,
    ) {
        Icon(icon, contentDescription)
    }
}

/** The transport's natural width: two 48 dp toggles, two medium skip buttons, the 80 dp play button and the group's gaps. */
private val PlayerControlsWidth = 336.dp

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
    S2Theme {
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
