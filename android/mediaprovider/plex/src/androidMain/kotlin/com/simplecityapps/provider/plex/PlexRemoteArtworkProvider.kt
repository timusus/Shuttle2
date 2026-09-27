package com.simplecityapps.provider.plex

import com.simplecityapps.mediaprovider.RemoteArtworkProvider
import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.provider.plex.http.ItemsService
import com.simplecityapps.provider.plex.http.Metadata
import com.simplecityapps.shuttle.model.Song
import dev.zacsweers.metro.Inject

/** Artwork urls on the signed-in Plex server; they carry no token, which [PlexArtworkTokenInterceptor] adds at request time. */
class PlexRemoteArtworkProvider
@Inject
constructor(
    private val authenticationManager: PlexAuthenticationManager,
    private val itemsService: ItemsService
) : RemoteArtworkProvider {
    override fun handles(scheme: String?): Boolean = scheme == "plex"

    override suspend fun getAlbumArtworkUrl(song: Song): String? = artworkUrl(song) { metadata -> metadata.parentThumb ?: metadata.thumb }

    override suspend fun getArtistArtworkUrl(song: Song): String? = artworkUrl(song) { metadata -> metadata.grandparentThumb }

    private suspend fun artworkUrl(
        song: Song,
        thumb: (Metadata) -> String?
    ): String? {
        val address = authenticationManager.getAddress() ?: return null
        val credentials = authenticationManager.getAuthenticatedCredentials() ?: return null
        val ratingKey = plexRatingKey(song.path) ?: return null

        val result = authenticationManager.checkSession(credentials, itemsService.item(url = address, token = credentials.accessToken, key = "$METADATA_PATH$ratingKey"))
        if (result is NetworkResult.Success) {
            val path = result.body.mediaContainer.metadata?.firstOrNull()?.let(thumb) ?: return null
            return "$address$path"
        }

        return null
    }
}
