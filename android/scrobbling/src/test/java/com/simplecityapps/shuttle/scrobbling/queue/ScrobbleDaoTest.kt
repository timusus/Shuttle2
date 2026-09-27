package com.simplecityapps.shuttle.scrobbling.queue

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScrobbleDaoTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val database = Room.inMemoryDatabaseBuilder(context, ScrobbleDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val dao = database.scrobbleDao()

    @After
    fun tearDown() {
        database.close()
    }

    private fun entity(
        startedAtEpochSec: Long,
        track: String = "track",
        service: String = QueuedScrobbleEntity.SERVICE_LASTFM
    ) = QueuedScrobbleEntity(
        service = service,
        artist = "artist",
        track = track,
        album = "album",
        albumArtist = "album-artist",
        durationMs = 200_000,
        startedAtEpochSec = startedAtEpochSec
    )

    @Test
    fun `enqueuing the same service, startedAt and track twice is a no-op`() = runTest {
        dao.enqueue(entity(startedAtEpochSec = 1_000))
        dao.enqueue(entity(startedAtEpochSec = 1_000))

        dao.count(QueuedScrobbleEntity.SERVICE_LASTFM) shouldBe 1
    }

    @Test
    fun `a different track at the same time is a separate row`() = runTest {
        dao.enqueue(entity(startedAtEpochSec = 1_000, track = "track-a"))
        dao.enqueue(entity(startedAtEpochSec = 1_000, track = "track-b"))

        dao.count(QueuedScrobbleEntity.SERVICE_LASTFM) shouldBe 2
    }

    @Test
    fun `oldestBatch returns rows oldest first, capped at the limit`() = runTest {
        dao.enqueue(entity(startedAtEpochSec = 3_000, track = "third"))
        dao.enqueue(entity(startedAtEpochSec = 1_000, track = "first"))
        dao.enqueue(entity(startedAtEpochSec = 2_000, track = "second"))

        val batch = dao.oldestBatch(QueuedScrobbleEntity.SERVICE_LASTFM, limit = 2)

        batch.map { it.track } shouldBe listOf("first", "second")
    }

    @Test
    fun `deleteByIds removes only the named rows`() = runTest {
        dao.enqueue(entity(startedAtEpochSec = 1_000, track = "keep"))
        dao.enqueue(entity(startedAtEpochSec = 2_000, track = "remove"))
        val toRemove = dao.oldestBatch(QueuedScrobbleEntity.SERVICE_LASTFM, limit = 10).single { it.track == "remove" }

        dao.deleteByIds(listOf(toRemove.id))

        dao.oldestBatch(QueuedScrobbleEntity.SERVICE_LASTFM, limit = 10).map { it.track } shouldBe listOf("keep")
    }

    @Test
    fun `deleteOlderThan drops rows before the cutoff and keeps the rest`() = runTest {
        dao.enqueue(entity(startedAtEpochSec = 1_000, track = "old"))
        dao.enqueue(entity(startedAtEpochSec = 5_000, track = "new"))

        dao.deleteOlderThan(cutoffEpochSec = 2_000)

        dao.oldestBatch(QueuedScrobbleEntity.SERVICE_LASTFM, limit = 10).map { it.track } shouldBe listOf("new")
    }

    @Test
    fun `deleteOlderThan with nothing past the cutoff leaves the queue untouched`() = runTest {
        dao.enqueue(entity(startedAtEpochSec = 5_000, track = "new"))

        dao.deleteOlderThan(cutoffEpochSec = 1_000)

        dao.count(QueuedScrobbleEntity.SERVICE_LASTFM) shouldBe 1
    }

    @Test
    fun `trimToNewest keeps only the newest rows for the service, dropping the oldest`() = runTest {
        dao.enqueue(entity(startedAtEpochSec = 1_000, track = "oldest"))
        dao.enqueue(entity(startedAtEpochSec = 2_000, track = "middle"))
        dao.enqueue(entity(startedAtEpochSec = 3_000, track = "newest"))

        dao.trimToNewest(QueuedScrobbleEntity.SERVICE_LASTFM, keep = 2)

        dao.oldestBatch(QueuedScrobbleEntity.SERVICE_LASTFM, limit = 10).map { it.track } shouldBe listOf("middle", "newest")
    }

    @Test
    fun `trimToNewest under the cap leaves the queue untouched`() = runTest {
        dao.enqueue(entity(startedAtEpochSec = 1_000, track = "a"))

        dao.trimToNewest(QueuedScrobbleEntity.SERVICE_LASTFM, keep = 5_000)

        dao.count(QueuedScrobbleEntity.SERVICE_LASTFM) shouldBe 1
    }

    @Test
    fun `trimToNewest only ever touches its own service`() = runTest {
        dao.enqueue(entity(startedAtEpochSec = 1_000, track = "a", service = "other-service"))

        dao.trimToNewest(QueuedScrobbleEntity.SERVICE_LASTFM, keep = 0)

        dao.count("other-service") shouldBe 1
    }

    @Test
    fun `an empty queue reports no rows`() = runTest {
        dao.oldestBatch(QueuedScrobbleEntity.SERVICE_LASTFM, limit = 10).shouldBeEmpty()
        dao.count(QueuedScrobbleEntity.SERVICE_LASTFM) shouldBe 0
    }
}
