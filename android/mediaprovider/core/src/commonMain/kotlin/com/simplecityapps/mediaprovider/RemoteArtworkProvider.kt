package com.simplecityapps.mediaprovider

import com.simplecityapps.shuttle.model.Song
import dev.zacsweers.metro.Qualifier

interface RemoteArtworkProvider {
    /** Whether this provider handles a song whose path has [scheme] (`null` for a path with none). */
    fun handles(scheme: String?): Boolean

    suspend fun getAlbumArtworkUrl(song: Song): String?

    suspend fun getArtistArtworkUrl(song: Song): String?
}

/** Routes each call to the first of [providers] that handles the song's uri; each provider module contributes its own via `@IntoSet`. */
class AggregateRemoteArtworkProvider(private val providers: Set<RemoteArtworkProvider>) : RemoteArtworkProvider {
    override fun handles(scheme: String?): Boolean = providers.any { it.handles(scheme) }

    override suspend fun getAlbumArtworkUrl(song: Song): String? = providers.firstOrNull { it.handles(schemeOf(song.path)) }?.getAlbumArtworkUrl(song)

    override suspend fun getArtistArtworkUrl(song: Song): String? = providers.firstOrNull { it.handles(schemeOf(song.path)) }?.getArtistArtworkUrl(song)
}

/**
 * Qualifies the OkHttp interceptors a provider module contributes via `@IntoSet` to authenticate its artwork requests. Artwork urls stay free of
 * credentials, since they end up in logs; each interceptor adds its server's credentials at request time, and only for that server.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RemoteArtworkInterceptor
