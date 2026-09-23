package com.simplecityapps.playback

import com.simplecityapps.playback.fakes.FakePlayback
import com.simplecityapps.playback.fakes.FakeSharedPreferences
import com.simplecityapps.playback.fakes.testPlaybackManager
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.playback.queue.QueueWatcher
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
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
    private val progressEvents = mutableListOf<Triple<Int, Int, Boolean>>()
    private val playbackWatcher =
        PlaybackWatcher().apply {
            addCallback(
                object : PlaybackWatcherCallback {
                    override fun onProgressChanged(
                        position: Int,
                        duration: Int,
                        fromUser: Boolean
                    ) {
                        progressEvents += Triple(position, duration, fromUser)
                    }
                }
            )
        }
    private val queueWatcher = QueueWatcher()
    private val queueManager = QueueManager(queueWatcher, GeneralPreferenceManager(FakeSharedPreferences()))
    private val playback =
        FakePlayback("A").apply {
            progressMs = 1_000
            durationMs = 5_000
        }

    private fun TestScope.createPlaybackManager() = testPlaybackManager(
        exoplayerPlayback = playback,
        queueWatcher = queueWatcher,
        queueManager = queueManager,
        playbackWatcher = playbackWatcher,
        progressTicker = ProgressTicker(backgroundScope)
    )

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
        advanceBy(200)

        progressEvents shouldBe List(3) { Triple(1_000, 5_000, false) }
    }

    @Test
    fun `loading publishes progress`() = runTest {
        createPlaybackManager()

        enter(PlaybackState.Loading)
        runCurrent()

        progressEvents shouldBe listOf(Triple(1_000, 5_000, false))
    }

    @Test
    fun `loading then playing keeps a single tick per interval`() = runTest {
        createPlaybackManager()

        enter(PlaybackState.Loading)
        runCurrent()
        enter(PlaybackState.Playing)
        runCurrent()
        advanceBy(100)

        progressEvents.size shouldBe 2
    }

    @Test
    fun `pausing stops progress updates`() = runTest {
        createPlaybackManager()

        enter(PlaybackState.Playing)
        runCurrent()
        enter(PlaybackState.Paused)
        progressEvents.clear()
        advanceBy(1_000)

        progressEvents.shouldBeEmpty()
    }

    @Test
    fun `no progress is published before playback starts`() = runTest {
        createPlaybackManager()

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

        progressEvents shouldBe listOf(Triple(1_000, 7_000, false))
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
