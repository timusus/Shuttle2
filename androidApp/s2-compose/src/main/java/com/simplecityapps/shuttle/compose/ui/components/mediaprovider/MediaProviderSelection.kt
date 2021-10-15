package com.simplecityapps.shuttle.compose.ui.components.mediaprovider

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simplecityapps.shuttle.compose.R
import com.simplecityapps.shuttle.compose.ui.components.ThemedPreviewProvider
import com.simplecityapps.shuttle.compose.ui.theme.MaterialColors
import com.simplecityapps.shuttle.compose.ui.theme.Theme
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.isRemote
import com.simplecityapps.shuttle.ui.mediaprovider.MediaProviderSelectionViewModel

@Composable
fun MediaProviderSelection(
    viewModel: MediaProviderSelectionViewModel,
    onAddMediaProvider: () -> Unit = {},
    onConfigureMediaProvider: (MediaProviderType) -> Unit = {}
) {
    val selectedMediaProviders by viewModel.selectedMediaProviders.collectAsState()

    MediaProviderSelection(
        selectedMediaProviders = selectedMediaProviders,
        onRemoveMediaProvider = { mediaProviderType ->
            viewModel.removeMediaProvider(mediaProviderType)
        },
        onAddMediaProvider = onAddMediaProvider,
        onConfigureMediaProvider = onConfigureMediaProvider
    )
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun MediaProviderSelection(
    selectedMediaProviders: List<MediaProviderType>,
    onRemoveMediaProvider: (MediaProviderType) -> Unit = {},
    onAddMediaProvider: () -> Unit = {},
    onConfigureMediaProvider: (MediaProviderType) -> Unit = {}
) {
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
            Text(
                text = stringResource(id = R.string.onboarding_media_selection_subtitle),
                style = MaterialTheme.typography.body1,
                fontSize = 14.sp
            )

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = {
                    selectedMediaProviders
                        .groupBy { it.isRemote() }
                        .forEach { (isRemote, mediaProviders) ->
                            item {
                                Text(
                                    text = if (isRemote) stringResource(id = R.string.media_provider_type_remote) else stringResource(id = R.string.media_provider_type_local),
                                    style = MaterialTheme.typography.subtitle1
                                )
                            }
                            items(mediaProviders) { mediaProviderType ->
                                MediaProviderListItem(
                                    mediaProviderType = mediaProviderType,
                                    showOverflow = true,
                                    onRemove = { onRemoveMediaProvider(mediaProviderType) },
                                    onConfigure = { onConfigureMediaProvider(mediaProviderType) }
                                )
                            }
                        }
                })

            if (selectedMediaProviders.size != MediaProviderType.values().size) {
                OutlinedButton(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 16.dp),
                    onClick = {
                        onAddMediaProvider()
                    }) {
                    Text(
                        text = stringResource(id = R.string.media_provider_add),
                        style = MaterialTheme.typography.button
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MediaProviderSelectionPreview(@PreviewParameter(ThemedPreviewProvider::class) darkTheme: Boolean) {
    Theme(isDark = darkTheme) {
        MediaProviderSelection(
            selectedMediaProviders = listOf(MediaProviderType.MediaStore, MediaProviderType.Shuttle)
        )
    }
}

