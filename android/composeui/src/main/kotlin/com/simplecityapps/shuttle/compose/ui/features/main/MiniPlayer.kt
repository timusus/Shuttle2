package com.simplecityapps.shuttle.compose.ui.features.main

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.simplecityapps.shuttle.compose.ui.theme.AppTheme

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun MiniPlayer(
    modifier: Modifier = Modifier,
    onTogglePlayback: () -> Unit = {},
    onSkipNext: () -> Unit = {},
) {
    Row(
        modifier = modifier
            .padding(
                horizontal = 16.dp,
                vertical = 8.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlideImage(
            modifier = Modifier.size(40.dp),
            model = null,
            contentDescription = null
        )
        Spacer(
            modifier = Modifier.size(16.dp)
        )
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                modifier = Modifier
                    .basicMarquee(),
                text = "Sick, Sick, Sick",
                maxLines = 1,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                modifier = Modifier
                    .basicMarquee(),
                text = "Queens of the Stone Age • Era Vulgaris",
                maxLines = 1,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        IconButton(onClick = { onTogglePlayback() }) {
            Icon(Icons.Outlined.PlayArrow, contentDescription = null)
        }
        IconButton(onClick = { onSkipNext() }) {
            Icon(Icons.Outlined.SkipNext, contentDescription = null)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun NowPLayingPreview() {
    AppTheme {
        MiniPlayer()
    }
}
