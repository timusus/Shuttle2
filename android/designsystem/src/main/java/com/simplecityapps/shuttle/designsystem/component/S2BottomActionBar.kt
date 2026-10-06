package com.simplecityapps.shuttle.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.simplecityapps.shuttle.designsystem.preview.S2Preview
import com.simplecityapps.shuttle.designsystem.theme.S2Spacing

/**
 * A screen's main actions, pinned below its scrolling content as a `Scaffold` bottom bar, so they stay in reach
 * wherever the user has scrolled. Stacks [content] (usually full-width [S2Button]s) on the container surface, clear of
 * the navigation bar.
 */
@Composable
fun S2BottomActionBar(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = S2Spacing.medium, vertical = S2Spacing.smallMedium),
            verticalArrangement = Arrangement.spacedBy(S2Spacing.small),
            content = content
        )
    }
}

@Preview
@Composable
private fun S2BottomActionBarPreview() {
    S2Preview {
        S2BottomActionBar {
            S2Button(text = "Start free trial", onClick = {}, size = S2ButtonSize.Medium, modifier = Modifier.fillMaxWidth())
            S2Button(text = "Buy now", onClick = {}, style = S2ButtonStyle.Outlined, size = S2ButtonSize.Medium, modifier = Modifier.fillMaxWidth())
        }
    }
}
