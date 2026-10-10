package com.simplecityapps.mediaprovider.server.mediabrowser

import com.simplecityapps.mediaprovider.ClientIdentity
import com.simplecityapps.mediaprovider.FavouriteWriter
import com.simplecityapps.networking.networkResult
import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.shuttle.model.Song
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.post

/**
 * Writes a favourite through `/Users/{userId}/FavoriteItems/{itemId}`: POST marks it, DELETE clears it. The server
 * answers with the item's user data; only the status matters, so the body is discarded.
 *
 * TODO(#347): Jellyfin has served this legacy route since 10.9 only for compatibility; move it to
 * `/UserFavoriteItems/{itemId}` once the minimum supported server version allows it. Emby stays on this one.
 */
class MediaBrowserFavouriteWriter(
    private val authenticationManager: MediaBrowserAuthenticationManager,
    private val client: HttpClient,
    private val clientIdentity: ClientIdentity
) : FavouriteWriter {
    private val server = authenticationManager.server

    override fun handles(song: Song): Boolean = song.mediaProvider == server.type

    override suspend fun setFavourite(
        song: Song,
        favourite: Boolean
    ): Boolean {
        val address = authenticationManager.getAddress() ?: return false
        val credentials = authenticationManager.getAuthenticatedCredentials() ?: return false
        val itemId = song.externalId ?: return false
        val url = "$address${server.apiPrefix}/Users/${credentials.userId}/FavoriteItems/$itemId"
        val result: NetworkResult<Unit> = client.networkResult {
            if (favourite) {
                post(url) { server.authorizeSession(this, credentials.accessToken, clientIdentity) }
            } else {
                delete(url) { server.authorizeSession(this, credentials.accessToken, clientIdentity) }
            }
        }
        return authenticationManager.checkSession(credentials, result) is NetworkResult.Success
    }
}
