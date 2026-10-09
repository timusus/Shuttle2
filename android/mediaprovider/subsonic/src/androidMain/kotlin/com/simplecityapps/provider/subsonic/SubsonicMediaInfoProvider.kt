package com.simplecityapps.provider.subsonic

import android.net.Uri
import androidx.core.net.toUri
import com.simplecityapps.mediaprovider.DownloadInfo
import com.simplecityapps.mediaprovider.MediaInfo
import com.simplecityapps.mediaprovider.MediaInfoProvider
import com.simplecityapps.shuttle.model.Song

/**
 * A Subsonic song's stream for Media3 ([SubsonicStreams.stream], with how a transcode seeks) or Cast
 * ([SubsonicStreams.castStream]), and its download.
 */
class SubsonicMediaInfoProvider(
    private val streams: SubsonicStreams
) : MediaInfoProvider {
    override fun handles(scheme: String?): Boolean = scheme == "subsonic"

    @Throws(IllegalStateException::class)
    override suspend fun getMediaInfo(
        song: Song,
        castCompatibilityMode: Boolean,
        playId: String?
    ): MediaInfo {
        val stream = if (castCompatibilityMode) streams.castStream(song) else streams.stream(song)
        return MediaInfo(
            path = stream.url.toUri(),
            mimeType = stream.mimeType,
            isRemote = true,
            timeSeek = stream.timeSeek
        )
    }

    override suspend fun downloadInfo(song: Song): DownloadInfo? = streams.downloadSource(song)?.let { DownloadInfo(it.url.toUri(), it.mimeType) }

    // Subsonic has no separate download permission to fall back from.
    override suspend fun downloadFallbackInfo(
        song: Song,
        responseCode: Int
    ): DownloadInfo? = null
}
