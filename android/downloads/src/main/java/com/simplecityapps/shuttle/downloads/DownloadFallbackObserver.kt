package com.simplecityapps.shuttle.downloads

import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import com.simplecityapps.mediaprovider.AggregateMediaInfoProvider
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Retries a failed download once with [AggregateMediaInfoProvider.downloadFallbackUri] when Media3
 * reports an HTTP 401/403 from the Download URL (#322) — the server admin turned off download
 * permission after the URL was cached, or (401 only) the cached session simply expired. The
 * fallback is the static stream URL, which stays available in both cases; resolving it also
 * persists the permission change on a 403, so later downloads go straight to the stream URL.
 *
 * Keeps its own retried-path set rather than relying on Media3's retry count: a download that
 * fails for a different reason (network, disk) should keep retrying normally, so only a 401/403
 * consumes the one fallback attempt.
 */
@Singleton
@UnstableApi
class DownloadFallbackObserver
@Inject
constructor(
    private val downloadManager: DownloadManager,
    private val songDownloadManager: SongDownloadManager,
    private val mediaInfoProvider: AggregateMediaInfoProvider
) {
    private val retriedPaths = Collections.synchronizedSet(mutableSetOf<String>())

    fun observe(scope: CoroutineScope) {
        downloadManager.addListener(
            object : DownloadManager.Listener {
                override fun onDownloadChanged(
                    downloadManager: DownloadManager,
                    download: Download,
                    finalException: Exception?
                ) = onDownloadChanged(download, finalException, scope)
            }
        )
    }

    /** Split out from the [DownloadManager.Listener] for testing without a real [DownloadManager]. */
    internal fun onDownloadChanged(
        download: Download,
        finalException: Exception?,
        scope: CoroutineScope
    ) {
        if (download.state != Download.STATE_FAILED) return
        val responseCode = finalException?.let { invalidResponseCode(it) }
        if (responseCode != 401 && responseCode != 403) return

        val path = download.request.id
        if (!retriedPaths.add(path)) return

        scope.launch {
            val fallbackUri = mediaInfoProvider.downloadFallbackUri(path, responseCode)
            if (fallbackUri == null) {
                Timber.w("Download for $path got HTTP $responseCode but no fallback URL was available")
                return@launch
            }
            Timber.i("Download for $path got HTTP $responseCode; retrying once with the fallback URL")
            songDownloadManager.download(path, download.request.mimeType ?: MimeTypes.AUDIO_UNKNOWN, fallbackUri)
        }
    }

    /** Media3 wraps the HTTP failure in retry/IO exceptions, so the response code can be several causes deep. */
    private fun invalidResponseCode(exception: Throwable): Int? = generateSequence(exception) { it.cause }
        .filterIsInstance<HttpDataSource.InvalidResponseCodeException>()
        .firstOrNull()
        ?.responseCode
}
