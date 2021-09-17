package com.simplecityapps.shuttle.compose.ui.components.miniplayer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.simplecityapps.shuttle.compose.ui.components.ThemedPreviewProvider
import com.simplecityapps.shuttle.compose.ui.theme.MaterialColors
import com.simplecityapps.shuttle.compose.ui.theme.Theme

@Composable
fun MiniPLayer() {

    Column(
        modifier = Modifier
            .height(56.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.Center
    ) {

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.size(8.dp))

            Box(
                Modifier
                    .size(40.dp)
                    .background(MaterialColors.primary.copy(alpha = 0.25f))
            )

            Spacer(modifier = Modifier.size(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text("Rosetta Stoned")
                Text("TOOL | 10,000 Days")
            }

            Spacer(modifier = Modifier.size(8.dp))

            IconButton(onClick = {}) {
                Icon(imageVector = Icons.Outlined.PlayArrow, contentDescription = "Play")
            }

            IconButton(onClick = {}) {
                Icon(imageVector = Icons.Outlined.SkipNext, contentDescription = "Skip")
            }
        }
    }
}


@Preview(showBackground = true)
@Composable
fun MiniPlayerPreview(@PreviewParameter(ThemedPreviewProvider::class) darkTheme: Boolean) {
    Theme(isDark = darkTheme) {
        MiniPLayer()
    }
}