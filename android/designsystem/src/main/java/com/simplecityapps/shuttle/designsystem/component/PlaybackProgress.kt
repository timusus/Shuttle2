package com.simplecityapps.shuttle.designsystem.component

import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.simplecityapps.shuttle.designsystem.preview.S2Preview

// Material's wave is loud on a thin mini player bar, so ours is shallower and longer than its defaults.
private const val PEAK_AMPLITUDE = 0.45f
private const val AMPLITUDE_TAPER_START = 0.1f
private const val AMPLITUDE_TAPER_END = 0.95f
private const val WAVELENGTH_SCALE = 1.75f
private const val WAVE_SPEED_WAVELENGTHS_PER_SECOND = 0.5f

private val wavelength: Dp = WavyProgressIndicatorDefaults.LinearDeterminateWavelength * WAVELENGTH_SCALE
private val waveSpeed: Dp = wavelength * WAVE_SPEED_WAVELENGTHS_PER_SECOND

/** Flat at the ends of the track, like [WavyProgressIndicatorDefaults.indicatorAmplitude], [PEAK_AMPLITUDE] in between. */
private fun taperedAmplitude(progress: Float): Float = if (progress <= AMPLITUDE_TAPER_START || progress >= AMPLITUDE_TAPER_END) 0f else PEAK_AMPLITUDE

/** False when the system's animator duration scale is 0 ("Remove animations"). Read once per composition site. */
@Composable
private fun rememberSystemAnimationsEnabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f }
}

/**
 * Playback progress as a `LinearWavyProgressIndicator`: a subtle wave while [playing], flattening to a
 * straight line when paused or when system animations are removed. A null [progress] is indeterminate
 * (buffering). Other progress (imports, downloads) uses [S2ProgressBar].
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
    val waving = playing && rememberSystemAnimationsEnabled()
    // The wave's height is a colour-like effect, not a position, so it eases without overshoot.
    val scale by animateFloatAsState(if (waving) 1f else 0f, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "amplitude")
    LinearWavyProgressIndicator(
        progress = progress,
        modifier = modifier,
        amplitude = { taperedAmplitude(it) * scale },
        wavelength = wavelength,
        waveSpeed = waveSpeed,
    )
}

@Preview
@Composable
private fun S2PlaybackProgressPreview() {
    S2Preview {
        S2PlaybackProgress(progress = { 0.4f }, playing = true, modifier = Modifier.fillMaxWidth())
    }
}
