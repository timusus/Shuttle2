package com.simplecityapps.provider.plex

import com.simplecityapps.mediaprovider.RemoteArtworkProvider
import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.provider.plex.http.ItemsService
import com.simplecityapps.provider.plex.http.Metadata
import com.simplecityapps.provider.plex.http.PLEX_TOKEN
import com.simplecityapps.shuttle.model.Song
import dev.zacsweers.metro.Inject

/** Artwork urls on the signed-in Plex server; they carry no token, which [PlexArtworkTokenInterceptor] (Android) or [requestHeaders] (iOS) adds at request time. */
class PlexRemoteArtworkProvider
@Inject
constructor(
    private val authenticationManager: PlexAuthenticationManager,
    private val itemsService: ItemsService
) : RemoteArtworkProvider {
    override fun handles(scheme: String?): Boolean = scheme == "plex"

    override suspend fun getAlbumArtworkUrl(song: Song): String? = plexRatingKey(song.path)?.let { ratingKey -> artworkUrl(ratingKey) { metadata -> metadata.parentThumb ?: metadata.thumb } }

    /** The artist's own thumb, from their metadata: [serverArtistId] is their rating key. */
    override suspend fun getArtistArtworkUrl(
        song: Song,
        serverArtistId: String
    ): String? = artworkUrl(serverArtistId) { metadata -> metadata.thumb }

    override fun requestHeaders(url: String): Map<String, String> {
        val token = plexArtworkToken(url, authenticationManager.getAddress(), authenticationManager.getAuthenticatedCredentials()?.accessToken)
        return if (token == null) emptyMap() else mapOf(PLEX_TOKEN to token)
    }

    /** [thumb] of the item with [ratingKey]. */
    private suspend fun artworkUrl(
        ratingKey: String,
        thumb: (Metadata) -> String?
    ): String? {
        val address = authenticationManager.getAddress() ?: return null
        val credentials = authenticationManager.getAuthenticatedCredentials() ?: return null

        val result = authenticationManager.checkSession(credentials, itemsService.item(url = address, token = credentials.accessToken, key = "$METADATA_PATH$ratingKey"))
        if (result is NetworkResult.Success) {
            val path = result.body.mediaContainer.metadata?.firstOrNull()?.let(thumb) ?: return null
            return "$address$path"
        }

        return null
    }
}
