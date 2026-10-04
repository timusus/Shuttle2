package com.simplecityapps.provider.emby

import com.simplecityapps.mediaprovider.FavouriteWriter
import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.provider.emby.http.FavouriteService
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import dev.zacsweers.metro.Inject

/**
 * Writes a favourite through `/Users/{userId}/FavoriteItems/{itemId}`: POST marks it, DELETE clears it.
 *
 * TODO(#347): Jellyfin's equivalent of this route is obsolete; keep Emby on it, and revisit with the move to
 * Jellyfin's `/UserFavoriteItems/{itemId}`.
 */
class EmbyFavouriteWriter
@Inject
constructor(
    private val authenticationManager: EmbyAuthenticationManager,
    private val favouriteService: FavouriteService
) : FavouriteWriter {
    override fun handles(song: Song): Boolean = song.mediaProvider == MediaProviderType.Emby

    override suspend fun setFavourite(
        song: Song,
        favourite: Boolean
    ): Boolean {
        val address = authenticationManager.getAddress() ?: return false
        val credentials = authenticationManager.getAuthenticatedCredentials() ?: return false
        val itemId = song.externalId ?: return false
        val url = "$address/emby/Users/${credentials.userId}/FavoriteItems/$itemId"
        val authorization = authenticationManager.clientAuthorizationHeader()
        val result = if (favourite) {
            favouriteService.favourite(url, credentials.accessToken, authorization)
        } else {
            favouriteService.unfavourite(url, credentials.accessToken, authorization)
        }
        return authenticationManager.checkSession(credentials, result) is NetworkResult.Success
    }
}
