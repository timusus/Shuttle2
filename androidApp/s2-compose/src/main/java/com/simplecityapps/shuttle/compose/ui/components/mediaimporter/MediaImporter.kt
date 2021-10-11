package com.simplecityapps.shuttle.compose.ui.components.mediaimporter

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Error
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simplecityapps.shuttle.compose.R
import com.simplecityapps.shuttle.compose.ui.components.ThemedPreviewProvider
import com.simplecityapps.shuttle.compose.ui.components.mediaprovider.iconResId
import com.simplecityapps.shuttle.compose.ui.components.mediaprovider.titleResId
import com.simplecityapps.shuttle.compose.ui.theme.MaterialColors
import com.simplecityapps.shuttle.compose.ui.theme.Theme
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Progress
import com.simplecityapps.shuttle.model.SongData
import com.simplecityapps.shuttle.ui.mediaimporter.ImportViewState
import com.simplecityapps.shuttle.ui.mediaimporter.MediaImporterViewModel
import com.simplecityapps.shuttle.ui.mediaimporter.ViewState

@Composable
fun MediaImporter(viewModel: MediaImporterViewModel, isVisible: Boolean) {
    val viewState by viewModel.viewState.collectAsState()
    MediaImporter(viewState = viewState)

    if (isVisible) {
        LaunchedEffect(viewModel) {
            viewModel.import()
        }
    }
}

@Composable
fun MediaImporter(viewState: ViewState) {
    Scaffold(topBar = {
        TopAppBar(
            backgroundColor = MaterialColors.background,
            title = { Text(stringResource(id = R.string.onboarding_media_scanner_title)) }
        )
    }) { padding ->
        when (viewState) {
            is ViewState.Loading -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    CircularProgressIndicator()
                }
            }
            is ViewState.ImportingMedia -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    items(viewState.importStates) { importState ->
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Image(
                                    modifier = Modifier.size(24.dp),
                                    painter = painterResource(id = importState.mediaProviderType.iconResId),
                                    contentDescription = stringResource(id = importState.mediaProviderType.titleResId)
                                )
                                Spacer(modifier = Modifier.size(16.dp))
                                Text(
                                    text = stringResource(id = importState.mediaProviderType.titleResId),
                                    style = MaterialTheme.typography.body1
                                )
                            }
                            Spacer(Modifier.size(4.dp))
                            ImportProgress(importState)
                        }
                    }
                }
            }
            is ViewState.Failed -> {
                when (viewState.reason) {
                    ViewState.Failed.Reason.NoMediaProviders -> {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Row(Modifier, verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Error, "Error")
                                Spacer(modifier = Modifier.size(16.dp))
                                Column() {
                                    Text(
                                        text = stringResource(id = R.string.media_import_error),
                                        style = MaterialTheme.typography.body1
                                    )
                                    Text(
                                        text = stringResource(id = R.string.media_scan_failed_no_media_providers),
                                        style = MaterialTheme.typography.body2
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ImportProgressView(titleText: String, progress: Progress?, progressText: String?) {
    Text(
        text = titleText,
        style = MaterialTheme.typography.body2,
    )

    Spacer(Modifier.size(8.dp))

    if (progress == null) {
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth()
        )
    } else {
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth(),
            progress = progress.asFloat
        )
    }

    if (progressText != null) {
        Spacer(Modifier.size(4.dp))
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = progressText,
            style = MaterialTheme.typography.body2,
            textAlign = TextAlign.End,
            fontSize = 12.sp,
            maxLines = 1
        )
    }
}


@Composable
fun ImportCompleteView(titleText: String, success: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = titleText,
            style = MaterialTheme.typography.body2,
        )

        Spacer(Modifier.size(8.dp))

        if (success) {
            Image(
                imageVector = Icons.Outlined.Check,
                contentDescription = "Check",
                colorFilter = ColorFilter.tint(MaterialColors.primary)
            )
        } else {
            Image(
                imageVector = Icons.Outlined.Close,
                contentDescription = "Error",
                colorFilter = ColorFilter.tint(MaterialColors.error)
            )
        }
    }
}

