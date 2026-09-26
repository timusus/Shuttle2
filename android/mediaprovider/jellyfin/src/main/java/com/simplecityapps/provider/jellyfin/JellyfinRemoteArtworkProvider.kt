package com.simplecityapps.provider.jellyfin

import android.net.Uri
import com.simplecityapps.mediaprovider.RemoteArtworkProvider
import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.provider.jellyfin.http.Item
import com.simplecityapps.provider.jellyfin.http.ItemsService
import com.simplecityapps.provider.jellyfin.http.item
import com.simplecityapps.shuttle.model.Song
import javax.inject.Inject

/** Artwork urls on the signed-in Jellyfin server; null when the server doesn't know the song's album or artist. */
class JellyfinRemoteArtworkProvider
@Inject
constructor(
    private val jellyfinAuthenticationManager: JellyfinAuthenticationManager,
    private val itemsService: ItemsService,
    private val credentialStore: CredentialStore
) : RemoteArtworkProvider {
    override fun handles(uri: Uri): Boolean = uri.scheme == "jellyfin"

    override suspend fun getAlbumArtworkUrl(song: Song): String? = artworkUrl(song) { item -> item.albumId }

    override suspend fun getArtistArtworkUrl(song: Song): String? = artworkUrl(song) { item -> item.artistItems.firstOrNull()?.id }

    private suspend fun artworkUrl(
        song: Song,
        imageItemId: (Item) -> String?
    ): String? {
        val itemId = Uri.parse(song.path).pathSegments.lastOrNull() ?: return null
        val address = credentialStore.address ?: return null
        val authenticatedCredentials = jellyfinAuthenticationManager.getAuthenticatedCredentials() ?: return null

        val result =
            itemsService.item(
                address,
                jellyfinAuthenticationManager.authorizationHeader(authenticatedCredentials),
                authenticatedCredentials.userId,
                itemId
            )
        if (result is NetworkResult.Success) {
            val id = imageItemId(result.body) ?: return null
            return "$address/Items/$id/Images/Primary?maxWidth=1000&maxHeight=1000"
        }

        return null
    }
}
