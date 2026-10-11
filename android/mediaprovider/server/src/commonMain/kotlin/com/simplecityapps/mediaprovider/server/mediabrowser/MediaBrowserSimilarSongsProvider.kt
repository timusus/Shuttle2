package com.simplecityapps.mediaprovider.server.mediabrowser

import com.simplecityapps.mediaprovider.ClientIdentity
import com.simplecityapps.mediaprovider.SimilarSongsProvider
import com.simplecityapps.networking.networkResult
import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.shuttle.model.Song
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter

/** Reads a server's Instant Mix for a song, `/Items/{id}/InstantMix`; Jellyfin and Emby answer with the same item list. */
class MediaBrowserSimilarSongsProvider(
    private val authenticationManager: MediaBrowserAuthenticationManager,
    private val client: HttpClient,
    private val clientIdentity: ClientIdentity
) : SimilarSongsProvider {
    private val server = authenticationManager.server

    override fun handles(song: Song): Boolean = song.mediaProvider == server.type

    override suspend fun similarSongIds(
        seed: Song,
        limit: Int
    ): List<String> {
        val address = authenticationManager.getAddress() ?: return emptyList()
        val credentials = authenticationManager.getAuthenticatedCredentials() ?: return emptyList()
        val itemId = seed.externalId ?: return emptyList()
        val result: NetworkResult<QueryResult> = client.networkResult {
            get("$address${server.apiPrefix}/Items/$itemId/InstantMix") {
                server.authorize(this, credentials.accessToken, clientIdentity)
                parameter(server.itemsParameter("UserId"), credentials.userId)
                parameter(server.itemsParameter("Limit"), limit)
            }
        }
        return when (val checked = authenticationManager.checkSession(credentials, result)) {
            is NetworkResult.Success -> checked.body.instantMixIds()
            is NetworkResult.Failure -> emptyList()
        }
    }
}

/** The ids of the mix's songs in the server's order, each once; the seed's own entry is left for the caller to drop. */
internal fun QueryResult.instantMixIds(): List<String> = items.map { it.id }.distinct()
