package com.simplecityapps.provider.subsonic

import com.simplecityapps.mediaprovider.RemoteArtworkProvider
import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.provider.subsonic.http.SubsonicService
import com.simplecityapps.shuttle.model.Song
import dev.zacsweers.metro.Inject

/**
 * `getCoverArt` urls on the signed-in server. They carry no credentials: the cover art id names the art itself (with a
 * hash of it, on Navidrome), so the url keys the image cache, and the platform's loader signs the request on its way out
 * (SubsonicArtworkAuthInterceptor on Android).
 */
@Inject
class SubsonicRemoteArtworkProvider(
    private val authenticationManager: SubsonicAuthenticationManager,
    private val service: SubsonicService
) : RemoteArtworkProvider {
    override fun handles(scheme: String?): Boolean = scheme == "subsonic"

    /** The song's cover art, which the sync keeps as its artwork version (falling back to the album id). */
    override suspend fun getAlbumArtworkUrl(song: Song): String? {
        val address = authenticationManager.getAddress() ?: return null
        val coverArt = song.artworkVersion ?: song.serverAlbumId ?: return null
        return coverArtUrl(address, coverArt)
    }

    /** The first artist's cover art, from `getArtist`; null when the server has none. */
    override suspend fun getArtistArtworkUrl(song: Song): String? {
        val address = authenticationManager.getAddress() ?: return null
        val artistId = song.serverArtistIds?.firstOrNull() ?: return null
        val result = authenticationManager.request { requestAddress, auth -> service.artist(requestAddress, auth, artistId) }
        val coverArt = (result as? NetworkResult.Success)?.body?.coverArt ?: return null
        return coverArtUrl(address, coverArt)
    }

    private fun coverArtUrl(address: String, coverArt: String): String = service.unsignedUrl(address, "getCoverArt", "id" to coverArt, "size" to ARTWORK_SIZE)

    private companion object {
        const val ARTWORK_SIZE = 1000
    }
}
