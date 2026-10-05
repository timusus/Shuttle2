package com.simplecityapps.provider.emby

import android.net.Uri
import androidx.core.net.toUri
import com.simplecityapps.mediaprovider.DownloadInfo
import com.simplecityapps.mediaprovider.MediaInfo
import com.simplecityapps.mediaprovider.MediaInfoProvider
import com.simplecityapps.mediaprovider.StreamingPolicy
import com.simplecityapps.provider.emby.http.EmbyTranscodeService
import com.simplecityapps.shuttle.model.Song
import dev.zacsweers.metro.Inject

class EmbyMediaInfoProvider
@Inject
constructor(
    private val embyAuthenticationManager: EmbyAuthenticationManager,
    private val embyTranscodeService: EmbyTranscodeService,
    private val streamingPolicy: StreamingPolicy
) : MediaInfoProvider {
    private val streamUrls = EmbyStreamUrlProvider(embyAuthenticationManager, streamingPolicy)

    override fun handles(scheme: String?): Boolean = scheme == "emby"

    @Throws(IllegalStateException::class)
    override suspend fun getMediaInfo(
        song: Song,
        castCompatibilityMode: Boolean
    ): MediaInfo {
        val embyPath = buildPlaybackPathString(song).toUri()

        return MediaInfo(
            path = embyPath,
            mimeType = if (castCompatibilityMode) getMimeType(embyPath, song.mimeType) else song.mimeType,
            isRemote = true
        )
    }

    /**
     * String form of [getMediaInfo]'s path, capped by the current [StreamingPolicy], kept separate so tests can
     * assert on it without pulling Robolectric into this module for `Uri.parse`.
     */
    @Throws(IllegalStateException::class)
    internal fun buildPlaybackPathString(song: Song): String = streamUrls.streamUrl(song)

    private suspend fun getMimeType(
        path: Uri,
        defaultMimeType: String
    ): String = embyTranscodeService.contentType(path.toString()) ?: defaultMimeType

    override suspend fun downloadInfo(song: Song): DownloadInfo? = streamUrls.downloadSource(song)?.let { DownloadInfo(it.url.toUri(), it.mimeType) }

    override suspend fun downloadFallbackInfo(
        song: Song,
        responseCode: Int
    ): DownloadInfo? = streamUrls.downloadFallback(song, responseCode)?.let { DownloadInfo(it.url.toUri(), it.mimeType) }

    override fun downloadsAsTranscode(song: Song): Boolean = !embyAuthenticationManager.directPlays(song.audioCodec)
}
