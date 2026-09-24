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
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

/**
 * A reported pause saves the position to resume from before the report returns (#276), read from
 * [PlaybackManager.getProgress], so a pause mid-load saves the position the load will start at. The
 * current song and that position are then published on [PlaybackManager.pausePositionFlow].
 */
class PlaybackManagerPausePositionTest {
    private val queueManager = QueueManager(GeneralPreferenceManager(FakeSharedPreferences()))
    private val preferences = PlaybackPreferenceManager(FakeSharedPreferences(), Moshi.Builder().build())
    private val playback = FakePlayback("A")
    private val playbackManager =
        testPlaybackManager(
            exoplayerPlayback = playback,
            queueManager = queueManager,
            playbackPreferenceManager = preferences
        )

    @Before
    fun setUp() {
        runBlocking { queueManager.setQueue(listOf(testSong(1, duration = 180_000), testSong(2, duration = 180_000))) }
        playback.durationMs = 180_000
    }

    @Test
    fun `a pause saves the playback's position`() {
        playback.progressMs = 42_000

        playbackManager.onPlaybackStateChanged(PlaybackState.Paused)

        preferences.playbackPosition shouldBe 42_000
    }

    @Test
    fun `a pause while a load is pending saves the position the load will start at`() {
        playback.progressMs = 179_900
        playbackManager.skipToNext()
        playbackManager.seekTo(30_000)

        playbackManager.onPlaybackStateChanged(PlaybackState.Paused)

        preferences.playbackPosition shouldBe 30_000
    }

    @Test
    fun `a pause with no position clears the saved one`() {
        preferences.playbackPosition = 65_000
        playback.progressMs = null

        playbackManager.onPlaybackStateChanged(PlaybackState.Paused)

        preferences.playbackPosition shouldBe null
    }

    @Test
    fun `a pause publishes the current song and its position, 0 when there is none`() = runTest {
        val paused = mutableListOf<SongPosition>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            playbackManager.pausePositionFlow.collect { paused += it }
        }

        playback.progressMs = 42_000
        playbackManager.onPlaybackStateChanged(PlaybackState.Paused)
        playback.progressMs = null
        playbackManager.onPlaybackStateChanged(PlaybackState.Paused)

        paused.map { it.song.id to it.positionMs } shouldBe listOf(1L to 42_000, 1L to 0)
    }

    @Test
    fun `other states neither save a position nor publish one`() = runTest {
        val paused = mutableListOf<SongPosition>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            playbackManager.pausePositionFlow.collect { paused += it }
        }
        preferences.playbackPosition = 65_000
        playback.progressMs = 42_000

        playbackManager.onPlaybackStateChanged(PlaybackState.Playing)
        playbackManager.onPlaybackStateChanged(PlaybackState.Loading)

        preferences.playbackPosition shouldBe 65_000
        paused shouldBe emptyList()
    }
}
