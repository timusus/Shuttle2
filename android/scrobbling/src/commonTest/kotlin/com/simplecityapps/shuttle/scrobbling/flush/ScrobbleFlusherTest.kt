package com.simplecityapps.shuttle.scrobbling.flush

import com.simplecityapps.networking.S2Json
import com.simplecityapps.networking.createHttpClient
import com.simplecityapps.shuttle.scrobbling.lastfm.FakeLastFmSessionStore
import com.simplecityapps.shuttle.scrobbling.lastfm.LASTFM_BASE_URL
import com.simplecityapps.shuttle.scrobbling.lastfm.LastFmApi
import com.simplecityapps.shuttle.scrobbling.lastfm.LastFmClient
import com.simplecityapps.shuttle.scrobbling.lastfm.LastFmCredentials
import com.simplecityapps.shuttle.scrobbling.lastfm.LastFmError
import com.simplecityapps.shuttle.scrobbling.lastfm.LastFmScrobbleResponse
import com.simplecityapps.shuttle.scrobbling.lastfm.LastFmSession
import com.simplecityapps.shuttle.scrobbling.listenbrainz.FakeListenBrainzServer
import com.simplecityapps.shuttle.scrobbling.listenbrainz.FakeListenBrainzSessionStore
import com.simplecityapps.shuttle.scrobbling.listenbrainz.ListenBrainzAccount
import com.simplecityapps.shuttle.scrobbling.queue.FakeScrobbleDao
import com.simplecityapps.shuttle.scrobbling.queue.QueuedScrobbleEntity
import com.simplecityapps.shuttle.scrobbling.queue.ScrobbleDao
import com.simplecityapps.shuttle.scrobbling.queue.ScrobbleQueue
import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.forms.FormDataContent
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.test.runTest

class ScrobbleFlusherTest {
    private val dao = FakeScrobbleDao()
    private val fakeEngine = FakeLastFmEngine()
    private val credentials = LastFmCredentials(apiKey = "api-key", sharedSecret = "shared-secret")
    private val client = LastFmClient(LastFmApi(createHttpClient(fakeEngine.engine)), credentials)
    private val sessionStore = FakeLastFmSessionStore(LastFmSession(key = "session-key", username = "user"))

    private val listenBrainzServer = FakeListenBrainzServer()
    private val listenBrainzStore = FakeListenBrainzSessionStore()

    private val flusher = ScrobbleFlusher(dao, client, sessionStore, ListenBrainzFlusher(dao, listenBrainzServer.client(), listenBrainzStore))

    private fun nowEpochSec(): Long = Clock.System.now().epochSeconds

    private fun entity(
        index: Int,
        startedAtEpochSec: Long = nowEpochSec() - index
    ) = QueuedScrobbleEntity(
        service = QueuedScrobbleEntity.SERVICE_LASTFM,
        artist = "artist",
        track = "track-$index",
        album = "album",
        albumArtist = "album-artist",
        durationMs = 200_000,
        startedAtEpochSec = startedAtEpochSec
    )

    @Test
    fun `no session key is a no-op - the queue is left untouched`() = runTest {
        sessionStore.signOut()
        dao.enqueue(entity(1))

        val result = flusher.flush()

        result shouldBe FlushResult.Done
        dao.count(QueuedScrobbleEntity.SERVICE_LASTFM) shouldBe 1
        fakeEngine.requests shouldBe emptyList()
    }

    @Test
    fun `drains more than one batch - 50 scrobbles per call`() = runTest {
        repeat(60) { dao.enqueue(entity(it)) }
        fakeEngine.enqueueSuccess()
        fakeEngine.enqueueSuccess()

        val result = flusher.flush()

        result shouldBe FlushResult.Done
        fakeEngine.requests.size shouldBe 2
        scrobbleCount(fakeEngine.requests[0]) shouldBe 50
        scrobbleCount(fakeEngine.requests[1]) shouldBe 10
        dao.count(QueuedScrobbleEntity.SERVICE_LASTFM) shouldBe 0
    }

    @Test
    fun `an accepted batch is deleted`() = runTest {
        dao.enqueue(entity(1))
        fakeEngine.enqueueSuccess()

        flusher.flush()

        dao.count(QueuedScrobbleEntity.SERVICE_LASTFM) shouldBe 0
    }

