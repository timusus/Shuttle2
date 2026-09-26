package com.simplecityapps.provider.emby

import android.net.Uri
import com.simplecityapps.mediaprovider.RemoteArtworkProvider
import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.provider.emby.http.Item
import com.simplecityapps.provider.emby.http.ItemsService
import com.simplecityapps.provider.emby.http.item
import com.simplecityapps.shuttle.model.Song
import javax.inject.Inject

/** Artwork urls on the signed-in Emby server; null when the server doesn't know the song's album or artist. */
class EmbyRemoteArtworkProvider
@Inject
constructor(
    private val embyAuthenticationManager: EmbyAuthenticationManager,
    private val credentialStore: CredentialStore,
    private val itemsService: ItemsService
) : RemoteArtworkProvider {
    override fun handles(uri: Uri): Boolean = uri.scheme == "emby"

    override suspend fun getAlbumArtworkUrl(song: Song): String? = artworkUrl(song) { item -> item.albumId }

    override suspend fun getArtistArtworkUrl(song: Song): String? = artworkUrl(song) { item -> item.artistItems.firstOrNull()?.id }

    private suspend fun artworkUrl(
        song: Song,
        imageItemId: (Item) -> Int?
    ): String? {
        val itemId = Uri.parse(song.path).pathSegments.lastOrNull() ?: return null
        val address = credentialStore.address ?: return null
        val authenticatedCredentials = embyAuthenticationManager.getAuthenticatedCredentials() ?: return null

        val result = itemsService.item(address, authenticatedCredentials.accessToken, authenticatedCredentials.userId, itemId)
        if (result is NetworkResult.Success) {
            val id = imageItemId(result.body) ?: return null
            return "$address/Items/$id/Images/Primary?maxWidth=1000&maxHeight=1000"
        }

        return null
    }
}
