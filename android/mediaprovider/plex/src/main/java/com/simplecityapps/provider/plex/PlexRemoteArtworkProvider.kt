package com.simplecityapps.provider.plex

import android.net.Uri
import com.simplecityapps.mediaprovider.RemoteArtworkProvider
import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.provider.plex.http.ItemsService
import com.simplecityapps.provider.plex.http.Metadata
import com.simplecityapps.provider.plex.http.item
import com.simplecityapps.shuttle.model.Song
import javax.inject.Inject

class PlexRemoteArtworkProvider
@Inject
constructor(
    private val authenticationManager: PlexAuthenticationManager,
    private val itemsService: ItemsService
) : RemoteArtworkProvider {
    override fun handles(uri: Uri): Boolean = uri.scheme == "plex"

    override suspend fun getAlbumArtworkUrl(song: Song): String? = artworkUrl(song) { metadata -> metadata.parentThumb ?: metadata.thumb }

    override suspend fun getArtistArtworkUrl(song: Song): String? = artworkUrl(song) { metadata -> metadata.grandparentThumb }

    private suspend fun artworkUrl(
        song: Song,
        thumb: (Metadata) -> String?
    ): String? {
        val address = authenticationManager.getAddress() ?: return null
        val credentials = authenticationManager.getAuthenticatedCredentials() ?: return null
        val ratingKey = plexRatingKey(song.path) ?: return null

        val result = itemsService.item(url = address, token = credentials.accessToken, key = "$METADATA_PATH$ratingKey")
        if (result is NetworkResult.Success) {
            val path = result.body.mediaContainer.metadata?.firstOrNull()?.let(thumb) ?: return null
            return "$address$path?X-Plex-Token=${credentials.accessToken}"
        }

        return null
    }
}
