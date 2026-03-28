package com.simplecityapps.mediaprovider

import android.net.Uri
import com.simplecityapps.shuttle.model.Song
import java.io.File

data class MediaInfo(val path: Uri, val mimeType: String, val isRemote: Boolean)

interface MediaInfoProvider {
    @Throws(IllegalStateException::class)
    fun handles(uri: Uri): Boolean

    suspend fun getMediaInfo(
        song: Song,
        castCompatibilityMode: Boolean = false
    ): MediaInfo
}

class AggregateMediaInfoProvider(val providers: MutableSet<MediaInfoProvider> = mutableSetOf()) : MediaInfoProvider {
    fun addProvider(provider: MediaInfoProvider) {
        providers.add(provider)
    }

    fun removeProvider(provider: MediaInfoProvider) {
        providers.remove(provider)
    }

    override fun handles(uri: Uri): Boolean = true

    override suspend fun getMediaInfo(
        song: Song,
        castCompatibilityMode: Boolean
    ): MediaInfo {
        val uri: Uri =
            if (song.path.startsWith("content://")) {
                Uri.parse(song.path)
            } else {
                Uri.fromFile(File(song.path))
            }
        return providers.firstOrNull { it.handles(uri) }?.getMediaInfo(song, castCompatibilityMode)
            ?: MediaInfo(path = uri, mimeType = song.mimeType, isRemote = false)
    }
}
