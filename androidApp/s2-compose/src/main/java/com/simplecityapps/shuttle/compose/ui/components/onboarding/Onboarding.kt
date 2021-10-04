package com.simplecityapps.shuttle.compose.ui.components.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.simplecityapps.shuttle.compose.R
import com.simplecityapps.shuttle.compose.ui.components.ThemedPreviewProvider
import com.simplecityapps.shuttle.compose.ui.theme.MaterialColors
import com.simplecityapps.shuttle.compose.ui.theme.Theme

@Composable
fun Onboarding() {
    Scaffold(topBar = {
        TopAppBar(
            backgroundColor = MaterialColors.background,
            title = { Text(stringResource(id = R.string.media_provider_toolbar_title_onboarding)) }
        )
    }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(start = 16.dp, end = 16.dp, top = 16.dp)
        ) {
            Text(stringResource(id = R.string.onboarding_media_selection_subtitle))

        }
    }
}

@Preview(showBackground = true)
@Composable
fun OnboardingPreview(@PreviewParameter(ThemedPreviewProvider::class) darkTheme: Boolean) {
    Theme(isDark = darkTheme) {
        Onboarding()
    }
}