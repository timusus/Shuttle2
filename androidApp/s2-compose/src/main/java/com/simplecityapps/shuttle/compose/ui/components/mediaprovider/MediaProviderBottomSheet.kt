package com.simplecityapps.shuttle.compose.ui.components.mediaprovider

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.simplecityapps.shuttle.compose.R
import com.simplecityapps.shuttle.compose.ui.components.ThemedPreviewProvider
import com.simplecityapps.shuttle.compose.ui.theme.Theme
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.isRemote

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun MediaProviderBottomSheet(mediaProviders: List<MediaProviderType>, onMediaProviderTypeSelected: (MediaProviderType) -> Unit = {}) {
    Column(Modifier.padding(16.dp)) {
        Text(
            text = stringResource(id = R.string.media_provider_add),
            style = MaterialTheme.typography.h6
        )
        Spacer(modifier = Modifier.size(8.dp))
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = {
                mediaProviders
                    .groupBy { it.isRemote() }
                    .forEach { (isRemote, mediaProviders) ->
                        item {
                            Text(
                                text = if (isRemote) stringResource(id = R.string.media_provider_type_remote) else stringResource(id = R.string.media_provider_type_local),
                                style = MaterialTheme.typography.subtitle1
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                        }
                        items(mediaProviders) { mediaProviderType ->
                            Surface(
                                onClick = { onMediaProviderTypeSelected(mediaProviderType) }) {
                                MediaProviderListItem(
                                    modifier = Modifier.padding(start = 16.dp, end = 16.dp),
                                    mediaProviderType = mediaProviderType,
                                    showOverflow = false
                                )
                            }
                        }
                    }
            })
    }

}

@Preview(showBackground = true)
@Composable
fun MediaProviderBottomSheetPreview(@PreviewParameter(ThemedPreviewProvider::class) darkTheme: Boolean) {
    Theme(isDark = darkTheme) {
        MediaProviderBottomSheet(
            mediaProviders = listOf(MediaProviderType.MediaStore, MediaProviderType.Shuttle, MediaProviderType.Emby, MediaProviderType.Jellyfin)
        )
    }
}