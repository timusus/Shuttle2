package com.simplecityapps.playback.sleeptimer

import com.simplecityapps.playback.fakes.FakePlaybackOperations
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

    /** The number of pauses the timer asked for. */
    private fun pauses() = playbackOperations.pauses

    /** Plays the current track to its end, and runs whatever it resumes. */
    private fun TestScope.endTrack() {
        playbackOperations.endItem()
        runCurrent()
    }

    private fun TestScope.sleepTimer() = SleepTimer(
        playbackOperations = playbackOperations,
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
}
