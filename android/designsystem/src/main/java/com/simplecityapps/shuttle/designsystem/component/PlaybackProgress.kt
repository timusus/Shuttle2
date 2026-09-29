package com.simplecityapps.shuttle.designsystem.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.simplecityapps.shuttle.designsystem.preview.S2Preview

/**
 * Playback or download progress as a flat `LinearProgressIndicator`.
 * A null [progress] is indeterminate (loading, buffering). [playing] is kept for call-site
 * compatibility and has no visual effect.
 */
@Composable
fun S2PlaybackProgress(
    progress: (() -> Float)?,
    playing: Boolean,
    modifier: Modifier = Modifier,
) {
    if (progress == null) {
        LinearProgressIndicator(modifier = modifier)
        return
    }
    LinearProgressIndicator(progress = progress, modifier = modifier)
}

@Preview
@Composable
private fun S2PlaybackProgressPreview() {
    S2Preview {
        S2PlaybackProgress(progress = { 0.4f }, playing = true, modifier = Modifier.fillMaxWidth())
    }
}
