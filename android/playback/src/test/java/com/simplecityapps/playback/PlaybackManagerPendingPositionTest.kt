package com.simplecityapps.playback

import com.simplecityapps.playback.fakes.FakePlayback
import com.simplecityapps.playback.fakes.FakeSharedPreferences
import com.simplecityapps.playback.fakes.testPlaybackManager
import com.simplecityapps.playback.fakes.testSong
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.playback.queue.QueueWatcher
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test

/**
 * While a load is pending the playback still reports the item it's replacing, so the position is the
 * one the load will start at, and play()'s near-end check reads that position against the item being
 * loaded (#300). Once the load has completed, the playback reports its own position again.
 */
class PlaybackManagerPendingPositionTest {
    private val events = mutableListOf<String>()
    private val queueWatcher = QueueWatcher()
    private val queueManager = QueueManager(queueWatcher, GeneralPreferenceManager(FakeSharedPreferences()))
    private val playback = FakePlayback("A", events = events)

    private lateinit var playbackManager: PlaybackManager

    @Before
    fun setUp() {
        playbackManager =
            testPlaybackManager(
                exoplayerPlayback = playback,
                queueWatcher = queueWatcher,
                queueManager = queueManager
            )
        runBlocking { queueManager.setQueue(listOf(testSong(1, duration = 180_000), testSong(2, duration = 180_000))) }
        // The first song is loaded and nearly finished.
        playback.progressMs = 179_900
        playback.durationMs = 180_000
    }

    @Test
    fun `progress is the pending load's start position while it loads`() {
        playbackManager.skipToNext()

        playbackManager.getProgress() shouldBe 0
    }

    @Test
    fun `progress follows a seek made while the load is pending`() {
        playbackManager.skipToNext()

        playbackManager.seekTo(30_000)

        playbackManager.getProgress() shouldBe 30_000
    }

    @Test
    fun `progress is the playback's own once the load completes`() {
        playbackManager.skipToNext()
        playback.progressMs = 1_234

        playback.completeLoad()

        playbackManager.getProgress() shouldBe 1_234
    }

    @Test
    fun `play while a load is pending does not seek the playback it is replacing`() {
        playbackManager.skipToNext()
        events.clear()

        playbackManager.play()

        events shouldNotContain "A seek 0"
        events shouldBe listOf("A play")
    }

    @Test
    fun `play while a load is pending near the loading item's end restarts the load from zero`() {
        playbackManager.load(179_900) {}
        playback.progressMs = 10_000
        events.clear()

        playbackManager.play()

        events shouldBe listOf("A play")
        playbackManager.getProgress() shouldBe 0

        playback.completeLoad()

        events shouldBe listOf("A play", "A seek 0")
    }
}
