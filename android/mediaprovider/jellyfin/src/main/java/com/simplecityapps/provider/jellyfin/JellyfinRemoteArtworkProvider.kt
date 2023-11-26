package com.simplecityapps.provider.jellyfin

import android.net.Uri
import com.simplecityapps.mediaprovider.RemoteArtworkProvider
import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.provider.jellyfin.http.Item
import com.simplecityapps.provider.jellyfin.http.ItemsService
import com.simplecityapps.provider.jellyfin.http.item
import com.simplecityapps.shuttle.model.Song
import javax.inject.Inject

class JellyfinRemoteArtworkProvider
@Inject
constructor(
    private val jellyfinAuthenticationManager: JellyfinAuthenticationManager,
    private val itemsService: ItemsService,
    private val credentialStore: CredentialStore
) : RemoteArtworkProvider {
    override fun handles(uri: Uri): Boolean {
        return uri.scheme == "jellyfin"
    }

    override suspend fun getAlbumArtworkUrl(song: Song): String? {
        val itemId = Uri.parse(song.path).pathSegments.last() ?: return null

        val address = credentialStore.address ?: return null

        val authenticatedCredentials = jellyfinAuthenticationManager.getAuthenticatedCredentials() ?: return null

        val result: NetworkResult<Item> =
            itemsService.item(
                address,
                authenticatedCredentials.accessToken,
                authenticatedCredentials.userId,
                itemId
            )
        if (result is NetworkResult.Success && result.body.albumId != null) {
            return "$address/Items/${result.body.albumId}/Images/Primary?maxWidth=1000&maxHeight=1000"
        }

        return null
    }

    override suspend fun getArtistArtworkUrl(song: Song): String? {
        val itemId = Uri.parse(song.path).pathSegments.last() ?: return null

        val address = credentialStore.address ?: return null

        val authenticatedCredentials = jellyfinAuthenticationManager.getAuthenticatedCredentials() ?: return null

        val result: NetworkResult<Item> =
            itemsService.item(
                address,
                authenticatedCredentials.accessToken,
                authenticatedCredentials.userId,
                itemId
            )
        if (result is NetworkResult.Success && result.body.artistItems.isNotEmpty()) {
            return "$address/Items/${result.body.artistItems.firstOrNull()?.id}/Images/Primary?maxWidth=1000&maxHeight=1000"
        }

        return null
    }
}