@Composable
fun ImportProgress(songImportState: ImportViewState) {
    when (songImportState) {
        is ImportViewState.Loading -> {
            ImportProgressView(
                titleText = stringResource(id = R.string.onboarding_media_import_importing_songs),
                progress = null,
                progressText = stringResource(id = R.string.media_provider_querying_api)
            )
        }
        is ImportViewState.QueryingApi -> {
            ImportProgressView(
                titleText = stringResource(id = R.string.onboarding_media_import_importing_songs),
                progress = songImportState.progress,
                progressText = stringResource(id = R.string.media_provider_querying_api)
            )
        }
        is ImportViewState.ReadingSongs -> {
            ImportProgressView(
                titleText = stringResource(id = R.string.onboarding_media_import_importing_songs),
                progress = songImportState.progress,
                progressText = songImportState.songData.displayName()
            )
        }
        is ImportViewState.UpdatingDatabase -> {
            ImportProgressView(
                titleText = stringResource(id = R.string.onboarding_media_import_importing_songs),
                progress = null,
                progressText = stringResource(id = R.string.media_import_updating_database)
            )
        }
        is ImportViewState.Complete -> {
            ImportCompleteView(
                titleText = stringResource(id = R.string.media_import_song_import_complete),
                success = true
            )
        }
        is ImportViewState.Failure -> {
            ImportCompleteView(
                titleText = stringResource(id = R.string.media_import_song_import_failure),
                success = false
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MediaImporterPreview(@PreviewParameter(ThemedPreviewProvider::class) darkTheme: Boolean) {
    Theme(isDark = darkTheme) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialColors.background)
        ) {
            MediaImporter(
                ViewState.ImportingMedia(
                    listOf(
                        ImportViewState.ReadingSongs(
                            mediaProviderType = MediaProviderType.MediaStore,
                            progress = Progress(5, 20),
                            songData = fakeSongData
                        ),
                        ImportViewState.QueryingApi(
                            mediaProviderType = MediaProviderType.MediaStore,
                            progress = Progress(15, 20)
                        ),
                        ImportViewState.Failure(mediaProviderType = MediaProviderType.Jellyfin),
                        ImportViewState.UpdatingDatabase(mediaProviderType = MediaProviderType.Plex)
                    )
                )
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MediaImporterErrorPreview(@PreviewParameter(ThemedPreviewProvider::class) darkTheme: Boolean) {
    Theme(isDark = darkTheme) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialColors.background)
        ) {
            MediaImporter(
                ViewState.Failed(ViewState.Failed.Reason.NoMediaProviders)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MediaImporterLoadingPreview(@PreviewParameter(ThemedPreviewProvider::class) darkTheme: Boolean) {
    Theme(isDark = darkTheme) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialColors.background)
        ) {
            MediaImporter(
                ViewState.Loading
            )
        }
    }
}

@Composable
internal fun SongData.displayName(): String {
    return listOf(
        albumArtist ?: artists.firstOrNull() ?: stringResource(id = R.string.unknown),
        name ?: stringResource(id = R.string.unknown)
    ).joinToString(" • ")
}

val fakeSongData = SongData(
    name = "Right Where it Belongs",
    albumArtist = "Nine Inch Nails",
    artists = listOf("Nine Inch Nails"),
    album = "With Teeth",
    track = 1,
    disc = 2,
    duration = 3,
    date = null,
    genres = emptyList(),
    path = "",
    size = null,
    mimeType = null,
    dateModified = null,
    lastPlayed = null,
    lastCompleted = null,
    externalId = null,
    mediaProvider = MediaProviderType.MediaStore,
    replayGainTrack = null,
    replayGainAlbum = null,
    lyrics = null,
    grouping = null,
    bitRate = null,
    sampleRate = null,
    channelCount = null,
    composer = null
)