    @Test
    fun `the request has the shape Last-fm's authspec requires`() = runTest {
        dao.enqueue(entity(1))
        fakeEngine.enqueueSuccess()

        flusher.flush()

        val request = fakeEngine.requests.single()
        request.method shouldBe HttpMethod.Post
        request.url.toString() shouldBe LASTFM_BASE_URL
        val form = formData(request)
        form["method"] shouldBe "track.scrobble"
        form["api_key"] shouldBe "api-key"
        form["sk"] shouldBe "session-key"
        form["artist[0]"] shouldBe "artist"
        form["track[0]"] shouldBe "track-1"
        form["format"] shouldBe "json"
        form["api_sig"]?.length shouldBe 32
    }

    @Test
    fun `a batch that Last-fm ignores is still deleted - not retried`() = runTest {
        dao.enqueue(entity(1))
        fakeEngine.enqueueSuccess(
            LastFmScrobbleResponse(
                scrobbles = LastFmScrobbleResponse.Scrobbles(
                    scrobble = listOf(LastFmScrobbleResponse.ScrobbleResult(LastFmScrobbleResponse.IgnoredMessage(code = "1")))
                )
            )
        )

        val result = flusher.flush()

        result shouldBe FlushResult.Done
        dao.count(QueuedScrobbleEntity.SERVICE_LASTFM) shouldBe 0
    }

    @Test
    fun `a retryable error code keeps the queue and asks for a retry`() = runTest {
        dao.enqueue(entity(1))
        fakeEngine.enqueueSuccess(LastFmScrobbleResponse(error = 11))

        val result = flusher.flush()

        result shouldBe FlushResult.Retry
        dao.count(QueuedScrobbleEntity.SERVICE_LASTFM) shouldBe 1
    }

    @Test
    fun `a rate-limited error code keeps the queue and asks for a retry`() = runTest {
        dao.enqueue(entity(1))
        fakeEngine.enqueueSuccess(LastFmScrobbleResponse(error = 29))

        val result = flusher.flush()

        result shouldBe FlushResult.Retry
        dao.count(QueuedScrobbleEntity.SERVICE_LASTFM) shouldBe 1
    }

    @Test
    fun `an HTTP failure keeps the queue and asks for a retry`() = runTest {
        dao.enqueue(entity(1))
        fakeEngine.enqueueHttpError(HttpStatusCode.InternalServerError)

        val result = flusher.flush()

        result shouldBe FlushResult.Retry
        dao.count(QueuedScrobbleEntity.SERVICE_LASTFM) shouldBe 1
    }

    @Test
    fun `an invalid session signs the user out and stops without retrying`() = runTest {
        dao.enqueue(entity(1))
        fakeEngine.enqueueSuccess(LastFmScrobbleResponse(error = LastFmError.INVALID_SESSION))

        val result = flusher.flush()

        result shouldBe FlushResult.SignedOut
        sessionStore.session.value shouldBe null
        dao.count(QueuedScrobbleEntity.SERVICE_LASTFM) shouldBe 1
    }

    @Test
    fun `an operation-failed error code keeps the queue and asks for a retry`() = runTest {
        dao.enqueue(entity(1))
        fakeEngine.enqueueSuccess(LastFmScrobbleResponse(error = 8))

        val result = flusher.flush()

        result shouldBe FlushResult.Retry
        dao.count(QueuedScrobbleEntity.SERVICE_LASTFM) shouldBe 1
    }

    @Test
    fun `key - signature and suspended-key errors hold the queue without retrying`() = runTest {
        listOf(10, 13, 26).forEach { code ->
            dao.deleteAll(QueuedScrobbleEntity.SERVICE_LASTFM)
            dao.enqueue(entity(1))
            fakeEngine.enqueueSuccess(LastFmScrobbleResponse(error = code))

            val result = flusher.flush()

            result shouldBe FlushResult.Held
            dao.count(QueuedScrobbleEntity.SERVICE_LASTFM) shouldBe 1
            sessionStore.session.value?.key shouldBe "session-key"
        }
    }

