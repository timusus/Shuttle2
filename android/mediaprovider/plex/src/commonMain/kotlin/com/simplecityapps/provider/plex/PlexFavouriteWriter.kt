package com.simplecityapps.provider.plex

import com.simplecityapps.mediaprovider.FavouriteWriter
import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.provider.plex.http.FavouriteService
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import dev.zacsweers.metro.Inject

/**
 * Plex has no favourite flag on a track, only a 0 to 10 rating, so a favourite is a rating of 10 (a full five stars in
 * Plex's own apps) through `/:/rate`.
 *
 * Hearting a track replaces any other rating the user gave it (a 6, say) with 10: that is the price of mapping a
 * favourite onto a rating, and the owner's decision.
 *
 * Unfavouriting clears the rating only if it is still 10: a track the user has since rated 6 elsewhere isn't a favourite
 * any more in the sense we wrote, and wiping their rating would destroy something we didn't put there. A track that
 * isn't rated at all needs nothing sent.
 */
class PlexFavouriteWriter
@Inject
constructor(
    private val authenticationManager: PlexAuthenticationManager,
    private val favouriteService: FavouriteService
) : FavouriteWriter {
    override fun handles(song: Song): Boolean = song.mediaProvider == MediaProviderType.Plex

    override suspend fun setFavourite(
        song: Song,
        favourite: Boolean
    ): Boolean {
        val address = authenticationManager.getAddress() ?: return false
        val credentials = authenticationManager.getAuthenticatedCredentials() ?: return false
        val ratingKey = plexRatingKey(song.path) ?: return false

        if (!favourite) {
            val current = favouriteService.userRating("$address$METADATA_PATH$ratingKey", credentials.accessToken)
            val rating = when (val checked = authenticationManager.checkSession(credentials, current)) {
                is NetworkResult.Success -> checked.body.userRating
                else -> return false
            }
            if (rating != FAVOURITE_RATING.toDouble()) return true
        }

        val result = favouriteService.rate(
            url = "$address/:/rate",
            token = credentials.accessToken,
            ratingKey = ratingKey,
            identifier = LIBRARY_IDENTIFIER,
            rating = if (favourite) FAVOURITE_RATING else CLEAR_RATING
        )
        return authenticationManager.checkSession(credentials, result) is NetworkResult.Success
    }

    private companion object {
        const val FAVOURITE_RATING = 10

        // What python-plexapi sends to remove a rating; not documented by Plex.
        const val CLEAR_RATING = -1
    }
}
