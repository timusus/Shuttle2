package com.simplecityapps.playback

import com.simplecityapps.playback.fakes.FakePlayback
import com.simplecityapps.playback.fakes.FakeSharedPreferences
import com.simplecityapps.playback.fakes.testPlaybackManager
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * While the playback is Loading or Playing, PlaybackManager publishes the playback's progress every
 * 100ms; in any other state it stops.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackManagerProgressTest {
    /** Each progress published after the manager is created. Positions change per step, so each tick is a new value. */
    private val progressEvents = mutableListOf<PlaybackProgress>()
    private val queueManager = QueueManager(GeneralPreferenceManager(FakeSharedPreferences()))
    private val playback =
        FakePlayback("A").apply {
            progressMs = 1_000
            durationMs = 5_000
        }

    private fun TestScope.createPlaybackManager() {
        val playbackManager =
            testPlaybackManager(
                exoplayerPlayback = playback,
                queueManager = queueManager,
                progressTicker = ProgressTicker(backgroundScope)
            )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            playbackManager.progressFlow.filterNotNull().collect { progressEvents += it }
        }
    }

    private fun TestScope.advanceBy(millis: Long) {
        advanceTimeBy(millis)
        runCurrent()
    }

    private fun enter(state: PlaybackState) {
        playback.callback!!.onPlaybackStateChanged(state)
    }

    @Test
    fun `playing publishes progress every 100ms`() = runTest {
        createPlaybackManager()

        enter(PlaybackState.Playing)
        runCurrent()
        playback.progressMs = 1_100
        advanceBy(100)
        playback.progressMs = 1_200
        advanceBy(100)

        progressEvents shouldBe listOf(1_000, 1_100, 1_200).map { PlaybackProgress(it, 5_000) }
    }

    @Test
    fun `loading publishes progress`() = runTest {
        createPlaybackManager()

        enter(PlaybackState.Loading)
        runCurrent()

        progressEvents shouldBe listOf(PlaybackProgress(1_000, 5_000))
    }

    @Test
    fun `loading then playing keeps a single tick per interval`() = runTest {
        createPlaybackManager()

        enter(PlaybackState.Loading)
        runCurrent()
        playback.progressMs = 1_100
        enter(PlaybackState.Playing)
        runCurrent()
        playback.progressMs = 1_200
        advanceBy(100)

        // Entering Playing doesn't restart the ticker, so nothing is published at 1_100.
        progressEvents shouldBe listOf(PlaybackProgress(1_000, 5_000), PlaybackProgress(1_200, 5_000))
    }

    @Test
    fun `pausing stops progress updates`() = runTest {
        createPlaybackManager()

        enter(PlaybackState.Playing)
        runCurrent()
        enter(PlaybackState.Paused)
        progressEvents.clear()
        playback.progressMs = 2_000
        advanceBy(1_000)

        progressEvents.shouldBeEmpty()
    }

    @Test
    fun `no progress is published before playback starts`() = runTest {
        createPlaybackManager()

        playback.progressMs = 2_000
        advanceBy(1_000)

        progressEvents.shouldBeEmpty()
    }

    @Test
    fun `falls back to the current song's duration when the playback has none`() = runTest {
        playback.durationMs = null
        queueManager.setQueue(listOf(createSong(duration = 7_000)))
        createPlaybackManager()

        enter(PlaybackState.Playing)
        runCurrent()

        progressEvents shouldBe listOf(PlaybackProgress(1_000, 7_000))
    }

    private fun createSong(duration: Int) = Song(
        id = 1,
        name = "Song",
        albumArtist = null,
        artists = emptyList(),
        album = null,
        track = null,
        disc = null,
        duration = duration,
        date = null,
        genres = emptyList(),
        path = "/music/song.mp3",
        size = 0,
        mimeType = "audio/mpeg",
        lastModified = null,
        lastPlayed = null,
        lastCompleted = null,
        playCount = 0,
        playbackPosition = 0,
        blacklisted = false,
        mediaProvider = MediaProviderType.Shuttle,
        lyrics = null,
        grouping = null,
        bitRate = null,
        bitDepth = null,
        sampleRate = null,
        channelCount = null
    )
}
