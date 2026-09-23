package com.simplecityapps.playback

import com.simplecityapps.playback.fakes.FakeAudioFocusHelper
import com.simplecityapps.playback.fakes.FakePlayback
import com.simplecityapps.playback.fakes.FakeSharedPreferences
import com.simplecityapps.playback.fakes.testPlaybackManager
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.playback.queue.QueueWatcher
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test

/**
 * PlaybackManager registers itself as the AudioFocusHelper.Listener, so a transient focus loss
 * ducks the volume, a full loss pauses, and regaining focus restores the volume and resumes.
 */
class PlaybackManagerAudioFocusTest {
    private val events = mutableListOf<String>()
    private val queueWatcher = QueueWatcher()
    private val queueManager = QueueManager(queueWatcher, GeneralPreferenceManager(FakeSharedPreferences()))
    private val playback = FakePlayback("A", events = events)
    private val audioFocusHelper = FakeAudioFocusHelper()

    private lateinit var playbackManager: PlaybackManager

    @Before
    fun setUp() {
        playbackManager = testPlaybackManager(
            exoplayerPlayback = playback,
            queueWatcher = queueWatcher,
            queueManager = queueManager,
            audioFocusHelper = audioFocusHelper
        )
        runBlocking { queueManager.setQueue(listOf(createSong())) }
        events.clear()
    }

    @Test
    fun `PlaybackManager registers itself as the audio focus listener`() {
        audioFocusHelper.listener shouldNotBe null
    }

    @Test
    fun `a transient focus loss ducks the volume without pausing`() {
        audioFocusHelper.listener!!.duck()

        events shouldBe listOf("A setVolume 0.2")
    }

    @Test
    fun `a full focus loss pauses playback`() {
        audioFocusHelper.listener!!.pause()

        events shouldBe listOf("A pause")
    }

    @Test
    fun `regaining focus restores full volume and resumes playback`() {
        audioFocusHelper.listener!!.restoreVolumeAndPlay()

        events shouldBe listOf("A setVolume 1.0", "A play")
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
