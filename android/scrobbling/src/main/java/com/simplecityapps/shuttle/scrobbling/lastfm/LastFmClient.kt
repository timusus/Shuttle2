package com.simplecityapps.shuttle.scrobbling.lastfm

import com.simplecityapps.networking.S2Json
import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.networking.retrofit.error.RemoteServiceHttpError
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.scrobbling.queue.QueuedScrobbleEntity
import dev.zacsweers.metro.Inject

/** A Last.fm call's outcome. */
sealed interface LastFmResult<out T> {
    data class Success<T>(val value: T) : LastFmResult<T>

    /** Last.fm answered with an [error code](https://www.last.fm/api/errorcodes); see [LastFmError]'s codes. */
    data class Error(val code: Int) : LastFmResult<Nothing>

    /** No usable answer: offline, or an HTTP failure or body that carries no Last.fm error code. */
    data object Unreachable : LastFmResult<Nothing>
}

/**
 * Signs and sends the Last.fm calls S2 makes ([authspec](https://www.last.fm/api/authspec)), and reads each answer
 * into a [LastFmResult]. Last.fm sends most errors with a 4xx status, so the error code is read from the error body
 * as well as from a successful one.
 */
class LastFmClient
@Inject
constructor(
    private val api: LastFmApi,
    private val credentials: LastFmCredentials
) {
    /** False for a build without the API key and secret (a fork or F-Droid build): Last.fm is hidden there. */
    val isConfigured: Boolean get() = credentials.apiKey.isNotBlank() && credentials.sharedSecret.isNotBlank()

    /** The last.fm page where the user approves S2 for [token]. */
    fun approvalUrl(token: String): String = "$LASTFM_AUTH_URL?api_key=${credentials.apiKey}&token=$token"

    suspend fun getToken(): LastFmResult<String> = read(
        api.getToken(signed(mapOf("method" to "auth.getToken"))),
        error = { it.error },
        value = { it.token }
    )

    suspend fun getSession(token: String): LastFmResult<LastFmSession> = read(
        api.getSession(signed(mapOf("method" to "auth.getSession", "token" to token))),
        error = { it.error },
        value = { response -> response.session?.let { LastFmSession(key = it.key, username = it.name) } }
    )

    suspend fun updateNowPlaying(
        song: Song,
        sessionKey: String
    ): LastFmResult<Unit> {
        val params = buildMap {
            put("method", "track.updateNowPlaying")
            put("sk", sessionKey)
            put("artist", song.friendlyArtistName.orEmpty())
            put("track", song.name.orEmpty())
            song.album?.let { put("album", it) }
            song.albumArtist?.let { put("albumArtist", it) }
            if (song.duration > 0) put("duration", (song.duration / 1000).toString())
        }
        return read(api.updateNowPlaying(signed(params)), error = { it.error }, value = { })
    }

    /** One `track.scrobble` call for up to [com.simplecityapps.shuttle.scrobbling.queue.ScrobbleQueue.BATCH_SIZE] queued scrobbles, oldest first. */
    suspend fun scrobble(
        batch: List<QueuedScrobbleEntity>,
        sessionKey: String
    ): LastFmResult<LastFmScrobbleResponse> {
        val params = mutableMapOf(
            "method" to "track.scrobble",
            "sk" to sessionKey
        )
        batch.forEachIndexed { index, entity ->
            params["artist[$index]"] = entity.artist
            params["track[$index]"] = entity.track
            params["timestamp[$index]"] = entity.startedAtEpochSec.toString()
            entity.album?.let { params["album[$index]"] = it }
            entity.albumArtist?.let { params["albumArtist[$index]"] = it }
        }
        return read(api.scrobble(signed(params)), error = { it.error }, value = { it })
    }

    private fun signed(params: Map<String, String>): Map<String, String> {
        val withKey = params + ("api_key" to credentials.apiKey)
        return withKey + ("api_sig" to LastFmSigner.sign(withKey, credentials.sharedSecret)) + ("format" to "json")
    }

    private fun <B : Any, T> read(
        result: NetworkResult<B>,
        error: (B) -> Int?,
        value: (B) -> T?
    ): LastFmResult<T> = when (result) {
        is NetworkResult.Success -> {
            val code = error(result.body)
            if (code != null) {
                LastFmResult.Error(code)
            } else {
                value(result.body)?.let { LastFmResult.Success(it) } ?: LastFmResult.Unreachable
            }
        }

        is NetworkResult.Failure -> errorCode(result.error)?.let { LastFmResult.Error(it) } ?: LastFmResult.Unreachable
    }

    private fun errorCode(failure: Throwable): Int? {
        val body = (failure as? RemoteServiceHttpError)?.body ?: return null
        return try {
            S2Json.decodeFromString(LastFmBasicResponse.serializer(), body).error
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    companion object {
        const val LASTFM_AUTH_URL = "https://www.last.fm/api/auth/"
    }
}
