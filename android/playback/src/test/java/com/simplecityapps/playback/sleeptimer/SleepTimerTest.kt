package com.simplecityapps.playback.sleeptimer

import com.simplecityapps.fakes.FakeQueueOperations
import com.simplecityapps.playback.fakes.FakePlaybackOperations
import com.simplecityapps.playback.fakes.testSong
import com.simplecityapps.playback.queue.QueueItem
import com.simplecityapps.playback.queue.QueueState
import com.simplecityapps.shuttle.model.Song
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * [SleepTimer] pauses playback at its deadline, or at the first track end after it when playing to the
 * end, unless it's stopped or restarted first.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SleepTimerTest {
    private val playbackOperations = FakePlaybackOperations()
    private val queueOperations = FakeQueueOperations()

    /** The number of pauses the timer asked for. */
    private fun pauses() = playbackOperations.pauses

    /** Plays the current track to its end, and runs whatever it resumes. */
    private fun TestScope.endTrack() {
        playbackOperations.endItem()
        runCurrent()
    }

    private fun TestScope.sleepTimer() = SleepTimer(
        playbackOperations = playbackOperations,
        queueOperations = queueOperations,
        appCoroutineScope = backgroundScope,
        context = StandardTestDispatcher(testScheduler),
        elapsedRealtime = { testScheduler.currentTime }
    )

    /** Advances virtual time by [ms] and runs whatever is due at the new time. */
    private fun TestScope.advance(ms: Long) {
        advanceTimeBy(ms)
        runCurrent()
    }

    @Test
    fun `pauses playback at the deadline`() = runTest {
        val sleepTimer = sleepTimer()
        sleepTimer.startTimer(60_000, playToEnd = false)

        advance(59_999)
        pauses() shouldBe 0
        sleepTimer.timeRemaining() shouldBe 1L

        advance(1)
        pauses() shouldBe 1
        sleepTimer.timeRemaining() shouldBe null
    }

    @Test
    fun `a stopped timer never pauses`() = runTest {
        val sleepTimer = sleepTimer()
        sleepTimer.startTimer(60_000, playToEnd = false)
        advance(30_000)

        sleepTimer.stopTimer()
        advance(60_000)

        pauses() shouldBe 0
        sleepTimer.timeRemaining() shouldBe null
    }

    @Test
    fun `restarting the timer replaces the old deadline`() = runTest {
        val sleepTimer = sleepTimer()
        sleepTimer.startTimer(60_000, playToEnd = false)
        advance(30_000)

        sleepTimer.startTimer(60_000, playToEnd = false)
        advance(59_999)
        pauses() shouldBe 0

        advance(1)
        pauses() shouldBe 1
    }

    @Test
    fun `reports the time remaining until the deadline`() = runTest {
        val sleepTimer = sleepTimer()
        sleepTimer.timeRemaining() shouldBe null

        sleepTimer.startTimer(60_000, playToEnd = false)
        advance(15_000)

        sleepTimer.timeRemaining() shouldBe 45_000L
    }

    @Test
    fun `playing to the end pauses at the end of the track playing after the deadline`() = runTest {
        val sleepTimer = sleepTimer()
        sleepTimer.startTimer(60_000, playToEnd = true)

        advance(30_000)
        endTrack()
        pauses() shouldBe 0
        playbackOperations.pausingAtEnd shouldBe 0

        advance(60_000)
        pauses() shouldBe 0
        playbackOperations.pausingAtEnd shouldBe 1
        sleepTimer.timeRemaining() shouldBe 0L

        endTrack()
        pauses() shouldBe 1
        playbackOperations.pausingAtEnd shouldBe 0
        sleepTimer.timeRemaining() shouldBe null

        endTrack()
        pauses() shouldBe 1
    }

    @Test
    fun `a timer that doesn't play to the end never waits for the track end`() = runTest {
        val sleepTimer = sleepTimer()
        sleepTimer.startTimer(60_000, playToEnd = false)

        advance(60_000)

        pauses() shouldBe 1
        playbackOperations.pausingAtEnd shouldBe 0
    }

    @Test
    fun `a timer stopped while waiting for the track end never pauses, and stops waiting`() = runTest {
        val sleepTimer = sleepTimer()
        sleepTimer.startTimer(60_000, playToEnd = true)
        advance(60_000)
        playbackOperations.pausingAtEnd shouldBe 1

        sleepTimer.stopTimer()
        runCurrent()
        playbackOperations.pausingAtEnd shouldBe 0

        endTrack()
        pauses() shouldBe 0
    }

    @Test
    fun `restarting the timer while waiting for the track end waits for the new deadline`() = runTest {
        val sleepTimer = sleepTimer()
        sleepTimer.startTimer(0, playToEnd = true)
        advance(0)
        playbackOperations.pausingAtEnd shouldBe 1

        sleepTimer.startTimer(60_000, playToEnd = true)
        runCurrent()
        playbackOperations.pausingAtEnd shouldBe 0

        endTrack()
        pauses() shouldBe 0
    }

    private fun movement(id: Long) = testSong(id, name = "Movement $id").copy(work = "Sonata No. 3", composer = "Ludwig Harrow", conductor = "Ada Quill")

    private fun play(
        songs: List<Song>,
        position: Int
    ) {
        val items = songs.mapIndexed { index, song -> QueueItem(uid = song.id, song = song, isCurrent = index == position) }
        queueOperations.queueStateFlow.value = QueueState(items = items, currentItem = items[position], currentPosition = position)
    }

    private val sonata get() = listOf(movement(1), movement(2), movement(3))

    @Test
    fun `end of work pauses when the last movement ends`() = runTest {
        play(sonata, position = 0)
        sleepTimer().startTimerAtEndOfWork()
        runCurrent()

        endTrack()
        pauses() shouldBe 0
        play(sonata, position = 1)
        runCurrent()
        endTrack()
        pauses() shouldBe 0
        play(sonata, position = 2)
        runCurrent()
        endTrack()
        pauses() shouldBe 1
    }

    @Test
    fun `end of work follows a queue that grows past the work`() = runTest {
        play(sonata, position = 2)
        sleepTimer().startTimerAtEndOfWork()
        runCurrent()

        play(sonata + testSong(9), position = 2)
        runCurrent()
        endTrack()
        pauses() shouldBe 1
    }

    @Test
    fun `end of work on a lone song pauses at its end`() = runTest {
        play(listOf(testSong(9)), position = 0)
        sleepTimer().startTimerAtEndOfWork()
        runCurrent()

        endTrack()
        pauses() shouldBe 1
    }

    @Test
    fun `a stopped end of work timer never pauses`() = runTest {
        play(sonata, position = 2)
        val sleepTimer = sleepTimer()
        sleepTimer.startTimerAtEndOfWork()
        runCurrent()

        sleepTimer.stopTimer()
        runCurrent()
        endTrack()
        pauses() shouldBe 0
        sleepTimer.timeRemaining() shouldBe null
    }

    @Test
    fun `end of work reports waiting while it runs and off after pausing`() = runTest {
        play(sonata, position = 2)
        val sleepTimer = sleepTimer()
        sleepTimer.startTimerAtEndOfWork()
        runCurrent()
        sleepTimer.timeRemaining() shouldBe 0L

        endTrack()
        sleepTimer.timeRemaining() shouldBe null
    }
}
