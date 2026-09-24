package com.simplecityapps.playback

import com.simplecityapps.playback.fakes.FakePlayback
import com.simplecityapps.playback.fakes.FakeSharedPreferences
import com.simplecityapps.playback.fakes.testPlaybackManager
import com.simplecityapps.playback.fakes.testSong
import com.simplecityapps.playback.persistence.PlaybackPreferenceManager
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.squareup.moshi.Moshi
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * A track playing to its end is published on [PlaybackManager.trackEndedFlow] before the queue moves on, and the saved position is reset to 0 only if the queue is
 * about to move on to another item.
 */
class PlaybackManagerTrackEndedTest {
    private val queueManager = QueueManager(GeneralPreferenceManager(FakeSharedPreferences()))
    private val preferences = PlaybackPreferenceManager(FakeSharedPreferences(), Moshi.Builder().build())
    private val playback = FakePlayback("A")
    private val playbackManager =
        testPlaybackManager(
            exoplayerPlayback = playback,
            queueManager = queueManager,
            playbackPreferenceManager = preferences
        )

    @Test
    fun `a track end emits the song that ended, before the queue moves on`() = runTest {
        runBlocking { queueManager.setQueue((1L..3L).map { testSong(it) }) }
        val currentWhenEmitted = mutableListOf<Long?>()
        val ended = mutableListOf<Song>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            playbackManager.trackEndedFlow.collect { song ->
                ended += song
                currentWhenEmitted += queueManager.getCurrentItem()?.song?.id
            }
        }

        playbackManager.onTrackEnded(trackWentToNext = true)

        ended.map { it.id } shouldBe listOf(1L)
        currentWhenEmitted shouldBe listOf(1L)
        queueManager.getCurrentItem()?.song?.id shouldBe 2L
    }

    @Test
    fun `a new collector is not replayed an earlier track end`() = runTest {
        runBlocking { queueManager.setQueue((1L..3L).map { testSong(it) }) }
        playbackManager.onTrackEnded(trackWentToNext = true)

        val ended = mutableListOf<Song>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            playbackManager.trackEndedFlow.collect { song -> ended += song }
        }

        ended shouldBe emptyList()
    }

    @Test
    fun `a track end saves 0 only when the queue has a next item to move to`() {
        runBlocking { queueManager.setQueue((1L..2L).map { testSong(it) }) }
        preferences.playbackPosition = 175_000

        playbackManager.onTrackEnded(trackWentToNext = true)

        // Moved on to the second song, so it starts from 0.
        preferences.playbackPosition shouldBe 0
        queueManager.getCurrentItem()?.song?.id shouldBe 2L

        preferences.playbackPosition = 175_000
        playbackManager.onTrackEnded(trackWentToNext = false)

        // The last song with repeat off: the queue stays on it, and so does its saved position.
        preferences.playbackPosition shouldBe 175_000
        queueManager.getCurrentItem()?.song?.id shouldBe 2L
        queueManager.getRepeatMode() shouldBe QueueManager.RepeatMode.Off
    }

    @Test
    fun `a non-gapless track end publishes the new track's progress, not the old track's`() {
        runBlocking { queueManager.setQueue(listOf(testSong(1, duration = 180_000), testSong(2, duration = 200_000))) }
        // The playback still reports the old track's near-end position; the load it's about to start
        // hasn't completed yet.
        playback.progressMs = 179_000
        playback.durationMs = 180_000

        playbackManager.onTrackEnded(trackWentToNext = false)

        playbackManager.progressFlow.value shouldBe PlaybackProgress(0, 200_000)
    }
}
