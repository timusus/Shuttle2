package com.simplecityapps.playback

import com.simplecityapps.playback.audiofocus.AudioFocusHelper
import com.simplecityapps.playback.fakes.FakeSharedPreferences
import com.simplecityapps.playback.persistence.PlaybackPreferenceManager
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.playback.queue.QueueWatcher
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.squareup.moshi.Moshi
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test

/**
 * A playback switch loads the new playback, then seeks to the saved position, rebinds the audio
 * effect session and resumes if the old playback was playing. A switch superseded by a later one
 * (e.g. a fast local -> Cast -> local toggle) can still complete its load; its callback must not
 * act on whichever playback is active by then.
 */
class PlaybackManagerSwitchTest {
    private val events = mutableListOf<String>()
    private val playbackPreferences = FakeSharedPreferences()
    private val queueWatcher = QueueWatcher()
    private val queueManager = QueueManager(queueWatcher, GeneralPreferenceManager(FakeSharedPreferences()))
    private val playbackPreferenceManager = PlaybackPreferenceManager(playbackPreferences, Moshi.Builder().build())
    private val audioEffectSessionManager = AudioEffectSessionManager(openSession = {}, closeSession = {})

    private val playbackA = FakePlayback("A", sessionId = 1, events)
    private val playbackB = FakePlayback("B", sessionId = 2, events)
    private val playbackC = FakePlayback("C", sessionId = 3, events)

    private lateinit var playbackManager: PlaybackManager

    @Before
    fun setUp() {
        playbackManager =
            PlaybackManager(
                queueManager = queueManager,
                playbackWatcher = PlaybackWatcher(),
                audioFocusHelper = FakeAudioFocusHelper(),
                playbackPreferenceManager = playbackPreferenceManager,
                audioEffectSessionManager = audioEffectSessionManager,
                appCoroutineScope = CoroutineScope(Dispatchers.Unconfined),
                exoplayerPlayback = playbackA,
                queueWatcher = queueWatcher,
                audioManager = null
            )
        runBlocking { queueManager.setQueue(listOf(createSong())) }
        playbackPreferenceManager.playbackPosition = SAVED_POSITION
        playbackA.state = PlaybackState.Playing
        events.clear()
    }

    @Test
    fun `completed switch seeks to the saved position and resumes`() {
        playbackManager.switchToPlayback(playbackB)
        events.clear()

        playbackB.completeLoad()

        events shouldBe listOf("B seek $SAVED_POSITION", "B play")
        audioEffectSessionManager.sessionId shouldBe playbackB.sessionId
    }

    @Test
    fun `superseded switch completing its load does not seek or play the active playback`() {
        playbackManager.switchToPlayback(playbackB)
        playbackManager.switchToPlayback(playbackC)
        events.clear()

        playbackB.completeLoad()

        events.shouldBeEmpty()
        audioEffectSessionManager.sessionId shouldBe playbackC.sessionId
    }

    @Test
    fun `latest switch still completes after a superseded one`() {
        playbackManager.switchToPlayback(playbackB)
        playbackManager.switchToPlayback(playbackC)
        playbackB.completeLoad()
        events.clear()

        playbackC.completeLoad()

        events shouldBe listOf("C seek $SAVED_POSITION")
        audioEffectSessionManager.sessionId shouldBe playbackC.sessionId
    }

    @Test
    fun `superseded switch to A does not act once A is switched away from and back`() {
        // A -> B -> A: the stale B load finishing must not seek or play A.
        playbackManager.switchToPlayback(playbackB)
        playbackManager.switchToPlayback(playbackA)
        events.clear()

        playbackB.completeLoad()

        events.shouldBeEmpty()
    }

    @Test
    fun `superseded switch to the playback that is active again does not act`() {
        // B -> A -> B: the first switch's playback is the active one again, so identity alone
        // can't tell its load callback is stale.
        playbackManager.switchToPlayback(playbackB)
        playbackManager.switchToPlayback(playbackA)
        playbackManager.switchToPlayback(playbackB)
        events.clear()

        playbackB.completeLoad()

        events.shouldBeEmpty()

        playbackB.completeLoad()

        events shouldBe listOf("B seek $SAVED_POSITION")
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

    private class FakePlayback(
        private val name: String,
        val sessionId: Int,
        private val events: MutableList<String>
    ) : Playback {
        override var callback: Playback.Callback? = null
        override var isReleased: Boolean = false
        var state: PlaybackState = PlaybackState.Paused

        private val pendingLoads = mutableListOf<(Result<Any?>) -> Unit>()

        /** Completes the oldest load requested of this playback that hasn't completed yet. */
        fun completeLoad() {
            isReleased = false
            pendingLoads.removeAt(0)(Result.success(null))
        }

        override suspend fun load(
            current: Song,
            next: Song?,
            seekPosition: Int,
            completion: (Result<Any?>) -> Unit
        ) {
            pendingLoads += completion
        }

        override suspend fun loadNext(song: Song?) {}

        override fun play() {
            events += "$name play"
            state = PlaybackState.Playing
        }

        override fun pause() {
            state = PlaybackState.Paused
        }

        override fun release() {
            isReleased = true
        }

        override fun playBackState(): PlaybackState = state

        override fun seek(position: Int) {
            events += "$name seek $position"
        }

        override fun getProgress(): Int? = null

        override fun getDuration(): Int? = null

        override fun setVolume(volume: Float) {}

        override fun getResumeWhenSwitched(oldPlayback: Playback): Boolean = true

        override fun setRepeatMode(repeatMode: QueueManager.RepeatMode) {}

        override fun getAudioSessionId(): Int = sessionId

        override fun setPlaybackSpeed(multiplier: Float) {}

        override fun getPlaybackSpeed(): Float = 1f
    }

    private class FakeAudioFocusHelper : AudioFocusHelper {
        override fun requestAudioFocus(): Boolean = true

        override fun abandonAudioFocus() {}

        override var listener: AudioFocusHelper.Listener? = null
        override var enabled: Boolean = true
        override var resumeOnFocusGain: Boolean = false
    }

    private companion object {
        const val SAVED_POSITION = 5_000
    }
}
