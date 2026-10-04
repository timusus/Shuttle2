package com.simplecityapps.provider.plex.http

import com.simplecityapps.networking.networkResult
import com.simplecityapps.networking.retrofit.NetworkResult
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.put
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A track's own rating, from `/library/metadata/{ratingKey}`. Absent when the user hasn't rated it. */
@Serializable
data class UserRatingResult(
    @SerialName("MediaContainer") val mediaContainer: UserRatingContainer = UserRatingContainer()
) {
    val userRating: Double? get() = mediaContainer.metadata.firstOrNull()?.userRating
}

@Serializable
data class UserRatingContainer(
    @SerialName("Metadata") val metadata: List<UserRatingMetadata> = emptyList()
)

@Serializable
data class UserRatingMetadata(
    @SerialName("userRating") val userRating: Double? = null
)

/** Plex's rating endpoints, which carry a Plex favourite as a 10 out of 10 rating. */
class FavouriteService(private val client: HttpClient) {
    /** Sets the track's rating to [rating] (0 to 10); -1 clears it. Only the status matters, so the body is discarded. */
    suspend fun rate(
        url: String,
        token: String,
        ratingKey: String,
        identifier: String,
        rating: Int
    ): NetworkResult<Unit> = client.networkResult {
        put(url) {
            header(PLEX_TOKEN, token)
            parameter("key", ratingKey)
            parameter("identifier", identifier)
            parameter("rating", rating)
        }
    }

    suspend fun userRating(
        url: String,
        token: String
    ): NetworkResult<UserRatingResult> = client.networkResult {
        get(url) { header(PLEX_TOKEN, token) }
    }
}
