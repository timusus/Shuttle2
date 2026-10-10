package com.simplecityapps.mediaprovider.server.mediabrowser

import android.net.Uri
import androidx.core.net.toUri
import com.simplecityapps.mediaprovider.DownloadInfo
import com.simplecityapps.mediaprovider.MediaInfo
import com.simplecityapps.mediaprovider.MediaInfoProvider
import com.simplecityapps.mediaprovider.StreamingPolicy
import com.simplecityapps.shuttle.model.Song

/** Playback and download uris for a [MediaBrowserServer]'s songs, which use the server's name as their scheme. */
class MediaBrowserMediaInfoProvider(
    private val authenticationManager: MediaBrowserAuthenticationManager,
    private val transcodeService: MediaBrowserTranscodeService,
    streamingPolicy: StreamingPolicy
) : MediaInfoProvider {
    private val scheme = authenticationManager.server.name.lowercase()
    private val streamUrls = MediaBrowserStreamUrlProvider(authenticationManager, streamingPolicy)

    override fun handles(scheme: String?): Boolean = scheme == this.scheme

    @Throws(IllegalStateException::class)
    override suspend fun getMediaInfo(
        song: Song,
        castCompatibilityMode: Boolean,
        playId: String?
    ): MediaInfo {
        val path = streamUrls.streamUrl(song, playId = playId).toUri()

        return MediaInfo(
            path = path,
            mimeType = if (castCompatibilityMode) getMimeType(path, song.mimeType) else song.mimeType,
            isRemote = true
        )
    }

    private suspend fun getMimeType(
        path: Uri,
        defaultMimeType: String
    ): String = transcodeService.contentType(path.toString()) ?: defaultMimeType

    override suspend fun downloadInfo(song: Song): DownloadInfo? = streamUrls.downloadSource(song)?.let { DownloadInfo(it.url.toUri(), it.mimeType) }

    override suspend fun downloadFallbackInfo(
        song: Song,
        responseCode: Int
    ): DownloadInfo? = streamUrls.downloadFallback(song, responseCode)?.let { DownloadInfo(it.url.toUri(), it.mimeType) }

    override fun downloadsAsTranscode(song: Song): Boolean = !authenticationManager.directPlays(song.audioCodec)
}
