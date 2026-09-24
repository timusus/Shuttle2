package com.simplecityapps.shuttle.appinitializers

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakeSongRepository
import com.simplecityapps.playback.AudioEffectSessionManager
import com.simplecityapps.playback.Playback
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.ProgressTicker
import com.simplecityapps.playback.audiofocus.AudioFocusHelper
import com.simplecityapps.playback.persistence.PlaybackPreferenceManager
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.testing.MainDispatcherRule
import com.squareup.moshi.Moshi
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * [PlaybackInitializer] saving the position a real [PlaybackManager] publishes, through a restore, play and
 * playback switch, rather than anchors set by hand.
 */
@RunWith(RobolectricTestRunner::class)
class PlaybackInitializerPlaybackManagerTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val application: Application = RuntimeEnvironment.getApplication()
    private val sharedPreferences = application.getSharedPreferences("playback_initializer_playback_manager_test", Context.MODE_PRIVATE)
    private val preferences = PlaybackPreferenceManager(sharedPreferences, Moshi.Builder().build())
    private val queueManager =
        QueueManager(GeneralPreferenceManager(application.getSharedPreferences("general_test", Context.MODE_PRIVATE)))
    private val appCoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val localPlayback = FakePlayback()
    private val castPlayback = FakePlayback()

    private val playbackManager =
        PlaybackManager(
            queueManager = queueManager,
            audioFocusHelper = mockk<AudioFocusHelper>(relaxed = true) { every { requestAudioFocus() } returns true },
            playbackPreferenceManager = preferences,
            audioEffectSessionManager = AudioEffectSessionManager(openSession = {}, closeSession = {}),
            appCoroutineScope = appCoroutineScope,
            // A dispatcher nothing advances, so progress never ticks.
            progressTicker = ProgressTicker(CoroutineScope(StandardTestDispatcher())),
            exoplayerPlayback = localPlayback,
            audioManager = null
        )

    private val initializer =
        PlaybackInitializer(
            context = application,
            songRepository = FakeSongRepository(),
            playbackManager = playbackManager,
            queueManager = queueManager,
            playbackPreferenceManager = preferences,
            castSessionManager = { mockk(relaxed = true) },
            mediaSessionManager = { mockk(relaxed = true) },
            noiseManager = { mockk(relaxed = true) },
            appCoroutineScope = appCoroutineScope
        )

    /** Every position written to the preferences, in order. */
    private val savedPositions = mutableListOf<Int?>()

    private val savedPositionListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "playback_position") savedPositions += preferences.playbackPosition
        }

    @After
    fun tearDown() {
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(savedPositionListener)
        appCoroutineScope.cancel()
    }

    @Test
    fun `restoring, playing and switching playback never saves a zero position`() {
        runBlocking { queueManager.setQueue(listOf(createSong(id = 1, duration = 200_000))) }
        preferences.playbackPosition = 30_000
        sharedPreferences.registerOnSharedPreferenceChangeListener(savedPositionListener)

        // Restore: with no saved queue position the queue is left as it is, and loaded at the saved position.
        initializer.init(application)
        localPlayback.completeLoad()
        playbackManager.play()
        localPlayback.progressMs = 42_000

        // Switch to a playback that reports 0 until it has loaded at the switch's position.
        playbackManager.switchToPlayback(castPlayback)
        castPlayback.completeLoad()

        savedPositions shouldNotContain 0
        preferences.playbackPosition shouldBe 42_000
        castPlayback.progressMs shouldBe 42_000
    }

    /** Loads at the requested position once completed, and reports play and pause as a real playback does. */
    private class FakePlayback : Playback {
        override var callback: Playback.Callback? = null
        override var isReleased: Boolean = false
        var progressMs: Int = 0
        private var state: PlaybackState = PlaybackState.Paused
        private val pendingLoads = mutableListOf<Pair<Int, (Result<Any?>) -> Unit>>()

        fun completeLoad() {
            val (seekPosition, completion) = pendingLoads.removeAt(0)
            isReleased = false
            progressMs = seekPosition
            completion(Result.success(null))
        }

        override suspend fun load(
            current: Song,
            next: Song?,
            seekPosition: Int,
            completion: (Result<Any?>) -> Unit
        ) {
            pendingLoads += seekPosition to completion
        }

        override suspend fun loadNext(song: Song?) {}

        override fun play() = report(PlaybackState.Playing)

        override fun pause() = report(PlaybackState.Paused)

        private fun report(state: PlaybackState) {
            this.state = state
            callback?.onPlaybackStateChanged(state)
        }

        override fun release() {
            isReleased = true
        }

        override fun playBackState(): PlaybackState = state

        override fun seek(position: Int) {
            progressMs = position
        }

        override fun getProgress(): Int = progressMs

        override fun getDuration(): Int = 200_000

        override fun setVolume(volume: Float) {}

        override fun getResumeWhenSwitched(oldPlayback: Playback): Boolean = true

        override fun setRepeatMode(repeatMode: QueueManager.RepeatMode) {}

        override fun setPlaybackSpeed(multiplier: Float) {}

        override fun getPlaybackSpeed(): Float = 1f
    }
}
