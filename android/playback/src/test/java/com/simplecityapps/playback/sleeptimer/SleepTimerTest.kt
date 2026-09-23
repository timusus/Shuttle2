package com.simplecityapps.playback.sleeptimer

import com.simplecityapps.playback.PlaybackWatcher
import com.simplecityapps.playback.fakes.FakePlayback
import com.simplecityapps.playback.fakes.testPlaybackManager
import com.simplecityapps.playback.fakes.testSong
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
    private val events = mutableListOf<String>()
    private val playbackWatcher = PlaybackWatcher()
    private val playbackManager = testPlaybackManager(exoplayerPlayback = FakePlayback("A", events = events), playbackWatcher = playbackWatcher)

    init {
        // Drop what the manager set up on the playback, so only the timer's calls are recorded.
        events.clear()
    }

    private fun TestScope.sleepTimer() = SleepTimer(
        playbackManager = playbackManager,
        playbackWatcher = playbackWatcher,
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
        events shouldBe emptyList()
        sleepTimer.timeRemaining() shouldBe 1L

        advance(1)
        events shouldBe listOf("A pause")
        sleepTimer.timeRemaining() shouldBe null
    }

    @Test
    fun `a stopped timer never pauses`() = runTest {
        val sleepTimer = sleepTimer()
        sleepTimer.startTimer(60_000, playToEnd = false)
        advance(30_000)

        sleepTimer.stopTimer()
        advance(60_000)

        events shouldBe emptyList()
        sleepTimer.timeRemaining() shouldBe null
    }

    @Test
    fun `restarting the timer replaces the old deadline`() = runTest {
        val sleepTimer = sleepTimer()
        sleepTimer.startTimer(60_000, playToEnd = false)
        advance(30_000)

        sleepTimer.startTimer(60_000, playToEnd = false)
        advance(59_999)
        events shouldBe emptyList()

        advance(1)
        events shouldBe listOf("A pause")
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
    fun `playing to the end waits for the track to end after the deadline`() = runTest {
        val sleepTimer = sleepTimer()
        sleepTimer.startTimer(60_000, playToEnd = true)

        advance(30_000)
        playbackWatcher.onTrackEnded(testSong(1))
        events shouldBe emptyList()

        advance(60_000)
        events shouldBe emptyList()
        sleepTimer.timeRemaining() shouldBe 0L

        playbackWatcher.onTrackEnded(testSong(2))
        events shouldBe listOf("A pause")
        sleepTimer.timeRemaining() shouldBe null

        playbackWatcher.onTrackEnded(testSong(3))
        events shouldBe listOf("A pause")
    }

    @Test
    fun `a timer stopped while waiting for the track end never pauses`() = runTest {
        val sleepTimer = sleepTimer()
        sleepTimer.startTimer(60_000, playToEnd = true)
        advance(60_000)

        sleepTimer.stopTimer()
        playbackWatcher.onTrackEnded(testSong(1))

        events shouldBe emptyList()
    }
}
