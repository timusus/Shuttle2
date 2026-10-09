package com.simplecityapps.shuttle.scrobbling.flush

import com.simplecityapps.networking.createHttpClient
import com.simplecityapps.shuttle.scrobbling.lastfm.FakeLastFmServer
import com.simplecityapps.shuttle.scrobbling.lastfm.FakeLastFmSessionStore
import com.simplecityapps.shuttle.scrobbling.lastfm.LastFmApi
import com.simplecityapps.shuttle.scrobbling.lastfm.LastFmClient
import com.simplecityapps.shuttle.scrobbling.lastfm.LastFmSession
import com.simplecityapps.shuttle.scrobbling.lastfm.testCredentials
import com.simplecityapps.shuttle.scrobbling.listenbrainz.FakeListenBrainzServer
import com.simplecityapps.shuttle.scrobbling.listenbrainz.FakeListenBrainzSessionStore
import com.simplecityapps.shuttle.scrobbling.queue.FakeScrobbleDao
import com.simplecityapps.shuttle.scrobbling.queue.QueuedScrobbleEntity
import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class InProcessScrobbleFlushSchedulerTest {
    private val dao = FakeScrobbleDao()
    private val server = FakeLastFmServer()
    private val sessionStore = FakeLastFmSessionStore(LastFmSession(key = "sk", username = "user"))
    private var online = true

    private fun TestScope.scheduler(client: LastFmClient = server.client(dispatcher = StandardTestDispatcher(testScheduler))) = InProcessScrobbleFlushScheduler(
        flusher = ScrobbleFlusher(dao, client, sessionStore, ListenBrainzFlusher(dao, FakeListenBrainzServer().client(), FakeListenBrainzSessionStore())),
        connectivity = { online },
        scope = backgroundScope
    )

    private suspend fun enqueue(index: Int) {
        dao.enqueue(
            QueuedScrobbleEntity(
                service = QueuedScrobbleEntity.SERVICE_LASTFM,
                artist = "artist",
                track = "track-$index",
                album = null,
                albumArtist = null,
                durationMs = 200_000,
                startedAtEpochSec = 2_000_000_000L + index
            )
        )
    }

    @Test
    fun `a flush sends the queue`() = runTest {
        enqueue(1)

        scheduler().scheduleFlush()
        runCurrent()

        server.requests.size shouldBe 1
        dao.count(QueuedScrobbleEntity.SERVICE_LASTFM) shouldBe 0
    }

    @Test
    fun `offline - nothing is attempted`() = runTest {
        enqueue(1)
        online = false

        scheduler().scheduleFlush()
        runCurrent()

        server.requests.size shouldBe 0
        dao.count(QueuedScrobbleEntity.SERVICE_LASTFM) shouldBe 1
    }

    @Test
    fun `requests made before a flush starts share one run`() = runTest {
        enqueue(1)
        val scheduler = scheduler()

        repeat(3) { scheduler.scheduleFlush() }
        runCurrent()

        server.requests.size shouldBe 1
    }

    @Test
    fun `a flush never runs while another is in progress`() = runTest {
        enqueue(1)
        val gate = CompletableDeferred<Unit>()
        var inFlight = 0
        var maxInFlight = 0
        var calls = 0
        val engine = MockEngine(
            MockEngineConfig().apply {
                dispatcher = StandardTestDispatcher(testScheduler)
                addHandler {
                    calls++
                    inFlight++
                    maxInFlight = maxOf(maxInFlight, inFlight)
                    gate.await()
                    inFlight--
                    respond("{}", headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
                }
            }
        )
        val scheduler = scheduler(LastFmClient(LastFmApi(createHttpClient(engine)), testCredentials))

        scheduler.scheduleFlush()
        runCurrent()
        // A second trigger and a background task's flush, both while the first run waits on the network.
        enqueue(2)
        scheduler.scheduleFlush()
        val background = async { scheduler.flush() }
        runCurrent()
        maxInFlight shouldBe 1
        gate.complete(Unit)
        runCurrent()

        background.await() shouldBe FlushResult.Done
        maxInFlight shouldBe 1
        dao.count(QueuedScrobbleEntity.SERVICE_LASTFM) shouldBe 0
    }

    @Test
    fun `a transient failure is retried after a backoff`() = runTest {
        enqueue(1)
        server.enqueueOffline()
        val scheduler = scheduler()

        scheduler.scheduleFlush()
        runCurrent()
        server.requests.size shouldBe 1
        dao.count(QueuedScrobbleEntity.SERVICE_LASTFM) shouldBe 1

        advanceTimeBy(InProcessScrobbleFlushScheduler.INITIAL_BACKOFF.inWholeMilliseconds - 1)
        runCurrent()
        server.requests.size shouldBe 1

        advanceTimeBy(2)
        runCurrent()
        server.requests.size shouldBe 2
        dao.count(QueuedScrobbleEntity.SERVICE_LASTFM) shouldBe 0
    }

    @Test
    fun `a retry that comes due while offline waits for connectivity`() = runTest {
        enqueue(1)
        server.enqueueOffline()
        val scheduler = scheduler()

        scheduler.scheduleFlush()
        runCurrent()
        online = false

        advanceTimeBy(InProcessScrobbleFlushScheduler.INITIAL_BACKOFF.inWholeMilliseconds + 1)
        advanceTimeBy(InProcessScrobbleFlushScheduler.OFFLINE_POLL.inWholeMilliseconds * 3)
        runCurrent()
        server.requests.size shouldBe 1

        online = true
        advanceTimeBy(InProcessScrobbleFlushScheduler.OFFLINE_POLL.inWholeMilliseconds)
        runCurrent()
        server.requests.size shouldBe 2
        dao.count(QueuedScrobbleEntity.SERVICE_LASTFM) shouldBe 0
    }

    @Test
    fun `the background task's flush reports its result`() = runTest {
        enqueue(1)
        server.enqueueOffline()

        scheduler().flush() shouldBe FlushResult.Retry
    }
}
