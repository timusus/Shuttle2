package com.simplecityapps.provider.jellyfin

import android.net.Uri
import androidx.core.net.toUri
import com.simplecityapps.mediaprovider.DownloadInfo
import com.simplecityapps.mediaprovider.MediaInfo
import com.simplecityapps.mediaprovider.MediaInfoProvider
import com.simplecityapps.mediaprovider.StreamingPolicy
import com.simplecityapps.provider.jellyfin.http.JellyfinTranscodeService
import com.simplecityapps.shuttle.model.Song
import dev.zacsweers.metro.Inject

class JellyfinMediaInfoProvider
@Inject
constructor(
    private val jellyfinAuthenticationManager: JellyfinAuthenticationManager,
    private val jellyfinTranscodeService: JellyfinTranscodeService,
    private val streamingPolicy: StreamingPolicy
) : MediaInfoProvider {
    private val streamUrls = JellyfinStreamUrlProvider(jellyfinAuthenticationManager, streamingPolicy)

    override fun handles(scheme: String?): Boolean = scheme == "jellyfin"

    @Throws(IllegalStateException::class)
    override suspend fun getMediaInfo(
        song: Song,
        castCompatibilityMode: Boolean,
        playId: String?
    ): MediaInfo {
        val jellyfinPath = buildPlaybackPathString(song, playId).toUri()

        return MediaInfo(
            path = jellyfinPath,
            mimeType = if (castCompatibilityMode) getMimeType(jellyfinPath, song.mimeType) else song.mimeType,
            isRemote = true
        )
    }

    /**
     * String form of [getMediaInfo]'s path, capped by the current [StreamingPolicy], kept separate so tests can
     * assert on it without pulling Robolectric into this module for `Uri.parse`.
     */
    @Throws(IllegalStateException::class)
    internal fun buildPlaybackPathString(
        song: Song,
        playId: String? = null
    ): String = streamUrls.streamUrl(song, playId = playId)

    private suspend fun getMimeType(
        path: Uri,
        defaultMimeType: String
    ): String = jellyfinTranscodeService.contentType(path.toString()) ?: defaultMimeType

    override suspend fun downloadInfo(song: Song): DownloadInfo? = streamUrls.downloadSource(song)?.let { DownloadInfo(it.url.toUri(), it.mimeType) }

    override suspend fun downloadFallbackInfo(
        song: Song,
        responseCode: Int
    ): DownloadInfo? = streamUrls.downloadFallback(song, responseCode)?.let { DownloadInfo(it.url.toUri(), it.mimeType) }

    override fun downloadsAsTranscode(song: Song): Boolean = !jellyfinAuthenticationManager.directPlays(song.audioCodec)
}
