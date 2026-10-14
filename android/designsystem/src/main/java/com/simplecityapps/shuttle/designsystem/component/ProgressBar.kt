package com.simplecityapps.shuttle.designsystem.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.simplecityapps.shuttle.designsystem.preview.S2Preview

/** A plain linear bar for work that isn't playback (imports, downloads, settings rows): determinate with [progress], otherwise indeterminate. */
@Composable
fun S2ProgressBar(
    progress: (() -> Float)?,
    modifier: Modifier = Modifier,
) {
    if (progress != null) LinearProgressIndicator(progress = progress, modifier = modifier) else LinearProgressIndicator(modifier = modifier)
}

@Preview
@Composable
private fun S2ProgressBarPreview() {
    S2Preview {
        S2ProgressBar(progress = { 0.4f }, modifier = Modifier.fillMaxWidth())
    }
}
