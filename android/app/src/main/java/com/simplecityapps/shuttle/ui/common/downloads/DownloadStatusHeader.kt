package com.simplecityapps.shuttle.ui.common.downloads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.S2DownloadStatus
import com.simplecityapps.shuttle.model.Song

/**
 * What a collection header says about its songs' downloads: "Downloading 3 of 12" with a progress bar while they run,
 * "N of M songs downloaded" when some are on the device, "Downloaded" when all are. Nothing for a collection with no
 * downloads, or none from a server.
 */
@Composable
fun DownloadStatusHeader(songs: List<Song>, modifier: Modifier = Modifier) {
    val offline = LocalDownloadOfflineStates.current
    val progress = LocalDownloadProgress.current
    val status = remember(songs, offline, progress) { collectionDownload(songs, offline, progress) }
    val tagged = modifier.testTag("download-status")
    when (status) {
        CollectionDownload.None -> Unit
        is CollectionDownload.Downloading -> S2DownloadStatus(stringResource(R.string.download_status_downloading, status.downloaded, status.total), tagged, progress = status.progress)
        is CollectionDownload.Partial -> S2DownloadStatus(stringResource(R.string.download_status_partial, status.downloaded, status.total), tagged)
        CollectionDownload.Downloaded -> S2DownloadStatus(stringResource(R.string.download_status_downloaded), tagged)
    }
}