    @Test
    fun `one poison row in a batch is dropped and the others are scrobbled`() = runTest {
        repeat(4) { dao.enqueue(entity(it, startedAtEpochSec = nowEpochSec() - 100 + it)) }
        val invalid = LastFmScrobbleResponse(error = LastFmError.INVALID_PARAMETERS)
        // Whole batch, then [0, 1], then [2, 3], then 2 alone, then 3 alone (the poison row).
        fakeEngine.enqueueSuccess(invalid)
        fakeEngine.enqueueSuccess()
        fakeEngine.enqueueSuccess(invalid)
        fakeEngine.enqueueSuccess()
        fakeEngine.enqueueSuccess(invalid)

        val result = flusher.flush()

        result shouldBe FlushResult.Done
        dao.count(QueuedScrobbleEntity.SERVICE_LASTFM) shouldBe 0
        fakeEngine.requests.map { scrobbleCount(it) } shouldBe listOf(4, 2, 2, 1, 1)
        formData(fakeEngine.requests[3])["track[0]"] shouldBe "track-2"
        formData(fakeEngine.requests[4])["track[0]"] shouldBe "track-3"
    }

    @Test
    fun `a single-row queue that gets error 6 is held - not dropped`() = runTest {
        dao.enqueue(entity(1))
        fakeEngine.enqueueSuccess(LastFmScrobbleResponse(error = LastFmError.INVALID_PARAMETERS))

        val result = flusher.flush()

        result shouldBe FlushResult.Held
        dao.count(QueuedScrobbleEntity.SERVICE_LASTFM) shouldBe 1
    }

    @Test
    fun `error 6 on every row deletes nothing`() = runTest {
        repeat(6) { dao.enqueue(entity(it, startedAtEpochSec = nowEpochSec() - 100 + it)) }
        fakeEngine.responder = { LastFmScrobbleResponse(error = LastFmError.INVALID_PARAMETERS) }

        val result = flusher.flush()

        result shouldBe FlushResult.Held
        dao.count(QueuedScrobbleEntity.SERVICE_LASTFM) shouldBe 6
    }

    @Test
    fun `a poison row first in the queue is dropped once a later row is accepted`() = runTest {
        repeat(4) { dao.enqueue(entity(it, startedAtEpochSec = nowEpochSec() - 100 + it)) }
        fakeEngine.responder = { request -> poisonedBy(request, "track-0") }

        val result = flusher.flush()

        result shouldBe FlushResult.Done
        dao.count(QueuedScrobbleEntity.SERVICE_LASTFM) shouldBe 0
    }

    @Test
    fun `the drop budget holds the queue once a run has dropped too many rows`() = runTest {
        repeat(20) { dao.enqueue(entity(it, startedAtEpochSec = nowEpochSec() - 100 + it)) }
        val poison = listOf(1, 4, 7, 10, 13, 16).map { "track-$it" }
        fakeEngine.responder = { request -> poisonedBy(request, *poison.toTypedArray()) }

        val result = flusher.flush()

        result shouldBe FlushResult.Held
        val remaining = dao.oldestBatch(QueuedScrobbleEntity.SERVICE_LASTFM, 50).map { it.track }
        (poison.take(ScrobbleFlusher.MAX_DROPS_PER_RUN).intersect(remaining.toSet())) shouldBe emptySet()
        remaining.contains("track-16") shouldBe true
    }

    private fun poisonedBy(
        request: HttpRequestData,
        vararg tracks: String
    ): LastFmScrobbleResponse? {
        val sent = formData(request).entries().filter { it.key.startsWith("track[") }.map { it.value.first() }
        return if (sent.any { it in tracks }) LastFmScrobbleResponse(error = LastFmError.INVALID_PARAMETERS) else null
    }

    @Test
    fun `a transient failure while isolating a poison row keeps the rows not yet accepted`() = runTest {
        repeat(2) { dao.enqueue(entity(it, startedAtEpochSec = nowEpochSec() - 100 + it)) }
        fakeEngine.enqueueSuccess(LastFmScrobbleResponse(error = LastFmError.INVALID_PARAMETERS))
        fakeEngine.enqueueSuccess()
        fakeEngine.enqueueSuccess(LastFmScrobbleResponse(error = 11))

        val result = flusher.flush()

        result shouldBe FlushResult.Retry
        dao.count(QueuedScrobbleEntity.SERVICE_LASTFM) shouldBe 1
    }

    @Test
    fun `systemic top-level errors hold the queue and delete nothing`() = runTest {
        listOf(2, 3, 4, 5, 14, 15, 17, 18, 27, 99).forEach { code ->
            dao.deleteAll(QueuedScrobbleEntity.SERVICE_LASTFM)
            dao.enqueue(entity(1))
            fakeEngine.enqueueSuccess(LastFmScrobbleResponse(error = code))

            val result = flusher.flush()

            result shouldBe FlushResult.Held
            dao.count(QueuedScrobbleEntity.SERVICE_LASTFM) shouldBe 1
            sessionStore.session.value?.key shouldBe "session-key"
        }
    }

