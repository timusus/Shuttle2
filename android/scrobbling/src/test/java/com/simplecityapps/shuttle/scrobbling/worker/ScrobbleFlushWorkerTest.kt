package com.simplecityapps.shuttle.scrobbling.worker

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.simplecityapps.networking.S2Json
import com.simplecityapps.networking.createHttpClient
import com.simplecityapps.shuttle.scrobbling.lastfm.LASTFM_BASE_URL
import com.simplecityapps.shuttle.scrobbling.lastfm.FakeLastFmSessionStore
import com.simplecityapps.shuttle.scrobbling.lastfm.LastFmApi
import com.simplecityapps.shuttle.scrobbling.lastfm.LastFmClient
import com.simplecityapps.shuttle.scrobbling.lastfm.LastFmError
import com.simplecityapps.shuttle.scrobbling.lastfm.LastFmSession
import com.simplecityapps.shuttle.scrobbling.lastfm.LastFmCredentials
import com.simplecityapps.shuttle.scrobbling.lastfm.LastFmScrobbleResponse
import com.simplecityapps.shuttle.scrobbling.queue.QueuedScrobbleEntity
import com.simplecityapps.shuttle.scrobbling.queue.ScrobbleDao
import com.simplecityapps.shuttle.scrobbling.queue.ScrobbleDatabase
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
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScrobbleFlushWorkerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val database = Room.inMemoryDatabaseBuilder(context, ScrobbleDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val dao = database.scrobbleDao()
    private val fakeEngine = FakeLastFmEngine()
    private val credentials = LastFmCredentials(apiKey = "api-key", sharedSecret = "shared-secret")
    private val client = LastFmClient(LastFmApi(createHttpClient(fakeEngine.engine)), credentials)
    private val sessionStore = FakeLastFmSessionStore(LastFmSession(key = "session-key", username = "user"))

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun entity(
        index: Int,
        startedAtEpochSec: Long = System.currentTimeMillis() / 1000 - index
    ) = QueuedScrobbleEntity(
        service = QueuedScrobbleEntity.SERVICE_LASTFM,
        artist = "artist",
        track = "track-$index",
        album = "album",
        albumArtist = "album-artist",
        durationMs = 200_000,
        startedAtEpochSec = startedAtEpochSec
    )

    private fun buildWorker(): ScrobbleFlushWorker = TestListenableWorkerBuilder<ScrobbleFlushWorker>(context)
        .setWorkerFactory(
            object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters
                ): ListenableWorker = ScrobbleFlushWorker(appContext, workerParameters, dao, client, sessionStore)
            }
        )
        .build()

    @Test
    fun `no session key is a no-op, the queue is left untouched`() = runTest {
        sessionStore.signOut()
        dao.enqueue(entity(1))

        val result = buildWorker().doWork()

        result shouldBe ListenableWorker.Result.success()
        dao.count(QueuedScrobbleEntity.SERVICE_LASTFM) shouldBe 1
        fakeEngine.requests shouldBe emptyList()
    }

    @Test
    fun `drains more than one batch, 50 scrobbles per call`() = runTest {
        repeat(60) { dao.enqueue(entity(it)) }
        fakeEngine.enqueueSuccess()
        fakeEngine.enqueueSuccess()

        val result = buildWorker().doWork()

        result shouldBe ListenableWorker.Result.success()
        fakeEngine.requests.size shouldBe 2
        scrobbleCount(fakeEngine.requests[0]) shouldBe 50
        scrobbleCount(fakeEngine.requests[1]) shouldBe 10
        dao.count(QueuedScrobbleEntity.SERVICE_LASTFM) shouldBe 0
    }

    @Test
    fun `an accepted batch is deleted`() = runTest {
        dao.enqueue(entity(1))
        fakeEngine.enqueueSuccess()

        buildWorker().doWork()

        dao.count(QueuedScrobbleEntity.SERVICE_LASTFM) shouldBe 0
    }

    @Test
    fun `the request has the shape Last-fm's authspec requires`() = runTest {
        dao.enqueue(entity(1))
        fakeEngine.enqueueSuccess()

        buildWorker().doWork()

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
    fun `a batch that Last-fm ignores is still deleted, not retried`() = runTest {
        dao.enqueue(entity(1))
        fakeEngine.enqueueSuccess(
            LastFmScrobbleResponse(
                scrobbles = LastFmScrobbleResponse.Scrobbles(
                    scrobble = listOf(LastFmScrobbleResponse.ScrobbleResult(LastFmScrobbleResponse.IgnoredMessage(code = "1")))
                )
            )
        )

        val result = buildWorker().doWork()

        result shouldBe ListenableWorker.Result.success()
        dao.count(QueuedScrobbleEntity.SERVICE_LASTFM) shouldBe 0
    }

    @Test
    fun `a retryable error code keeps the queue and asks WorkManager to retry`() = runTest {
        dao.enqueue(entity(1))
        fakeEngine.enqueueSuccess(LastFmScrobbleResponse(error = 11))

        val result = buildWorker().doWork()

        result shouldBe ListenableWorker.Result.retry()
        dao.count(QueuedScrobbleEntity.SERVICE_LASTFM) shouldBe 1
    }

    @Test
    fun `a rate-limited error code keeps the queue and asks WorkManager to retry`() = runTest {
        dao.enqueue(entity(1))
        fakeEngine.enqueueSuccess(LastFmScrobbleResponse(error = 29))

        val result = buildWorker().doWork()

        result shouldBe ListenableWorker.Result.retry()
        dao.count(QueuedScrobbleEntity.SERVICE_LASTFM) shouldBe 1
    }

    @Test
    fun `an HTTP failure keeps the queue and asks WorkManager to retry`() = runTest {
        dao.enqueue(entity(1))
        fakeEngine.enqueueHttpError(HttpStatusCode.InternalServerError)

        val result = buildWorker().doWork()

        result shouldBe ListenableWorker.Result.retry()
        dao.count(QueuedScrobbleEntity.SERVICE_LASTFM) shouldBe 1
    }

    @Test
    fun `an invalid session signs the user out and stops without retrying`() = runTest {
        dao.enqueue(entity(1))
        fakeEngine.enqueueSuccess(LastFmScrobbleResponse(error = LastFmError.INVALID_SESSION))

        val result = buildWorker().doWork()

        result shouldBe ListenableWorker.Result.failure()
        sessionStore.session.value shouldBe null
        dao.count(QueuedScrobbleEntity.SERVICE_LASTFM) shouldBe 1
    }

    @Test
    fun `an unrecoverable error code drops the batch rather than retrying forever`() = runTest {
        dao.enqueue(entity(1))
        fakeEngine.enqueueSuccess(LastFmScrobbleResponse(error = 99))

        val result = buildWorker().doWork()

        result shouldBe ListenableWorker.Result.success()
        dao.count(QueuedScrobbleEntity.SERVICE_LASTFM) shouldBe 0
    }

    @Test
    fun `entries older than the max age are dropped before anything is sent`() = runTest {
        val staleEpochSec = (System.currentTimeMillis() - ScrobbleQueue.MAX_AGE - TimeUnit.DAYS.toMillis(1)) / 1000
        dao.enqueue(entity(1, startedAtEpochSec = staleEpochSec))

        val result = buildWorker().doWork()

        result shouldBe ListenableWorker.Result.success()
        dao.count(QueuedScrobbleEntity.SERVICE_LASTFM) shouldBe 0
        fakeEngine.requests shouldBe emptyList()
    }

    private fun formData(request: HttpRequestData): Parameters = (request.body as FormDataContent).formData

    private fun scrobbleCount(request: HttpRequestData): Int = formData(request).names().count { it.startsWith("artist[") }
}

private class FakeLastFmEngine {
    val requests = mutableListOf<HttpRequestData>()
    private val responses = ArrayDeque<Pair<HttpStatusCode, LastFmScrobbleResponse>>()

    fun enqueueSuccess(response: LastFmScrobbleResponse = LastFmScrobbleResponse()) {
        responses.addLast(HttpStatusCode.OK to response)
    }

    fun enqueueHttpError(status: HttpStatusCode) {
        responses.addLast(status to LastFmScrobbleResponse())
    }

    val engine = MockEngine { request ->
        requests += request
        val (status, body) = responses.removeFirstOrNull() ?: (HttpStatusCode.OK to LastFmScrobbleResponse())
        respond(
            content = S2Json.encodeToString(LastFmScrobbleResponse.serializer(), body),
            status = status,
            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        )
    }
}
