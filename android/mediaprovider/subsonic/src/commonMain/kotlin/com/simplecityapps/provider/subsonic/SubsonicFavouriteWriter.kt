package com.simplecityapps.provider.subsonic

import com.simplecityapps.mediaprovider.FavouriteWriter
import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.provider.subsonic.http.SubsonicService
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import dev.zacsweers.metro.Inject

/** Writes a favourite as a star: `star` marks it, `unstar` clears it. */
@Inject
class SubsonicFavouriteWriter(
    private val authenticationManager: SubsonicAuthenticationManager,
    private val service: SubsonicService
) : FavouriteWriter {
    override fun handles(song: Song): Boolean = song.mediaProvider == MediaProviderType.Subsonic

    override suspend fun setFavourite(song: Song, favourite: Boolean): Boolean {
        val id = song.externalId ?: return false
        val result = authenticationManager.request { address, auth -> if (favourite) service.star(address, auth, id) else service.unstar(address, auth, id) }
        return result is NetworkResult.Success
    }
}
