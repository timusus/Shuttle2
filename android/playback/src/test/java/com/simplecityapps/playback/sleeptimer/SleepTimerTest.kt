package com.simplecityapps.playback.sleeptimer

import com.simplecityapps.playback.fakes.FakePlayback
import com.simplecityapps.playback.fakes.FakeSharedPreferences
import com.simplecityapps.playback.fakes.testPlaybackManager
import com.simplecityapps.playback.fakes.testSong
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
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
    private val queueManager = QueueManager(GeneralPreferenceManager(FakeSharedPreferences()))
    private val playbackManager =
        testPlaybackManager(exoplayerPlayback = FakePlayback("A", events = events), queueManager = queueManager)

    init {
        runBlocking { queueManager.setQueue((1L..5L).map { testSong(it) }) }
    }

    /** The pauses the timer asked for; a track end also moves the queue on, which records other calls. */
    private fun pauses() = events.filter { it == "A pause" }

    /** Ends the current track the way the playback reports it, and runs whatever it resumes. */
    private fun TestScope.endTrack() {
        playbackManager.onTrackEnded(trackWentToNext = true)
        runCurrent()
    }

    private fun TestScope.sleepTimer() = SleepTimer(
        playbackManager = playbackManager,
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
        pauses() shouldBe emptyList()
        sleepTimer.timeRemaining() shouldBe 1L

        advance(1)
        pauses() shouldBe listOf("A pause")
        sleepTimer.timeRemaining() shouldBe null
    }

    @Test
    fun `a stopped timer never pauses`() = runTest {
        val sleepTimer = sleepTimer()
        sleepTimer.startTimer(60_000, playToEnd = false)
        advance(30_000)

        sleepTimer.stopTimer()
        advance(60_000)

        pauses() shouldBe emptyList()
        sleepTimer.timeRemaining() shouldBe null
    }

    @Test
    fun `restarting the timer replaces the old deadline`() = runTest {
        val sleepTimer = sleepTimer()
        sleepTimer.startTimer(60_000, playToEnd = false)
        advance(30_000)

        sleepTimer.startTimer(60_000, playToEnd = false)
        advance(59_999)
        pauses() shouldBe emptyList()

        advance(1)
        pauses() shouldBe listOf("A pause")
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
        endTrack()
        pauses() shouldBe emptyList()

        advance(60_000)
        pauses() shouldBe emptyList()
        sleepTimer.timeRemaining() shouldBe 0L

        endTrack()
        pauses() shouldBe listOf("A pause")
        sleepTimer.timeRemaining() shouldBe null

        endTrack()
        pauses() shouldBe listOf("A pause")
    }

    @Test
    fun `a timer stopped while waiting for the track end never pauses`() = runTest {
        val sleepTimer = sleepTimer()
        sleepTimer.startTimer(60_000, playToEnd = true)
        advance(60_000)

        sleepTimer.stopTimer()
        endTrack()

        pauses() shouldBe emptyList()
    }
}
