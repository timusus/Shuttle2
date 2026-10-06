package com.simplecityapps.shuttle.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.simplecityapps.shuttle.designsystem.theme.S2IconSize
import com.simplecityapps.shuttle.designsystem.theme.S2Spacing

/**
 * What a collection's header says about its downloads: [label] beside a downloading or done icon, and while
 * [progress] (0..1) is given, a progress bar beneath.
 */
@Composable
fun S2DownloadStatus(
    label: String,
    modifier: Modifier = Modifier,
    progress: Float? = null,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(S2Spacing.xsmall)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(S2Spacing.xsmall)) {
            Icon(
                if (progress != null) Icons.Rounded.Downloading else Icons.Rounded.DownloadDone,
                contentDescription = null,
                modifier = Modifier.size(S2IconSize.small),
                tint = MaterialTheme.colorScheme.primary,
            )
            S2Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (progress != null) {
            S2PlaybackProgress(progress = { progress }, playing = true, modifier = Modifier.fillMaxWidth())
        }
    }
}
