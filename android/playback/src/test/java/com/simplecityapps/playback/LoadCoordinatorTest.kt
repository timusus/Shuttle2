package com.simplecityapps.playback

import com.simplecityapps.playback.fakes.FakePlayback
import com.simplecityapps.playback.fakes.testSong
import com.simplecityapps.shuttle.model.Song
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * [LoadCoordinator] delivers only the latest load's completion, holds a seek made during a load as
 * that load's start position, and applies only the latest next-item request, preparing the next item once a load succeeds.
 * Runs on the test's virtual-time dispatcher, so every interleaving is explicit. The coordinator
 * runs in backgroundScope, which advanceUntilIdle ignores, so time is advanced explicitly.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LoadCoordinatorTest {
    private val events = mutableListOf<String>()
    private val fake = FakePlayback("A", events = events)

    /** Delegates to [fake], except that loadNext takes [loadNextDelayMs] (per song) to finish. */
    private val playback =
        object : Playback by fake {
            override suspend fun loadNext(song: Song?) {
                events += "loadNext ${song?.name} start"
                delay(loadNextDelayMs[song?.name] ?: 0L)
                events += "loadNext ${song?.name} end"
            }
        }

    private val loadNextDelayMs = mutableMapOf<String?, Long>()

    private var nextSong: Song? = null

    private var pendingLoadChanges = 0

    private val results = mutableListOf<String>()

    private fun TestScope.coordinator() = LoadCoordinator(
        parentScope = backgroundScope,
        activePlayback = { playback },
        nextSong = { nextSong },
        onPendingLoadChanged = { pendingLoadChanges++ },
        loadTimeoutMs = LOAD_TIMEOUT_MS
    )

    private fun LoadCoordinator.load(
        id: Long,
        positionMs: Int = 0,
        completion: (Result<Any?>) -> Unit = { result -> results += "Song$id ${if (result.isSuccess) "loaded" else "failed"}" }
    ) = load(playback, testSong(id), positionMs, completion)

    @Test
    fun `a load's completion is delivered and clears the pending load`() = runTest {
        val coordinator = coordinator()
        coordinator.load(1, positionMs = 3_000)
        runCurrent()

        coordinator.pendingLoad!!.positionMs shouldBe 3_000
        events shouldBe listOf("A load Song1 seek 3000")

        fake.completeLoad()

        results shouldBe listOf("Song1 loaded")
        coordinator.pendingLoad shouldBe null
    }

    @Test
    fun `a superseded load's completion is ignored`() = runTest {
        // #293: rapid skips on a slow provider; the first load finishing mustn't play or seek.
        val coordinator = coordinator()
        coordinator.load(1)
        runCurrent()
        coordinator.load(2, positionMs = 1_000)
        runCurrent()

        fake.completeLoad()

        results.shouldBeEmpty()
        coordinator.pendingLoad!!.positionMs shouldBe 1_000

        fake.completeLoad()

        results shouldBe listOf("Song2 loaded")
        coordinator.pendingLoad shouldBe null
    }

    @Test
    fun `a superseded load's failure is ignored`() = runTest {
        val coordinator = coordinator()
        coordinator.load(1, positionMs = 5_000)
        runCurrent()
        coordinator.load(2, positionMs = 1_000)
        runCurrent()

        fake.failLoad()

        results.shouldBeEmpty()
        coordinator.pendingLoad!!.positionMs shouldBe 1_000
    }

    @Test
    fun `a load superseded before it starts never reaches the playback`() = runTest {
        val coordinator = coordinator()
        coordinator.load(1)
        coordinator.load(2)
        runCurrent()

        events shouldBe listOf("A load Song2 seek 0")
    }

    @Test
    fun `a seek during a load becomes its start position and is applied on completion`() = runTest {
        // #295: the seek used to go to the track being replaced, and the anchor ignored it.
        val coordinator = coordinator()
        coordinator.load(1)
        runCurrent()
        val changesBeforeSeek = pendingLoadChanges

        coordinator.seek(42_000) shouldBe true

        coordinator.pendingLoad!!.positionMs shouldBe 42_000
        pendingLoadChanges shouldBe changesBeforeSeek + 1
        events shouldBe listOf("A load Song1 seek 0")

        fake.completeLoad()

        events shouldBe listOf("A load Song1 seek 0", "A seek 42000")
        results shouldBe listOf("Song1 loaded")
    }

    @Test
    fun `the seek is applied before the completion runs`() = runTest {
        val coordinator = coordinator()
        coordinator.load(1) { events += "completion" }
        runCurrent()
        coordinator.seek(42_000)

        fake.completeLoad()

        events shouldBe listOf("A load Song1 seek 0", "A seek 42000", "completion")
    }

    @Test
    fun `a seek during a load that fails is not applied`() = runTest {
        val coordinator = coordinator()
        coordinator.load(1)
        runCurrent()
        coordinator.seek(42_000)

        fake.failLoad()

        events shouldBe listOf("A load Song1 seek 0")
        results shouldBe listOf("Song1 failed")
    }

    @Test
    fun `a seek with no load pending is left to the caller`() = runTest {
        val coordinator = coordinator()

        coordinator.seek(1_000) shouldBe false

        coordinator.load(1)
        runCurrent()
        fake.completeLoad()

        coordinator.seek(1_000) shouldBe false
    }

    @Test
    fun `a seek from the completion itself is left to the caller`() = runTest {
        val coordinator = coordinator()
        var seekHandled: Boolean? = null
        coordinator.load(1) { seekHandled = coordinator.seek(5_000) }
        runCurrent()

        fake.completeLoad()

        seekHandled shouldBe false
    }

    @Test
    fun `a load started from a failed load's completion keeps its own pending position`() = runTest {
        val coordinator = coordinator()
        coordinator.load(1, positionMs = 3_000) { coordinator.load(2, positionMs = 0) }
        runCurrent()

        fake.failLoad()
        runCurrent()

        coordinator.pendingLoad!!.positionMs shouldBe 0
        events shouldBe listOf("A load Song1 seek 3000", "A load Song2 seek 0")
    }

    /** Delegates to [fake], except that a load's completion is kept in [reportedCompletions] for the test to call. */
    private val reportedCompletions = mutableListOf<(Result<Any?>) -> Unit>()

    private val reportingPlayback =
        object : Playback by fake {
            override suspend fun load(
                current: Song,
                seekPosition: Int,
                completion: (Result<Any?>) -> Unit
            ) {
                reportedCompletions += completion
            }
        }

    @Test
    fun `a load reported successful twice completes once`() = runTest {
        val coordinator = coordinator()
        coordinator.load(reportingPlayback, testSong(1), 0) { results += "Song1 loaded" }
        runCurrent()
        coordinator.seek(42_000)

        reportedCompletions.single()(Result.success(null))
        reportedCompletions.single()(Result.success(null))

        results shouldBe listOf("Song1 loaded")
        events shouldBe listOf("A seek 42000")
    }

    @Test
    fun `a load reported failed twice completes once`() = runTest {
        val coordinator = coordinator()
        coordinator.load(reportingPlayback, testSong(1), 0) { results += "Song1 failed" }
        runCurrent()

        reportedCompletions.single()(Result.failure(RuntimeException("load failed")))
        reportedCompletions.single()(Result.failure(RuntimeException("load failed")))

        results shouldBe listOf("Song1 failed")
        coordinator.pendingLoad shouldBe null
    }

    @Test
    fun `cancel abandons the load in progress`() = runTest {
        // Clearing the queue, or removing its only item, mid-load.
        val coordinator = coordinator()
        coordinator.load(1)
        runCurrent()

        coordinator.cancel()
        fake.completeLoad()

        results.shouldBeEmpty()
        coordinator.pendingLoad shouldBe null
        coordinator.seek(1_000) shouldBe false
    }

    @Test
    fun `an exception from the playback's load fails the load`() = runTest {
        val throwing =
            object : Playback by fake {
                override suspend fun load(
                    current: Song,
                    seekPosition: Int,
                    completion: (Result<Any?>) -> Unit
                ): Unit = throw IllegalStateException("boom")
            }
        val coordinator = coordinator()
        coordinator.load(throwing, testSong(1), 0) { result -> results += "failed: ${result.exceptionOrNull()?.message}" }
        runCurrent()

        results shouldBe listOf("failed: boom")
        coordinator.pendingLoad shouldBe null
    }

    @Test
    fun `a next-item request supersedes one still in flight, so only the latest is applied`() = runTest {
        // #263, #315: requests that finish out of order could leave a stale next item queued.
        val coordinator = coordinator()
        runCurrent()
        loadNextDelayMs["Song1"] = 1_000
        nextSong = testSong(1)
        coordinator.requestNext()
        runCurrent()

        nextSong = testSong(2)
        coordinator.requestNext()
        nextSong = testSong(3)
        coordinator.requestNext()
        advanceTimeBy(2_000)
        runCurrent()

        events shouldBe listOf("loadNext Song1 start", "loadNext Song3 start", "loadNext Song3 end")
    }

    @Test
    fun `a slow next-item request is abandoned for a later one`() = runTest {
        val coordinator = coordinator()
        runCurrent()
        loadNextDelayMs["Song1"] = 1_000
        nextSong = testSong(1)
        coordinator.requestNext()
        runCurrent()
        nextSong = testSong(2)
        coordinator.requestNext()
        advanceTimeBy(2_000)
        runCurrent()

        events shouldBe listOf("loadNext Song1 start", "loadNext Song2 start", "loadNext Song2 end")
    }

    @Test
    fun `a next-item request waits for the pending load, then reads the next song`() = runTest {
        val coordinator = coordinator()
        runCurrent()
        coordinator.load(1)
        nextSong = testSong(2)
        coordinator.requestNext()
        runCurrent()

        events shouldBe listOf("A load Song1 seek 0")

        nextSong = testSong(3)
        fake.completeLoad()
        runCurrent()

        events shouldBe listOf("A load Song1 seek 0", "loadNext Song3 start", "loadNext Song3 end")
    }

    @Test
    fun `a load supersedes a next-item request made before it, then prepares the next item once it succeeds`() = runTest {
        // The load replaces the playlist the request would have edited.
        val coordinator = coordinator()
        runCurrent()
        nextSong = testSong(2)
        coordinator.requestNext()
        coordinator.load(1)
        runCurrent()

        fake.completeLoad()
        runCurrent()

        events shouldBe listOf("A load Song1 seek 0", "loadNext Song2 start", "loadNext Song2 end")
    }

    @Test
    fun `a failed load prepares no next item`() = runTest {
        val coordinator = coordinator()
        runCurrent()
        nextSong = testSong(2)
        coordinator.load(1)
        runCurrent()

        fake.failLoad()
        runCurrent()

        events shouldBe listOf("A load Song1 seek 0")
    }

    @Test
    fun `a load started while a next-item request waits supersedes it`() = runTest {
        val coordinator = coordinator()
        runCurrent()
        coordinator.load(1)
        coordinator.requestNext()
        runCurrent()

        coordinator.load(2)
        runCurrent()
        nextSong = testSong(3)
        fake.completeLoad()
        fake.completeLoad()
        runCurrent()

        // Song1's load was superseded, so only Song2's success prepares the next item.
        events shouldBe listOf("A load Song1 seek 0", "A load Song2 seek 0", "loadNext Song3 start", "loadNext Song3 end")
    }

    @Test
    fun `a load that times out stays pending and delivers nothing`() = runTest {
        val coordinator = coordinator()
        coordinator.load(1, positionMs = 3_000)
        runCurrent()

        advanceTimeBy(LOAD_TIMEOUT_MS * 2)
        runCurrent()

        results.shouldBeEmpty()
        coordinator.pendingLoad!!.positionMs shouldBe 3_000
        events shouldBe listOf("A load Song1 seek 3000")
    }

    @Test
    fun `a success reported after the load timed out is delivered`() = runTest {
        val coordinator = coordinator()
        coordinator.load(1)
        runCurrent()
        advanceTimeBy(LOAD_TIMEOUT_MS)
        runCurrent()

        fake.completeLoad()

        results shouldBe listOf("Song1 loaded")
        coordinator.pendingLoad shouldBe null
    }

    @Test
    fun `a seek during a load that timed out is applied when it completes`() = runTest {
        val coordinator = coordinator()
        coordinator.load(1)
        runCurrent()
        advanceTimeBy(LOAD_TIMEOUT_MS)
        runCurrent()

        coordinator.seek(4_000) shouldBe true
        fake.completeLoad()

        events shouldBe listOf("A load Song1 seek 0", "A seek 4000")
        results shouldBe listOf("Song1 loaded")
    }

    @Test
    fun `a failure reported after the load timed out is delivered`() = runTest {
        val coordinator = coordinator()
        coordinator.load(1)
        runCurrent()
        advanceTimeBy(LOAD_TIMEOUT_MS)
        runCurrent()

        fake.failLoad()

        results shouldBe listOf("Song1 failed")
        coordinator.pendingLoad shouldBe null
    }

    @Test
    fun `a load still resolving when it times out is not abandoned`() = runTest {
        val resolved = CompletableDeferred<Unit>()
        val slow =
            object : Playback by fake {
                override suspend fun load(
                    current: Song,
                    seekPosition: Int,
                    completion: (Result<Any?>) -> Unit
                ) {
                    resolved.await()
                    completion(Result.success(null))
                }
            }
        val coordinator = coordinator()
        coordinator.load(slow, testSong(1), 0) { results += "Song1 loaded" }
        runCurrent()
        advanceTimeBy(LOAD_TIMEOUT_MS)
        runCurrent()

        resolved.complete(Unit)
        runCurrent()

        results shouldBe listOf("Song1 loaded")
    }

    @Test
    fun `a load that reports in time never times out`() = runTest {
        val coordinator = coordinator()
        runCurrent()
        coordinator.load(1)
        runCurrent()
        fake.completeLoad()

        advanceTimeBy(LOAD_TIMEOUT_MS * 2)
        runCurrent()

        results shouldBe listOf("Song1 loaded")
        events shouldBe listOf("A load Song1 seek 0", "loadNext null start", "loadNext null end")
    }

    @Test
    fun `a next-item request waiting on a load that never reports runs once it times out`() = runTest {
        val coordinator = coordinator()
        runCurrent()
        coordinator.load(1)
        nextSong = testSong(2)
        coordinator.requestNext()
        runCurrent()

        advanceTimeBy(LOAD_TIMEOUT_MS - 1)
        runCurrent()

        events shouldBe listOf("A load Song1 seek 0")

        advanceTimeBy(1)
        runCurrent()

        events shouldBe listOf("A load Song1 seek 0", "loadNext Song2 start", "loadNext Song2 end")
        coordinator.pendingLoad shouldNotBe null
    }

    @Test
    fun `a next-item request made after a load timed out runs straight away`() = runTest {
        val coordinator = coordinator()
        runCurrent()
        coordinator.load(1)
        runCurrent()
        advanceTimeBy(LOAD_TIMEOUT_MS)
        runCurrent()

        nextSong = testSong(2)
        coordinator.requestNext()
        runCurrent()

        events shouldBe listOf("A load Song1 seek 0", "loadNext Song2 start", "loadNext Song2 end")
    }

    @Test
    fun `a load that timed out re-prepares the next item once it succeeds`() = runTest {
        // A next item prepared while the load was timed out went into the playlist the load then replaced.
        val resolved = CompletableDeferred<Unit>()
        val slow =
            object : Playback by playback {
                override suspend fun load(
                    current: Song,
                    seekPosition: Int,
                    completion: (Result<Any?>) -> Unit
                ) {
                    resolved.await()
                    completion(Result.success(null))
                }
            }
        val coordinator = coordinator()
        runCurrent()
        coordinator.load(slow, testSong(1), 0) { results += "Song1 loaded" }
        runCurrent()
        advanceTimeBy(LOAD_TIMEOUT_MS)
        runCurrent()
        nextSong = testSong(3)
        coordinator.requestNext()
        runCurrent()

        resolved.complete(Unit)
        runCurrent()

        results shouldBe listOf("Song1 loaded")
        events shouldBe listOf(
            "loadNext Song3 start",
            "loadNext Song3 end",
            "loadNext Song3 start",
            "loadNext Song3 end"
        )
    }

    @Test
    fun `a load that timed out is superseded by the next load`() = runTest {
        val coordinator = coordinator()
        runCurrent()
        coordinator.load(1)
        runCurrent()
        advanceTimeBy(LOAD_TIMEOUT_MS)
        runCurrent()

        coordinator.load(2)
        runCurrent()
        nextSong = testSong(3)
        coordinator.requestNext()
        runCurrent()

        fake.completeLoad() // Song1, superseded

        results.shouldBeEmpty()
        events shouldBe listOf("A load Song1 seek 0", "A load Song2 seek 0")

        fake.completeLoad()
        runCurrent()

        results shouldBe listOf("Song2 loaded")
        events shouldBe listOf("A load Song1 seek 0", "A load Song2 seek 0", "loadNext Song3 start", "loadNext Song3 end")
    }

    @Test
    fun `a load that timed out is abandoned by cancel`() = runTest {
        val coordinator = coordinator()
        coordinator.load(1)
        runCurrent()
        advanceTimeBy(LOAD_TIMEOUT_MS)
        runCurrent()

        coordinator.cancel()
        fake.completeLoad()

        results.shouldBeEmpty()
        coordinator.pendingLoad shouldBe null
    }

    @Test
    fun `a superseded load does not time out`() = runTest {
        val coordinator = coordinator()
        runCurrent()
        coordinator.load(1)
        runCurrent()
        advanceTimeBy(LOAD_TIMEOUT_MS / 2)
        coordinator.load(2)
        nextSong = testSong(3)
        coordinator.requestNext()
        runCurrent()

        advanceTimeBy(LOAD_TIMEOUT_MS / 2 + 1)
        runCurrent()

        events shouldBe listOf("A load Song1 seek 0", "A load Song2 seek 0")

        advanceTimeBy(LOAD_TIMEOUT_MS / 2)
        runCurrent()

        events shouldBe listOf("A load Song1 seek 0", "A load Song2 seek 0", "loadNext Song3 start", "loadNext Song3 end")
    }

    @Test
    fun `a next-item request resumes when the pending load is cancelled`() = runTest {
        val coordinator = coordinator()
        runCurrent()
        coordinator.load(1)
        coordinator.requestNext()
        runCurrent()

        coordinator.cancel()
        runCurrent()

        events shouldBe listOf("A load Song1 seek 0", "loadNext null start", "loadNext null end")
    }
}

private const val LOAD_TIMEOUT_MS = 30_000L
