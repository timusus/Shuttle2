package com.simplecityapps.provider.emby

import android.net.Uri
import androidx.core.net.toUri
import com.simplecityapps.mediaprovider.DownloadInfo
import com.simplecityapps.mediaprovider.MediaInfo
import com.simplecityapps.mediaprovider.MediaInfoProvider
import com.simplecityapps.mediaprovider.StreamingBitrateCap
import com.simplecityapps.provider.emby.http.EmbyTranscodeService
import com.simplecityapps.shuttle.model.Song
import dev.zacsweers.metro.Inject

class EmbyMediaInfoProvider
@Inject
constructor(
    private val embyAuthenticationManager: EmbyAuthenticationManager,
    private val embyTranscodeService: EmbyTranscodeService,
    private val streamingBitrateCap: StreamingBitrateCap
) : MediaInfoProvider {
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
     * String form of [getMediaInfo]'s path, capped by the current [StreamingBitrateCap], kept separate so tests can
     * assert on it without pulling Robolectric into this module for `Uri.parse`.
     */
    @Throws(IllegalStateException::class)
    internal fun buildPlaybackPathString(song: Song): String {
        val authenticatedCredentials = embyAuthenticationManager.getAuthenticatedCredentials()
            ?: throw IllegalStateException("Failed to authenticate")
        return embyAuthenticationManager.buildEmbyPath(
            itemId = song.path.substringAfterLast('/'),
            authenticatedCredentials = authenticatedCredentials,
            maxBitrateKbps = streamingBitrateCap.maxBitrateKbps()
        ) ?: throw IllegalStateException("Failed to build emby path")
    }

    private suspend fun getMimeType(
        path: Uri,
        defaultMimeType: String
    ): String = embyTranscodeService.contentType(path.toString()) ?: defaultMimeType

    override suspend fun downloadInfo(song: Song): DownloadInfo? {
        val authenticatedCredentials = embyAuthenticationManager.getAuthenticatedCredentials() ?: return null
        val itemId = Uri.parse(song.path).pathSegments.last()
        val uri = embyAuthenticationManager.buildDownloadPath(itemId, authenticatedCredentials)?.toUri() ?: return null
        return DownloadInfo(uri, song.mimeType)
    }

    override suspend fun downloadFallbackUri(
        path: String,
        responseCode: Int
    ): Uri? = buildFallbackPathString(path, responseCode)?.toUri()

    /**
     * String form of [downloadFallbackUri]'s path, kept separate so tests can assert on it without
     * pulling Robolectric into this module for `Uri.parse`. A 403 means the server has actually
     * revoked download permission, so it's persisted; a 401 can also mean the cached session
     * expired, so it isn't.
     */
    internal suspend fun buildFallbackPathString(
        path: String,
        responseCode: Int
    ): String? {
        val authenticatedCredentials = embyAuthenticationManager.getAuthenticatedCredentials() ?: return null
        if (responseCode == 403) {
            embyAuthenticationManager.disableDownloadPermission()
        }
        val itemId = path.substringAfterLast('/')
        return embyAuthenticationManager.buildStreamPath(itemId, authenticatedCredentials)
    }
}
