package com.simplecityapps.shuttle.scrobbling.listenbrainz

import com.simplecityapps.shuttle.scrobbling.createSong
import com.simplecityapps.shuttle.scrobbling.queue.QueuedScrobbleEntity
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

class ListenBrainzClientTest {
    private val server = FakeListenBrainzServer()
    private val client = server.client()

    private fun queued(
        index: Int,
        album: String? = "album"
    ) = QueuedScrobbleEntity(
        service = QueuedScrobbleEntity.SERVICE_LISTENBRAINZ,
        artist = "artist",
        track = "track-$index",
        album = album,
        albumArtist = null,
        durationMs = 200_000,
        startedAtEpochSec = 1_000L + index
    )

    @Test
    fun `a valid token gives the username and is sent as a Token authorization`() = runTest {
        server.enqueue("""{"code":200,"message":"Token valid.","valid":true,"user_name":"tim"}""")

        val result = client.validateToken("abc")

        result shouldBe ListenBrainzResult.Success(ListenBrainzUser("tim"))
        server.requests.single().method shouldBe HttpMethod.Get
        server.requests.single().url.toString() shouldBe "https://api.listenbrainz.org/1/validate-token"
        server.requests.single().headers[HttpHeaders.Authorization] shouldBe "Token abc"
    }

    @Test
    fun `an invalid token is reported even though ListenBrainz answers 200`() = runTest {
        server.enqueue("""{"code":200,"message":"Token invalid.","valid":false}""")

        client.validateToken("abc") shouldBe ListenBrainzResult.InvalidToken
    }

    @Test
    fun `a 401 from validation is an invalid token`() = runTest {
        server.enqueue("""{"code":401,"error":"Invalid authorization token."}""", HttpStatusCode.Unauthorized)

        client.validateToken("abc") shouldBe ListenBrainzResult.InvalidToken
    }

    @Test
    fun `offline and server errors are unreachable`() = runTest {
        server.enqueueOffline()
        server.enqueue("""{"error":"boom"}""", HttpStatusCode.InternalServerError)

        client.validateToken("abc") shouldBe ListenBrainzResult.Unreachable
        client.validateToken("abc") shouldBe ListenBrainzResult.Unreachable
    }

    @Test
    fun `queued scrobbles are submitted as one import with their start times`() = runTest {
        client.submitListens(listOf(queued(1), queued(2, album = null)), token = "abc") shouldBe ListenBrainzResult.Success(Unit)

        val request = server.requests.single()
        request.method shouldBe HttpMethod.Post
        request.url.toString() shouldBe "https://api.listenbrainz.org/1/submit-listens"
        request.headers[HttpHeaders.Authorization] shouldBe "Token abc"
        val body = server.body()
        body.getValue("listen_type").jsonPrimitive.content shouldBe "import"
        val payload = body.getValue("payload").jsonArray
        payload.size shouldBe 2
        val first = payload[0].jsonObject
        first.getValue("listened_at").jsonPrimitive.long shouldBe 1_001
        val metadata = first.getValue("track_metadata").jsonObject
        metadata.getValue("artist_name").jsonPrimitive.content shouldBe "artist"
        metadata.getValue("track_name").jsonPrimitive.content shouldBe "track-1"
        metadata.getValue("release_name").jsonPrimitive.content shouldBe "album"
        val info = metadata.getValue("additional_info").jsonObject
        info.getValue("duration_ms").jsonPrimitive.int shouldBe 200_000
        info.getValue("media_player").jsonPrimitive.content shouldBe "Shuttle Music"
        payload[1].jsonObject.getValue("track_metadata").jsonObject["release_name"] shouldBe null
    }

    @Test
    fun `now playing is a playing_now submission with no listened_at`() = runTest {
        client.playingNow(createSong(id = 1, duration = 200_000), token = "abc") shouldBe ListenBrainzResult.Success(Unit)

        val body = server.body()
        body.getValue("listen_type").jsonPrimitive.content shouldBe "playing_now"
        val listen = body.getValue("payload").jsonArray.single().jsonObject
        listen["listened_at"] shouldBe null
        listen.getValue("track_metadata").jsonObject.getValue("track_name").jsonPrimitive.contentOrNull shouldBe "song-1"
    }

    @Test
    fun `submission failures map to invalid token or rejected or unreachable`() = runTest {
        server.enqueue("""{"code":401}""", HttpStatusCode.Unauthorized)
        server.enqueue("""{"code":400}""", HttpStatusCode.BadRequest)
        server.enqueue("""{"code":429}""", HttpStatusCode.TooManyRequests)
        server.enqueueOffline()

        client.submitListens(listOf(queued(1)), "abc") shouldBe ListenBrainzResult.InvalidToken
        client.submitListens(listOf(queued(1)), "abc") shouldBe ListenBrainzResult.Rejected
        client.submitListens(listOf(queued(1)), "abc") shouldBe ListenBrainzResult.Unreachable
        client.submitListens(listOf(queued(1)), "abc") shouldBe ListenBrainzResult.Unreachable
    }
}
