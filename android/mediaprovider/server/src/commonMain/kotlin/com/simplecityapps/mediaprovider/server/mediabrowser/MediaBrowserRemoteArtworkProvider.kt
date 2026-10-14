package com.simplecityapps.mediaprovider.server.mediabrowser

import com.simplecityapps.mediaprovider.RemoteArtworkProvider
import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.shuttle.model.Song
import io.ktor.http.parseUrl

/** Artwork urls on the signed-in server; the song's own image when its album has none; null when the server doesn't know the song's album, or when signed out. */
open class MediaBrowserRemoteArtworkProvider(
    private val authenticationManager: MediaBrowserAuthenticationManager,
    private val itemsService: ItemsService
) : RemoteArtworkProvider {
    private val scheme = authenticationManager.server.songPathPrefix.substringBefore("://")

    override fun handles(scheme: String?): Boolean = scheme == this.scheme

    override suspend fun getAlbumArtworkUrl(song: Song): String? {
        val itemId = parseUrl(song.path)?.segments?.lastOrNull() ?: return null
        val address = authenticationManager.getAddress() ?: return null
        val authenticatedCredentials = authenticationManager.getAuthenticatedCredentials() ?: return null

        val result = authenticationManager.checkSession(authenticatedCredentials, itemsService.item(address, authenticatedCredentials.accessToken, authenticatedCredentials.userId, itemId))
        if (result is NetworkResult.Success) {
            val item = result.body
            // An album with no image of its own 404s, while its songs may carry one each
            if (item.albumPrimaryImageTag == null && item.imageTags.containsKey("Primary")) return primaryImageUrl(address, item.id)
            val albumId = item.albumId ?: return null
            return primaryImageUrl(address, albumId)
        }

        return null
    }

    /** The artist item's own primary image: the song's item needn't be asked, the artist's id being known already. */
    override suspend fun getArtistArtworkUrl(
        song: Song,
        serverArtistId: String
    ): String? {
        val address = authenticationManager.getAddress() ?: return null
        if (authenticationManager.getAuthenticatedCredentials() == null) return null
        return primaryImageUrl(address, serverArtistId)
    }

    private fun primaryImageUrl(
        address: String,
        itemId: String
    ): String = "$address/Items/$itemId/Images/Primary?maxWidth=1000&maxHeight=1000"
}
