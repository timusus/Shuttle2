package com.simplecityapps.playback

import com.simplecityapps.playback.fakes.FakePlayback
import com.simplecityapps.playback.fakes.FakeSharedPreferences
import com.simplecityapps.playback.fakes.testPlaybackManager
import com.simplecityapps.playback.persistence.PlaybackPreferenceManager
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.playback.queue.QueueWatcher
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.squareup.moshi.Moshi
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test

/**
 * play() reloads a released playback before resuming, up to 2 attempts, resetting the seek
 * position to zero if it's within 200ms of the track's end. An already-loaded playback seeks to
 * zero instead of reloading when resumed near the end.
 */
class PlaybackManagerPlayTest {
    private val events = mutableListOf<String>()
    private val queueWatcher = QueueWatcher()
    private val queueManager = QueueManager(queueWatcher, GeneralPreferenceManager(FakeSharedPreferences()))
    private val playbackPreferenceManager = PlaybackPreferenceManager(FakeSharedPreferences(), Moshi.Builder().build())

    private lateinit var playbackManager: PlaybackManager

    private fun createPlaybackManager(playback: FakePlayback) {
        playbackManager = testPlaybackManager(
            exoplayerPlayback = playback,
            queueWatcher = queueWatcher,
            queueManager = queueManager,
            playbackPreferenceManager = playbackPreferenceManager
        )
    }

    @Before
    fun setUp() {
        runBlocking { queueManager.setQueue(listOf(createSong())) }
    }

    @Test
    fun `play reloads a released playback and resumes once the reload succeeds`() {
        val playback = FakePlayback("A", events = events)
        createPlaybackManager(playback)
        playback.isReleased = true
        events.clear()

        playbackManager.play()
        playback.completeLoad()

        events shouldBe listOf("A load Song seek 0", "A play")
    }

    @Test
    fun `play gives up after 2 reload attempts while the playback remains released`() {
        // Pins current behaviour; see #250
        val playback = FakePlayback("A", events = events, resetReleasedOnLoad = false)
        createPlaybackManager(playback)
        playback.isReleased = true
        events.clear()

        playbackManager.play()
        playback.completeLoad()
        playback.completeLoad()

        events.count { it.contains("load") } shouldBe 2
        events.none { it == "A play" } shouldBe true
    }

    @Test
    fun `play resets the seek position to zero when reloading within 200ms of the track's end`() {
        val playback = FakePlayback("A", events = events)
        createPlaybackManager(playback)
        playback.isReleased = true
        playback.durationMs = 5_000
        playbackPreferenceManager.playbackPosition = 4_850
        events.clear()

        playbackManager.play()

        events shouldBe listOf("A load Song seek 0")
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

    private fun createSong() = Song(
        id = 1,
        name = "Song",
        albumArtist = null,
        artists = emptyList(),
        album = null,
        track = null,
        disc = null,
        duration = 180_000,
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
