package com.simplecityapps.mediaprovider

import android.net.Uri
import com.simplecityapps.shuttle.model.Song

interface RemoteArtworkProvider {
    fun handles(uri: Uri): Boolean

    suspend fun getAlbumArtworkUrl(song: Song): String?

    suspend fun getArtistArtworkUrl(song: Song): String?
}

class AggregateRemoteArtworkProvider(val providers: MutableSet<RemoteArtworkProvider>) : RemoteArtworkProvider {
    override fun handles(uri: Uri): Boolean = providers.any { it.handles(uri) }

    override suspend fun getAlbumArtworkUrl(song: Song): String? {
        val uri = Uri.parse(song.path)
        return providers.firstOrNull { it.handles(uri) }?.getAlbumArtworkUrl(song)
    }

    override suspend fun getArtistArtworkUrl(song: Song): String? {
        val uri = Uri.parse(song.path)
        return providers.firstOrNull { it.handles(uri) }?.getArtistArtworkUrl(song)
    }
}
