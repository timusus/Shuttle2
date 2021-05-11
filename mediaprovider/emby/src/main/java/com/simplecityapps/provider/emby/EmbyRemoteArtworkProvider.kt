package com.simplecityapps.provider.emby

import android.net.Uri
import com.simplecityapps.mediaprovider.RemoteArtworkProvider
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.provider.emby.http.Item
import com.simplecityapps.provider.emby.http.ItemsService
import com.simplecityapps.provider.emby.http.item
import javax.inject.Inject

class EmbyRemoteArtworkProvider @Inject constructor(
    private val embyAuthenticationManager: EmbyAuthenticationManager,
    private val credentialStore: CredentialStore,
    private val itemsService: ItemsService,
) : RemoteArtworkProvider {

    override fun handles(uri: Uri): Boolean {
        return uri.scheme == "emby"
    }

    override suspend fun getAlbumArtworkUrl(song: Song): String? {
        val itemId = Uri.parse(song.path).pathSegments.last() ?: return null

        val address = credentialStore.address ?: return null

        val authenticatedCredentials = embyAuthenticationManager.getAuthenticatedCredentials() ?: return null

        val result: NetworkResult<Item> = itemsService.item(
            address,
            authenticatedCredentials.accessToken,
            authenticatedCredentials.userId,
            itemId
        )
        if (result is NetworkResult.Success) {
            return "$address/Items/${result.body.albumId}/Images/Primary?maxWidth=1000&maxHeight=1000"
        }

        return null
    }

    override suspend fun getArtistArtworkUrl(song: Song): String? {
        val itemId = Uri.parse(song.path).pathSegments.last() ?: return null

        val address = credentialStore.address ?: return null

        val authenticatedCredentials = embyAuthenticationManager.getAuthenticatedCredentials() ?: return null

        val result: NetworkResult<Item> = itemsService.item(
            address,
            authenticatedCredentials.accessToken,
            authenticatedCredentials.userId,
            itemId
        )
        if (result is NetworkResult.Success) {
            return "$address/Items/${result.body.artistItems.firstOrNull()?.id}/Images/Primary?maxWidth=1000&maxHeight=1000"
        }

        return null
    }
}