package com.simplecityapps.shuttle.ui.screens.settings.downloads

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.FileDownloadOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.AlbumRow
import com.simplecityapps.shuttle.designsystem.component.ArtworkPlaceholder
import com.simplecityapps.shuttle.designsystem.component.EmptyState
import com.simplecityapps.shuttle.designsystem.component.LoadingState
import com.simplecityapps.shuttle.designsystem.component.S2Dialog
import com.simplecityapps.shuttle.designsystem.component.S2IconButton
import com.simplecityapps.shuttle.designsystem.component.S2Text
import com.simplecityapps.shuttle.designsystem.component.SectionHeader
import com.simplecityapps.shuttle.ui.screens.library.LibraryArtwork
import com.simplecityapps.shuttle.ui.screens.settings.SettingsScaffold

/** Nothing is downloaded, in progress or kept: when something is, the screen shows the storage and Remove all even before an album is listed. */
private val DownloadsUiState.isEmpty: Boolean get() = albums.isEmpty() && storageBytes == 0L

/** The albums downloaded for offline playback and the storage they use; the top bar removes every download. */
@Composable
fun DownloadsScreen(
    uiState: DownloadsUiState,
    onNavigateUp: () -> Unit,
    onRemoveAll: () -> Unit,
    onOpenAlbum: (DownloadedAlbum) -> Unit,
    modifier: Modifier = Modifier
) {
    var confirmRemoveAll by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    SettingsScaffold(
        title = stringResource(R.string.downloads_title),
        onNavigateUp = onNavigateUp,
        modifier = modifier,
        contentPadding = PaddingValues(),
        verticalArrangement = Arrangement.Top,
        actions = {
            if (!uiState.isEmpty) {
                S2IconButton(
                    icon = Icons.Rounded.ClearAll,
                    contentDescription = stringResource(R.string.downloads_remove_all),
                    onClick = { confirmRemoveAll = true }
                )
            }
        }
    ) {
        when {
            uiState.loading -> item(key = "loading") { LoadingState() }

            uiState.isEmpty -> item(key = "empty") {
                EmptyState(title = stringResource(R.string.downloads_empty), icon = Icons.Rounded.FileDownloadOff)
            }

            else -> {
                item(key = "storage", contentType = "header") {
                    SectionHeader(title = stringResource(R.string.downloads_storage_used, Formatter.formatShortFileSize(context, uiState.storageBytes)))
                }
                items(uiState.albums, key = { it.key }, contentType = { "album" }) { album ->
                    AlbumRow(
                        title = album.name ?: stringResource(com.simplecityapps.core.R.string.unknown),
                        artist = album.artist.orEmpty(),
                        onClick = { onOpenAlbum(album) },
                        artwork = { LibraryArtwork(album.cover, ArtworkPlaceholder.Album) },
                        meta = pluralStringResource(R.plurals.downloads_album_meta, album.songCount, album.songCount, Formatter.formatShortFileSize(context, album.bytes))
                    )
                }
            }
        }
    }

    if (confirmRemoveAll) {
        S2Dialog(
            title = stringResource(R.string.downloads_remove_all_title),
            onDismissRequest = { confirmRemoveAll = false },
            confirmLabel = stringResource(R.string.downloads_remove_all),
            onConfirm = {
                confirmRemoveAll = false
                onRemoveAll()
            },
            dismissLabel = stringResource(android.R.string.cancel)
        ) {
            S2Text(stringResource(R.string.downloads_remove_all_message))
        }
    }
}
