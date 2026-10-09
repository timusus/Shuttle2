package com.simplecityapps.shuttle.scrobbling.listenbrainz

import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.networking.retrofit.error.RemoteServiceHttpError
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.scrobbling.queue.QueuedScrobbleEntity
import dev.zacsweers.metro.Inject
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** A ListenBrainz call's outcome. */
sealed interface ListenBrainzResult<out T> {
    data class Success<T>(val value: T) : ListenBrainzResult<T>

    /** The token is missing or no longer valid (401). */
    data object InvalidToken : ListenBrainzResult<Nothing>

    /** ListenBrainz refused the payload as malformed (400); sending it again won't help. */
    data object Rejected : ListenBrainzResult<Nothing>

    /** No usable answer: offline, rate limited (429) or a server failure. Worth trying again later. */
    data object Unreachable : ListenBrainzResult<Nothing>
}

data class ListenBrainzUser(val username: String)

/** Sends the ListenBrainz calls S2 makes, and reads each answer into a [ListenBrainzResult]. */
class ListenBrainzClient
@Inject
constructor(
    private val api: ListenBrainzApi
) {
    /** `validate-token` answers 200 with `valid: false` for a bad token, so both shapes of "bad" map to [ListenBrainzResult.InvalidToken]. */
    suspend fun validateToken(token: String): ListenBrainzResult<ListenBrainzUser> = when (val result = api.validateToken(token)) {
        is NetworkResult.Success -> {
            val username = result.body.user_name
            if (result.body.valid && !username.isNullOrBlank()) ListenBrainzResult.Success(ListenBrainzUser(username)) else ListenBrainzResult.InvalidToken
        }

        is NetworkResult.Failure -> failure(result.error)
    }

    suspend fun playingNow(
        song: Song,
        token: String
    ): ListenBrainzResult<Unit> = submit(
        token,
        listenType = "playing_now",
        listens = listOf(
            listen(
                listenedAt = null,
                artist = song.friendlyArtistName.orEmpty(),
                track = song.name.orEmpty(),
                album = song.album,
                durationMs = song.duration
            )
        )
    )

    /** One `import` submission for up to [com.simplecityapps.shuttle.scrobbling.queue.ScrobbleQueue.BATCH_SIZE] queued scrobbles, which is what takes many listens at once. */
    suspend fun submitListens(
        batch: List<QueuedScrobbleEntity>,
        token: String
    ): ListenBrainzResult<Unit> = submit(
        token,
        listenType = "import",
        listens = batch.map {
            listen(listenedAt = it.startedAtEpochSec, artist = it.artist, track = it.track, album = it.album, durationMs = it.durationMs)
        }
    )

    private suspend fun submit(
        token: String,
        listenType: String,
        listens: List<JsonObject>
    ): ListenBrainzResult<Unit> {
        val body = buildJsonObject {
            put("listen_type", listenType)
            put("payload", buildJsonArray { listens.forEach { add(it) } })
        }
        return when (val result = api.submitListens(token, body.toString())) {
            is NetworkResult.Success -> ListenBrainzResult.Success(Unit)
            is NetworkResult.Failure -> failure(result.error)
        }
    }

    private fun listen(
        listenedAt: Long?,
        artist: String,
        track: String,
        album: String?,
        durationMs: Int
    ) = buildJsonObject {
        listenedAt?.let { put("listened_at", it) }
        put(
            "track_metadata",
            buildJsonObject {
                put("artist_name", artist)
                put("track_name", track)
                album?.let { put("release_name", it) }
                put(
                    "additional_info",
                    buildJsonObject {
                        put("media_player", MEDIA_PLAYER)
                        put("submission_client", MEDIA_PLAYER)
                        if (durationMs > 0) put("duration_ms", durationMs)
                    }
                )
            }
        )
    }

    private fun failure(error: Throwable): ListenBrainzResult<Nothing> = when ((error as? RemoteServiceHttpError)?.httpStatusCode) {
        HttpStatusCode.Unauthorized -> ListenBrainzResult.InvalidToken
        HttpStatusCode.BadRequest -> ListenBrainzResult.Rejected
        else -> ListenBrainzResult.Unreachable
    }

    private companion object {
        const val MEDIA_PLAYER = "Shuttle Music"
    }
}
