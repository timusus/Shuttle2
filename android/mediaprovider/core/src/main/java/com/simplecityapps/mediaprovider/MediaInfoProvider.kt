package com.simplecityapps.mediaprovider

import android.net.Uri
import com.simplecityapps.shuttle.model.Song

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
        val uri = Uri.parse(song.path)
        return providers.firstOrNull { it.handles(uri) }?.getMediaInfo(song, castCompatibilityMode)
            ?: MediaInfo(path = uri, mimeType = song.mimeType, isRemote = false)
    }
}
