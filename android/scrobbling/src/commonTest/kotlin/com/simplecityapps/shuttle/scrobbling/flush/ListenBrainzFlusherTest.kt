package com.simplecityapps.shuttle.scrobbling.flush

import com.simplecityapps.shuttle.scrobbling.listenbrainz.FakeListenBrainzServer
import com.simplecityapps.shuttle.scrobbling.listenbrainz.FakeListenBrainzSessionStore
import com.simplecityapps.shuttle.scrobbling.listenbrainz.ListenBrainzAccount
import com.simplecityapps.shuttle.scrobbling.queue.FakeScrobbleDao
import com.simplecityapps.shuttle.scrobbling.queue.QueuedScrobbleEntity
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray

class ListenBrainzFlusherTest {
    private val dao = FakeScrobbleDao()
    private val server = FakeListenBrainzServer()
    private val store = FakeListenBrainzSessionStore(ListenBrainzAccount(token = "lb-token", username = "tim"))
    private val flusher = ListenBrainzFlusher(dao, server.client(), store)

    private suspend fun enqueue(index: Int) {
        dao.enqueue(
            QueuedScrobbleEntity(
                service = QueuedScrobbleEntity.SERVICE_LISTENBRAINZ,
                artist = "artist",
                track = "track-$index",
                album = null,
                albumArtist = null,
                durationMs = 200_000,
                startedAtEpochSec = 2_000_000_000L + index
            )
        )
    }

    private fun payload(index: Int): JsonArray = server.body(index).getValue("payload").jsonArray

    @Test
    fun `signed out leaves the queue untouched`() = runTest {
        store.signOut()
        enqueue(1)

        flusher.flush() shouldBe FlushResult.Done

        dao.count(QueuedScrobbleEntity.SERVICE_LISTENBRAINZ) shouldBe 1
        server.requests shouldBe emptyList()
    }

    @Test
    fun `drains more than one batch - 50 listens per call`() = runTest {
        repeat(60) { enqueue(it) }

        flusher.flush() shouldBe FlushResult.Done

        server.requests.size shouldBe 2
        payload(0).size shouldBe 50
        payload(1).size shouldBe 10
        dao.count(QueuedScrobbleEntity.SERVICE_LISTENBRAINZ) shouldBe 0
    }

    @Test
    fun `an invalid token signs the user out and keeps the queue`() = runTest {
        enqueue(1)
        server.enqueue("""{"code":401,"error":"Invalid authorization token."}""", HttpStatusCode.Unauthorized)

        flusher.flush() shouldBe FlushResult.SignedOut

        store.account.value shouldBe null
        dao.count(QueuedScrobbleEntity.SERVICE_LISTENBRAINZ) shouldBe 1
    }

    @Test
    fun `rate limited keeps the batch and asks for a retry`() = runTest {
        enqueue(1)
        server.enqueue("""{"code":429,"error":"slow down"}""", HttpStatusCode.TooManyRequests)

        flusher.flush() shouldBe FlushResult.Retry

        dao.count(QueuedScrobbleEntity.SERVICE_LISTENBRAINZ) shouldBe 1
    }

    @Test
    fun `offline keeps the batch and asks for a retry`() = runTest {
        enqueue(1)
        server.enqueueOffline()

        flusher.flush() shouldBe FlushResult.Retry

        dao.count(QueuedScrobbleEntity.SERVICE_LISTENBRAINZ) shouldBe 1
    }

    @Test
    fun `a malformed listen is isolated and dropped while the rest are sent`() = runTest {
        repeat(3) { enqueue(it) }
        // Whole batch refused, then the first half (track-0) is accepted, then the second half is refused,
        // which splits into track-1 (accepted) and track-2 (refused alone).
        server.enqueue("""{"code":400}""", HttpStatusCode.BadRequest)
        server.enqueue()
        server.enqueue("""{"code":400}""", HttpStatusCode.BadRequest)
        server.enqueue()
        server.enqueue("""{"code":400}""", HttpStatusCode.BadRequest)

        flusher.flush() shouldBe FlushResult.Done

        dao.count(QueuedScrobbleEntity.SERVICE_LISTENBRAINZ) shouldBe 0
    }

    @Test
    fun `a refusal with nothing accepted before it is held rather than dropped`() = runTest {
        enqueue(1)
        server.enqueue("""{"code":400}""", HttpStatusCode.BadRequest)

        flusher.flush() shouldBe FlushResult.Held

        dao.count(QueuedScrobbleEntity.SERVICE_LISTENBRAINZ) shouldBe 1
    }
}
