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
import com.simplecityapps.shuttle.scrobbling.lastfm.LastFmApi
import com.simplecityapps.shuttle.scrobbling.lastfm.LastFmCredentials
import com.simplecityapps.shuttle.scrobbling.lastfm.LastFmScrobbleResponse
import com.simplecityapps.shuttle.scrobbling.lastfm.LastFmSessionStore
import com.simplecityapps.shuttle.scrobbling.queue.QueuedScrobbleEntity
import com.simplecityapps.shuttle.scrobbling.queue.ScrobbleDao
import com.simplecityapps.shuttle.scrobbling.queue.ScrobbleDatabase
import com.simplecityapps.shuttle.scrobbling.queue.ScrobbleQueue
import io.kotest.matchers.shouldBe
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Response

@RunWith(AndroidJUnit4::class)
class ScrobbleFlushWorkerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val database = Room.inMemoryDatabaseBuilder(context, ScrobbleDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val dao = database.scrobbleDao()
    private val api = FakeLastFmApi()
    private val sessionStore = FakeLastFmSessionStore(sessionKey = "session-key")
    private val credentials = LastFmCredentials(apiKey = "api-key", sharedSecret = "shared-secret")

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
                ): ListenableWorker = ScrobbleFlushWorker(appContext, workerParameters, dao, api, sessionStore, credentials)
            }
        )
        .build()

    @Test
    fun `no session key is a no-op, the queue is left untouched`() = runTest {
        sessionStore.sessionKey = null
        dao.enqueue(entity(1))

        val result = buildWorker().doWork()

        result shouldBe ListenableWorker.Result.success()
        dao.count(QueuedScrobbleEntity.SERVICE_LASTFM) shouldBe 1
        api.requests shouldBe emptyList()
    }

    @Test
    fun `drains more than one batch, 50 scrobbles per call`() = runTest {
        repeat(60) { dao.enqueue(entity(it)) }
        api.enqueue(successResponse())
        api.enqueue(successResponse())

        val result = buildWorker().doWork()

        result shouldBe ListenableWorker.Result.success()
        api.requests.size shouldBe 2
        scrobbleCount(api.requests[0]) shouldBe 50
        scrobbleCount(api.requests[1]) shouldBe 10
        dao.count(QueuedScrobbleEntity.SERVICE_LASTFM) shouldBe 0
    }

    @Test
    fun `an accepted batch is deleted`() = runTest {
        dao.enqueue(entity(1))
        api.enqueue(successResponse())

        buildWorker().doWork()

        dao.count(QueuedScrobbleEntity.SERVICE_LASTFM) shouldBe 0
    }

    @Test
    fun `a batch that Last-fm ignores is still deleted, not retried`() = runTest {
        dao.enqueue(entity(1))
        api.enqueue(
            Response.success(
                LastFmScrobbleResponse(
                    scrobbles = LastFmScrobbleResponse.Scrobbles(
                        scrobble = listOf(LastFmScrobbleResponse.ScrobbleResult(LastFmScrobbleResponse.IgnoredMessage(code = "1")))
                    )
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
        api.enqueue(Response.success(LastFmScrobbleResponse(error = 11)))

        val result = buildWorker().doWork()

        result shouldBe ListenableWorker.Result.retry()
        dao.count(QueuedScrobbleEntity.SERVICE_LASTFM) shouldBe 1
        dao.oldestBatch(QueuedScrobbleEntity.SERVICE_LASTFM, 10).single().attempts shouldBe 1
    }

    @Test
    fun `a rate-limited error code keeps the queue and asks WorkManager to retry`() = runTest {
        dao.enqueue(entity(1))
        api.enqueue(Response.success(LastFmScrobbleResponse(error = 29)))

        val result = buildWorker().doWork()

        result shouldBe ListenableWorker.Result.retry()
        dao.count(QueuedScrobbleEntity.SERVICE_LASTFM) shouldBe 1
    }

    @Test
    fun `an HTTP failure keeps the queue and asks WorkManager to retry`() = runTest {
        dao.enqueue(entity(1))
        api.enqueue(Response.error(500, "".toResponseBody("text/plain".toMediaType())))

        val result = buildWorker().doWork()

        result shouldBe ListenableWorker.Result.retry()
        dao.count(QueuedScrobbleEntity.SERVICE_LASTFM) shouldBe 1
    }

    @Test
    fun `an invalid session signs the user out and stops without retrying`() = runTest {
        dao.enqueue(entity(1))
        api.enqueue(Response.success(LastFmScrobbleResponse(error = LastFmScrobbleResponse.ERROR_INVALID_SESSION)))

        val result = buildWorker().doWork()

        result shouldBe ListenableWorker.Result.failure()
        sessionStore.sessionKey shouldBe null
        dao.count(QueuedScrobbleEntity.SERVICE_LASTFM) shouldBe 1
    }

    @Test
    fun `an unrecoverable error code drops the batch rather than retrying forever`() = runTest {
        dao.enqueue(entity(1))
        api.enqueue(Response.success(LastFmScrobbleResponse(error = 99)))

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
        api.requests shouldBe emptyList()
    }

    private fun successResponse() = Response.success(LastFmScrobbleResponse())

    private fun scrobbleCount(params: Map<String, String>) = params.keys.count { it.startsWith("artist[") }
}

private class FakeLastFmApi : LastFmApi {
    val requests = mutableListOf<Map<String, String>>()
    private val responses = ArrayDeque<Response<LastFmScrobbleResponse>>()

    fun enqueue(response: Response<LastFmScrobbleResponse>) {
        responses.addLast(response)
    }

    override suspend fun scrobble(params: Map<String, String>): Response<LastFmScrobbleResponse> {
        requests += params
        return responses.removeFirstOrNull() ?: Response.success(LastFmScrobbleResponse())
    }
}

private class FakeLastFmSessionStore(sessionKey: String?) : LastFmSessionStore {
    override var sessionKey: String? = sessionKey

    override fun signOut() {
        sessionKey = null
    }
}
