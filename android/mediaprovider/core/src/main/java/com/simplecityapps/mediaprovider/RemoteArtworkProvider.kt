package com.simplecityapps.mediaprovider

import android.net.Uri
import com.simplecityapps.shuttle.model.Song
import javax.inject.Qualifier

interface RemoteArtworkProvider {
    fun handles(uri: Uri): Boolean

    suspend fun getAlbumArtworkUrl(song: Song): String?

    suspend fun getArtistArtworkUrl(song: Song): String?
}

/** Routes each call to the first of [providers] that handles the song's uri; each provider module contributes its own via `@IntoSet`. */
class AggregateRemoteArtworkProvider(private val providers: Set<RemoteArtworkProvider>) : RemoteArtworkProvider {
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

/**
 * Qualifies the OkHttp interceptors a provider module contributes via `@IntoSet` to authenticate its artwork requests. Artwork urls stay free of
 * credentials, since they end up in logs; each interceptor adds its server's credentials at request time, and only for that server.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RemoteArtworkInterceptor
