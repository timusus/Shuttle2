package com.simplecityapps.mediaprovider

import com.simplecityapps.shuttle.model.Song
import dev.zacsweers.metro.Qualifier

interface RemoteArtworkProvider {
    /** Whether this provider handles a song whose path has [scheme] (`null` for a path with none). */
    fun handles(scheme: String?): Boolean

    suspend fun getAlbumArtworkUrl(song: Song): String?

    /**
     * The image of the artist the server knows as [serverArtistId], one of [song]'s artists ([com.simplecityapps.shuttle.model.serverArtistId]):
     * the caller names the artist, so the image is never another artist's on the same song (#653).
     */
    suspend fun getArtistArtworkUrl(
        song: Song,
        serverArtistId: String
    ): String?

    /**
     * The headers a request for [url], one of this provider's artwork urls, must carry to be authenticated: none by default.
     * Artwork urls stay free of credentials, so a loader that can't intercept requests (iOS's) asks for them here.
     */
    fun requestHeaders(url: String): Map<String, String> = emptyMap()

    /**
     * The url to request [url], one of this provider's artwork urls, at: [url] itself by default, or null when it can't be
     * requested now. A provider whose server takes credentials only in the url (Subsonic) signs it here on iOS, where the
     * loader can't sign a request on its way out; [url] stays its image's cache key, since the signature changes each time.
     */
    fun requestUrl(url: String): String? = url
}

/** Routes each call to the first of [providers] that handles the song's uri; each provider module contributes its own via `@IntoSet`. */
class AggregateRemoteArtworkProvider(private val providers: Set<RemoteArtworkProvider>) : RemoteArtworkProvider {
    override fun handles(scheme: String?): Boolean = providers.any { it.handles(scheme) }

    override suspend fun getAlbumArtworkUrl(song: Song): String? = providers.firstOrNull { it.handles(schemeOf(song.path)) }?.getAlbumArtworkUrl(song)

    override suspend fun getArtistArtworkUrl(
        song: Song,
        serverArtistId: String
    ): String? = providers.firstOrNull { it.handles(schemeOf(song.path)) }?.getArtistArtworkUrl(song, serverArtistId)

    /** Each provider adds headers only for its own server's urls, so the union is the headers for [url]'s. */
    override fun requestHeaders(url: String): Map<String, String> = providers.fold(emptyMap()) { headers, provider -> headers + provider.requestHeaders(url) }

    /** Each provider changes only its own server's urls, so passing [url] through them all gives its request url. */
    override fun requestUrl(url: String): String? = providers.fold<RemoteArtworkProvider, String?>(url) { requestUrl, provider -> requestUrl?.let(provider::requestUrl) }
}

/**
 * Qualifies the OkHttp interceptors a provider module contributes via `@IntoSet` to authenticate its artwork requests. Artwork urls stay free of
 * credentials, since they end up in logs; each interceptor adds its server's credentials at request time, and only for that server.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RemoteArtworkInterceptor
