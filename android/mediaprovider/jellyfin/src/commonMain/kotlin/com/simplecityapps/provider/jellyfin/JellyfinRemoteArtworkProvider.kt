package com.simplecityapps.provider.jellyfin

import com.simplecityapps.mediaprovider.RemoteArtworkProvider
import com.simplecityapps.mediaprovider.server.ServerCredentialStore
import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.provider.jellyfin.http.ItemsService
import com.simplecityapps.shuttle.model.Song
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Named
import io.ktor.http.parseUrl

/** Artwork urls on the signed-in Jellyfin server; null when the server doesn't know the song's album, or when signed out. */
class JellyfinRemoteArtworkProvider
@Inject
constructor(
    private val jellyfinAuthenticationManager: JellyfinAuthenticationManager,
    private val itemsService: ItemsService,
    @Named("JellyfinCredentialStore") private val credentialStore: ServerCredentialStore
) : RemoteArtworkProvider {
    override fun handles(scheme: String?): Boolean = scheme == "jellyfin"

    override suspend fun getAlbumArtworkUrl(song: Song): String? {
        val itemId = parseUrl(song.path)?.segments?.lastOrNull() ?: return null
        val address = credentialStore.address ?: return null
        val authenticatedCredentials = jellyfinAuthenticationManager.getAuthenticatedCredentials() ?: return null

        val result =
            jellyfinAuthenticationManager.checkSession(
                authenticatedCredentials,
                itemsService.item(
                    address,
                    jellyfinAuthenticationManager.authorizationHeader(authenticatedCredentials),
                    authenticatedCredentials.userId,
                    itemId
                )
            )
        if (result is NetworkResult.Success) {
            val albumId = result.body.albumId ?: return null
            return primaryImageUrl(address, albumId)
        }

        return null
    }

    /** The artist item's own primary image: the song's item needn't be asked, the artist's id being known already. */
    override suspend fun getArtistArtworkUrl(
        song: Song,
        serverArtistId: String
    ): String? {
        val address = credentialStore.address ?: return null
        if (jellyfinAuthenticationManager.getAuthenticatedCredentials() == null) return null
        return primaryImageUrl(address, serverArtistId)
    }

    private fun primaryImageUrl(
        address: String,
        itemId: String
    ): String = "$address/Items/$itemId/Images/Primary?maxWidth=1000&maxHeight=1000"
}
