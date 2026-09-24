package com.simplecityapps.playback

import com.simplecityapps.playback.fakes.FakePlayback
import com.simplecityapps.playback.fakes.FakeSharedPreferences
import com.simplecityapps.playback.fakes.testPlaybackManager
import com.simplecityapps.playback.fakes.testSong
import com.simplecityapps.playback.persistence.PlaybackPreferenceManager
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.squareup.moshi.Moshi
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test

/**
 * play() reloads a released playback before resuming, up to 2 attempts, resetting the seek
 * position to zero if it's within 200ms of the end of the song it reloads. If it's still released
 * after that, it gives up and pauses. An already-loaded playback seeks to
 * zero instead of reloading when resumed near the end.
 */
class PlaybackManagerPlayTest {
    private val events = mutableListOf<String>()
    private val queueManager = QueueManager(GeneralPreferenceManager(FakeSharedPreferences()))
    private val playbackPreferenceManager = PlaybackPreferenceManager(FakeSharedPreferences(), Moshi.Builder().build())

    private lateinit var playbackManager: PlaybackManager

    private fun createPlaybackManager(playback: FakePlayback) {
        playbackManager = testPlaybackManager(
            exoplayerPlayback = playback,
            queueManager = queueManager,
            playbackPreferenceManager = playbackPreferenceManager
        )
    }

    @Before
    fun setUp() {
        runBlocking { queueManager.setQueue(listOf(testSong(1, name = "Song", path = "/music/song.mp3"))) }
    }

    @Test
    fun `play reloads a released playback and resumes once the reload succeeds`() {
        val playback = FakePlayback("A", events = events)
        createPlaybackManager(playback)
        playback.isReleased = true
        events.clear()

        playbackManager.play()
        playback.completeLoad()

        events shouldBe listOf("A load Song seek 0", "A play", "A loadNext null")
    }

    @Test
    fun `play gives up after 2 reload attempts while the playback remains released, and pauses`() {
        val playback = FakePlayback("A", events = events, resetReleasedOnLoad = false)
        createPlaybackManager(playback)
        playback.isReleased = true
        events.clear()

        playbackManager.play()
        playback.completeLoad()
        playback.completeLoad()

        events.filterNot { it.contains("loadNext") } shouldBe listOf("A load Song seek 0", "A load Song seek 0", "A pause")
        playbackManager.playbackStateFlow.value shouldBe PlaybackState.Paused
    }

    @Test
    fun `play resets the seek position to zero when reloading within 200ms of the track's end`() {
        val playback = FakePlayback("A", events = events)
        createPlaybackManager(playback)
        playback.isReleased = true
        playbackPreferenceManager.playbackPosition = 179_850
        events.clear()

        playbackManager.play()

        events shouldBe listOf("A load Song seek 0")
    }

    @Test
    fun `play reloads at the saved position by the song's length, not the released playback's`() {
        // The released playback may still report the length of whatever it last held.
        val playback = FakePlayback("A", events = events)
        createPlaybackManager(playback)
        playback.isReleased = true
        playback.durationMs = 5_000
        playbackPreferenceManager.playbackPosition = 150_000
        events.clear()

        playbackManager.play()

        events shouldBe listOf("A load Song seek 150000")
    }

    @Test
    fun `play seeks to zero before resuming an already-loaded playback within 200ms of the track's end`() {
        val playback = FakePlayback("A", events = events)
        createPlaybackManager(playback)
        playback.isReleased = false
        playback.durationMs = 5_000
        playback.progressMs = 4_850
        events.clear()

        playbackManager.play()

        events shouldBe listOf("A seek 0", "A play")
    }
}
