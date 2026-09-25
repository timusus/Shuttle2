package com.simplecityapps.shuttle.ui.shell.player

import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.simplecityapps.shuttle.designsystem.R as DesignR
import com.simplecityapps.shuttle.designsystem.component.S2MiniPlayer

/** How far the mini player must be swiped sideways to skip. */
private val SkipSwipeThreshold = 72.dp

/**
 * The mini player (app-shell.md, section 1): tap to expand, play/pause and next in place, and a
 * sideways swipe to skip, towards the start for next and towards the end for previous. Composed
 * only while [interactive]: past half way it has faded out and Now Playing takes the taps.
 */
@Composable
internal fun MiniPlayer(
    player: PlayerUiState,
    progress: () -> PlayerProgress,
    actions: PlayerActions,
    interactive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth().height(MiniPlayerHeight).testTag(PlayerTestTags.MiniPlayer)) {
        val current = player.current
        if (interactive && current != null) {
            val previousLabel = stringResource(DesignR.string.ds_previous)
            S2MiniPlayer(
                title = current.title,
                subtitle = current.artist.orEmpty(),
                playing = player.playing,
                progress = { progress().fraction },
                onPlayPause = actions::togglePlayback,
                onNext = actions::skipToNext,
                onClick = onClick,
                modifier = Modifier
                    .fillMaxSize()
                    .skipSwipe(onNext = actions::skipToNext, onPrevious = actions::skipToPrevious)
                    .semantics {
                        customActions = listOf(
                            CustomAccessibilityAction(previousLabel) {
                                actions.skipToPrevious()
                                true
                            },
                        )
                    },
                artwork = { SongArtwork(current.song) },
                buffering = player.buffering,
            )
        }
    }
}

/** A sideways drag that follows the finger and springs back, skipping when let go past the threshold. */
@Composable
private fun Modifier.skipSwipe(
    onNext: () -> Unit,
    onPrevious: () -> Unit,
): Modifier {
    var dragX by remember { mutableFloatStateOf(0f) }
    val state = rememberDraggableState { delta -> dragX += delta }
    val ltr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val spec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val threshold = with(LocalDensity.current) { SkipSwipeThreshold.toPx() }
    return draggable(
        state = state,
        orientation = Orientation.Horizontal,
        onDragStopped = {
            val towardsStart = if (ltr) -dragX else dragX
            when {
                towardsStart >= threshold -> onNext()
                towardsStart <= -threshold -> onPrevious()
            }
            animate(dragX, 0f, animationSpec = spec) { value, _ -> dragX = value }
        },
    ).graphicsLayer { translationX = dragX }
}
