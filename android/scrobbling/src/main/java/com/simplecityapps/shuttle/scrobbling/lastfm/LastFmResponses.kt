package com.simplecityapps.shuttle.scrobbling.lastfm

import kotlinx.serialization.Serializable

/**
 * A Last.fm response that carries nothing but, on failure, an [error code](https://www.last.fm/api/errorcodes):
 * `track.updateNowPlaying`'s answer, and the `{"error": 9, "message": "…"}` body Last.fm sends with most 4xx statuses.
 */
@Serializable
data class LastFmBasicResponse(
    val error: Int? = null,
    val message: String? = null
)

/** [auth.getToken](https://www.last.fm/api/show/auth.getToken). */
@Serializable
data class LastFmTokenResponse(
    val token: String? = null,
    val error: Int? = null
)

/** [auth.getSession](https://www.last.fm/api/show/auth.getSession). */
@Serializable
data class LastFmSessionResponse(
    val session: Session? = null,
    val error: Int? = null
) {
    @Serializable
    data class Session(
        val name: String,
        val key: String
    )
}

/** The Last.fm [error codes](https://www.last.fm/api/errorcodes) S2 acts on. */
object LastFmError {
    /** Invalid authentication token: sign-in has to start again. */
    const val INVALID_TOKEN = 4

    /** The session key is invalid or revoked: sign the user out rather than retry. */
    const val INVALID_SESSION = 9

    /** The token hasn't been approved on last.fm yet. */
    const val UNAUTHORIZED_TOKEN = 14

    /** The token expired before it was approved. */
    const val TOKEN_EXPIRED = 15

    /** Retried with backoff: operation failed, service offline, temporarily unavailable, or rate limit exceeded. */
    val RETRYABLE = setOf(8, 11, 16, 29)

    /**
     * Invalid API key, invalid method signature or suspended API key: nothing wrong with the scrobbles themselves,
     * so the queue is kept and not retried until something else (the next play, the next start) schedules a flush.
     */
    val HOLD = setOf(10, 13, 26)
}
