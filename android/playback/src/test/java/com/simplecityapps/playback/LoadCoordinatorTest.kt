package com.simplecityapps.playback

import com.simplecityapps.playback.fakes.FakePlayback
import com.simplecityapps.playback.fakes.testSong
import com.simplecityapps.shuttle.model.Song
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * [LoadCoordinator] delivers only the latest load's completion, holds a seek made during a load as
 * that load's start position, and runs next-item preparation one at a time, last request winning.
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
        onPendingLoadChanged = { pendingLoadChanges++ }
    )

    private fun LoadCoordinator.load(
        id: Long,
        positionMs: Int = 0,
        completion: (Result<Any?>) -> Unit = { result -> results += "Song$id ${if (result.isSuccess) "loaded" else "failed"}" }
    ) = load(playback, testSong(id), null, positionMs, completion)

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
                    next: Song?,
                    seekPosition: Int,
                    completion: (Result<Any?>) -> Unit
                ): Unit = throw IllegalStateException("boom")
            }
        val coordinator = coordinator()
        coordinator.load(throwing, testSong(1), null, 0) { result -> results += "failed: ${result.exceptionOrNull()?.message}" }
        runCurrent()

        results shouldBe listOf("failed: boom")
        coordinator.pendingLoad shouldBe null
    }

    @Test
    fun `next-item requests run one at a time and the last one wins`() = runTest {
        // #263: independent launches could finish out of order, leaving a stale next item queued.
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

        events shouldBe listOf("loadNext Song1 start", "loadNext Song1 end", "loadNext Song3 start", "loadNext Song3 end")
    }

    @Test
    fun `a slow next-item request finishes before a later fast one starts`() = runTest {
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

        events shouldBe listOf("loadNext Song1 start", "loadNext Song1 end", "loadNext Song2 start", "loadNext Song2 end")
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
