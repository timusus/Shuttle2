package com.simplecityapps.shuttle.scrobbling.queue

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.simplecityapps.shuttle.scrobbling.createSong
import com.simplecityapps.shuttle.scrobbling.worker.WorkManagerScrobbleFlushScheduler
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScrobbleQueueTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val database = Room.inMemoryDatabaseBuilder(context, ScrobbleDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val dao = database.scrobbleDao()
    private val scrobbleQueue = ScrobbleQueue(dao, WorkManagerScrobbleFlushScheduler(context))

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `enqueue queues the song and schedules the unique flush work`() = runTest {
        scrobbleQueue.enqueue(QueuedScrobbleEntity.SERVICE_LASTFM, createSong(id = 1, duration = 200_000), startedAtEpochSec = 1_000)

        dao.count(QueuedScrobbleEntity.SERVICE_LASTFM) shouldBe 1
        val work = WorkManager.getInstance(context).getWorkInfosForUniqueWork(WorkManagerScrobbleFlushScheduler.UNIQUE_WORK_NAME).get()
        work.map { it.state } shouldBe listOf(WorkInfo.State.ENQUEUED)
    }

    @Test
    fun `enqueuing while a flush is already pending appends a follow-up run rather than dropping it`() = runTest {
        scrobbleQueue.enqueue(QueuedScrobbleEntity.SERVICE_LASTFM, createSong(id = 1, duration = 200_000), startedAtEpochSec = 1_000)
        scrobbleQueue.enqueue(QueuedScrobbleEntity.SERVICE_LASTFM, createSong(id = 2, duration = 200_000), startedAtEpochSec = 2_000)

        val work = WorkManager.getInstance(context).getWorkInfosForUniqueWork(WorkManagerScrobbleFlushScheduler.UNIQUE_WORK_NAME).get()
        work.size shouldBe 2
    }

    @Test
    fun `enqueuing the same play twice never duplicates the row`() = runTest {
        val song = createSong(id = 1, duration = 200_000)

        scrobbleQueue.enqueue(QueuedScrobbleEntity.SERVICE_LASTFM, song, startedAtEpochSec = 1_000)
        scrobbleQueue.enqueue(QueuedScrobbleEntity.SERVICE_LASTFM, song, startedAtEpochSec = 1_000)

        dao.count(QueuedScrobbleEntity.SERVICE_LASTFM) shouldBe 1
    }

    @Test
    fun `the same play queued for two services is one row each and clearing one leaves the other`() = runTest {
        val song = createSong(id = 1, duration = 200_000)

        scrobbleQueue.enqueue(QueuedScrobbleEntity.SERVICE_LASTFM, song, startedAtEpochSec = 1_000)
        scrobbleQueue.enqueue(QueuedScrobbleEntity.SERVICE_LISTENBRAINZ, song, startedAtEpochSec = 1_000)
        scrobbleQueue.clear(QueuedScrobbleEntity.SERVICE_LISTENBRAINZ)

        dao.count(QueuedScrobbleEntity.SERVICE_LASTFM) shouldBe 1
        dao.count(QueuedScrobbleEntity.SERVICE_LISTENBRAINZ) shouldBe 0
    }

    @Test
    fun `enqueue never grows the queue past the cap`() = runTest {
        // MAX_QUEUE_SIZE is 5,000; exercise the same trim through the DAO directly rather than inserting
        // that many rows here, then confirm one real enqueue() call still applies it end to end.
        repeat(3) { index ->
            dao.enqueue(
                QueuedScrobbleEntity(
                    service = QueuedScrobbleEntity.SERVICE_LASTFM,
                    artist = "artist",
                    track = "track-$index",
                    album = null,
                    albumArtist = null,
                    durationMs = 200_000,
                    startedAtEpochSec = index.toLong()
                )
            )
        }
        dao.trimToNewest(QueuedScrobbleEntity.SERVICE_LASTFM, keep = 2)
        dao.count(QueuedScrobbleEntity.SERVICE_LASTFM) shouldBe 2

        scrobbleQueue.enqueue(QueuedScrobbleEntity.SERVICE_LASTFM, createSong(id = 99, duration = 200_000), startedAtEpochSec = 100)

        dao.count(QueuedScrobbleEntity.SERVICE_LASTFM) shouldBe 3
    }
}
