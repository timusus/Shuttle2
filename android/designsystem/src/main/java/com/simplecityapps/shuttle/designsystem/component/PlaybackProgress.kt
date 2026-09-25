package com.simplecityapps.shuttle.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.simplecityapps.shuttle.designsystem.theme.S2Theme

/**
 * Playback or download progress as a `LinearWavyProgressIndicator`: wavy while [playing], flattening
 * to a straight line when paused. A null [progress] is indeterminate (loading, buffering).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun S2PlaybackProgress(
    progress: (() -> Float)?,
    playing: Boolean,
    modifier: Modifier = Modifier,
) {
    if (progress == null) {
        LinearWavyProgressIndicator(modifier = modifier)
        return
    }
    // The wave's height is a colour-like effect, not a position, so it eases without overshoot.
    val amplitude by animateFloatAsState(if (playing) 1f else 0f, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "amplitude")
    LinearWavyProgressIndicator(progress = progress, modifier = modifier, amplitude = { amplitude })
}

@Preview
@Composable
private fun S2PlaybackProgressPreview() {
    S2Theme {
        S2PlaybackProgress(progress = { 0.4f }, playing = true, modifier = Modifier.fillMaxWidth())
    }
}
