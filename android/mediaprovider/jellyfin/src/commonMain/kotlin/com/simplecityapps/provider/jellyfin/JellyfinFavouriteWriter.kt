package com.simplecityapps.provider.jellyfin

import com.simplecityapps.mediaprovider.FavouriteWriter
import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.provider.jellyfin.http.FavouriteService
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import dev.zacsweers.metro.Inject

/**
 * Writes a favourite through `/Users/{userId}/FavoriteItems/{itemId}`: POST marks it, DELETE clears it.
 *
 * TODO(#347): this is Jellyfin's legacy route, still served but obsolete since 10.9; move to `/UserFavoriteItems/{itemId}`
 * once the minimum supported server version allows it.
 */
class JellyfinFavouriteWriter
@Inject
constructor(
    private val authenticationManager: JellyfinAuthenticationManager,
    private val favouriteService: FavouriteService
) : FavouriteWriter {
    override fun handles(song: Song): Boolean = song.mediaProvider == MediaProviderType.Jellyfin

    override suspend fun setFavourite(
        song: Song,
        favourite: Boolean
    ): Boolean {
        val address = authenticationManager.getAddress() ?: return false
        val credentials = authenticationManager.getAuthenticatedCredentials() ?: return false
        val itemId = song.externalId ?: return false
        val url = "$address/Users/${credentials.userId}/FavoriteItems/$itemId"
        val authorization = authenticationManager.authorizationHeader(credentials)
        val result = if (favourite) favouriteService.favourite(url, authorization) else favouriteService.unfavourite(url, authorization)
        return authenticationManager.checkSession(credentials, result) is NetworkResult.Success
    }
}