    @Test
    fun `entries older than the max age are dropped before anything is sent`() = runTest {
        val staleEpochSec = (Clock.System.now() - ScrobbleQueue.MAX_AGE - 1.days).epochSeconds
        dao.enqueue(entity(1, startedAtEpochSec = staleEpochSec))

        val result = flusher.flush()

        result shouldBe FlushResult.Done
        dao.count(QueuedScrobbleEntity.SERVICE_LASTFM) shouldBe 0
        fakeEngine.requests shouldBe emptyList()
    }

    @Test
    fun `a play queued for both services is sent to each`() = runTest {
        listenBrainzStore.signIn(ListenBrainzAccount(token = "lb-token", username = "tim"))
        dao.enqueue(entity(1))
        dao.enqueue(entity(1).copy(service = QueuedScrobbleEntity.SERVICE_LISTENBRAINZ))
        fakeEngine.enqueueSuccess()

        val result = flusher.flush()

        result shouldBe FlushResult.Done
        fakeEngine.requests.size shouldBe 1
        listenBrainzServer.requests.size shouldBe 1
        dao.count(QueuedScrobbleEntity.SERVICE_LASTFM) shouldBe 0
        dao.count(QueuedScrobbleEntity.SERVICE_LISTENBRAINZ) shouldBe 0
    }

    @Test
    fun `ListenBrainz being unreachable does not stop Last-fm and asks for a retry`() = runTest {
        listenBrainzStore.signIn(ListenBrainzAccount(token = "lb-token", username = "tim"))
        dao.enqueue(entity(1))
        dao.enqueue(entity(1).copy(service = QueuedScrobbleEntity.SERVICE_LISTENBRAINZ))
        fakeEngine.enqueueSuccess()
        listenBrainzServer.enqueueOffline()

        val result = flusher.flush()

        result shouldBe FlushResult.Retry
        dao.count(QueuedScrobbleEntity.SERVICE_LASTFM) shouldBe 0
        dao.count(QueuedScrobbleEntity.SERVICE_LISTENBRAINZ) shouldBe 1
    }

    @Test
    fun `Last-fm failing does not stop ListenBrainz`() = runTest {
        listenBrainzStore.signIn(ListenBrainzAccount(token = "lb-token", username = "tim"))
        dao.enqueue(entity(1))
        dao.enqueue(entity(1).copy(service = QueuedScrobbleEntity.SERVICE_LISTENBRAINZ))
        fakeEngine.enqueueHttpError(HttpStatusCode.InternalServerError)

        val result = flusher.flush()

        result shouldBe FlushResult.Retry
        dao.count(QueuedScrobbleEntity.SERVICE_LASTFM) shouldBe 1
        dao.count(QueuedScrobbleEntity.SERVICE_LISTENBRAINZ) shouldBe 0
    }

    private fun formData(request: HttpRequestData): Parameters = (request.body as FormDataContent).formData

    private fun scrobbleCount(request: HttpRequestData): Int = formData(request).names().count { it.startsWith("artist[") }
}

private class FakeLastFmEngine {
    val requests = mutableListOf<HttpRequestData>()

    /** Answers a request itself when it returns non-null, ahead of the enqueued responses. */
    var responder: ((HttpRequestData) -> LastFmScrobbleResponse?)? = null
    private val responses = ArrayDeque<Pair<HttpStatusCode, LastFmScrobbleResponse>>()

    fun enqueueSuccess(response: LastFmScrobbleResponse = LastFmScrobbleResponse()) {
        responses.addLast(HttpStatusCode.OK to response)
    }

    fun enqueueHttpError(status: HttpStatusCode) {
        responses.addLast(status to LastFmScrobbleResponse())
    }

    val engine = MockEngine { request ->
        requests += request
        val (status, body) = responder?.let { it(request)?.let { r -> HttpStatusCode.OK to r } }
            ?: responses.removeFirstOrNull()
            ?: (HttpStatusCode.OK to LastFmScrobbleResponse())
        respond(
            content = S2Json.encodeToString(LastFmScrobbleResponse.serializer(), body),
            status = status,
            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        )
    }
}
