package com.simplecityapps.provider.subsonic

import com.simplecityapps.mediaprovider.RemoteArtworkProvider
import com.simplecityapps.provider.subsonic.http.SubsonicService
import com.simplecityapps.shuttle.model.Song
import dev.zacsweers.metro.Inject

/**
 * [SubsonicRemoteArtworkProvider]'s urls, signed, for a platform whose image loader can't sign a request on its way out
 * (iOS). Subsonic takes credentials only as query parameters, never a header ([RemoteArtworkProvider.requestHeaders]),
 * so each url carries a fresh salt and is no use as a cache key.
 */
@Inject
class SignedSubsonicArtworkProvider(
    private val artwork: SubsonicRemoteArtworkProvider,
    private val authenticationManager: SubsonicAuthenticationManager,
    private val service: SubsonicService
) : RemoteArtworkProvider by artwork {
    override suspend fun getAlbumArtworkUrl(song: Song): String? = artwork.getAlbumArtworkUrl(song)?.let(::sign)

    override suspend fun getArtistArtworkUrl(song: Song): String? = artwork.getArtistArtworkUrl(song)?.let(::sign)

    private fun sign(url: String): String? {
        val credentials = authenticationManager.getAuthenticatedCredentials() ?: return null
        return service.sign(url, authenticationManager.auth(credentials))
    }
}
