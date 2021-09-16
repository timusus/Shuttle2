package com.simplecityapps.shuttle.compose.ui.components.home

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.simplecityapps.shuttle.compose.ui.components.ThemedPreviewProvider
import com.simplecityapps.shuttle.compose.ui.theme.Theme

@Composable
fun Home() {
    Text("Fuck yeah")
}

@Preview(showBackground = true)
@Composable
fun HomePreview(@PreviewParameter(ThemedPreviewProvider::class) darkTheme: Boolean) {
    Theme(isDark = darkTheme) {
        Home()
    }
}