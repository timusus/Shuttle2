package com.simplecityapps.playback

import com.simplecityapps.playback.fakes.FakeAudioFocusHelper
import com.simplecityapps.playback.fakes.FakePlayback
import com.simplecityapps.playback.queue.QueueManager
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import org.junit.Test

/**
 * [PlaybackSwitcher] carries settings from the old playback to the new one, loads the new one at the
 * current position (a pending load's, else the old playback's), and once that load succeeds rebinds the effect session, restores the saved
 * position and resumes. Loads here complete only when a test says so, in any order, so a superseded
 * switch's completion can be delivered after a later switch without a [LoadCoordinator] filtering it.
 */
class PlaybackSwitcherTest {
    private val events = mutableListOf<String>()
    private val audioFocusHelper = FakeAudioFocusHelper()
    private val audioEffectSessionManager = AudioEffectSessionManager(openSession = {}, closeSession = {})
    private val callback =
        object : Playback.Callback {
            override fun onPlaybackStateChanged(playbackState: PlaybackState) {}

            override fun onTrackEnded(trackWentToNext: Boolean) {}
        }

    private val playbackA = FakePlayback("A", sessionId = 1, events)
    private val playbackB = FakePlayback("B", sessionId = 2, events)

    private var repeatMode = QueueManager.RepeatMode.Off
    private var savedPosition: Int? = SAVED_POSITION

    /** The position a pending non-switch load will start at, or null when none is pending. */
    private var pendingLoadPosition: Int? = null

    /** Load completions in the order the loads were requested, keyed by the position each started at. */
    private val loads = mutableListOf<Pair<Int, (Result<Boolean>) -> Unit>>()

    private var switchedTo: Playback? = null

    private val switcher: PlaybackSwitcher =
        PlaybackSwitcher(
            initialPlayback = playbackA,
            callback = callback,
            audioSessionId = AUDIO_SESSION_ID,
            audioFocusHelper = audioFocusHelper,
            audioEffectSessionManager = audioEffectSessionManager,
            repeatMode = { repeatMode },
            currentProgress = { pendingLoadPosition ?: switcher.playback.getProgress() },
            savedPosition = { savedPosition },
            onSwitched = {
                switchedTo = switcher.playback
                events += "switched"
            },
            load = { seekPosition, completion ->
                events += "load $seekPosition"
                loads += seekPosition to completion
            },
            seekTo = { position -> switcher.playback.seek(position) },
            play = { switcher.playback.play() }
        )

    private fun completeLoad(index: Int = 0) = loads.removeAt(index).second(Result.success(true))

    private fun failLoad(index: Int = 0) = loads.removeAt(index).second(Result.failure(RuntimeException("load failed")))

    @Test
    fun `initial playback is attached and the effect session opens on our audio session`() {
        repeatMode = QueueManager.RepeatMode.All

        switcher.attachInitialPlayback()

        switcher.playback shouldBe playbackA
        playbackA.callback shouldBe callback
        playbackA.requestedAudioSessionId shouldBe AUDIO_SESSION_ID
        events shouldBe listOf("A setRepeatMode All")
        audioEffectSessionManager.sessionId shouldBe AUDIO_SESSION_ID
    }

    @Test
    fun `switch carries repeat mode, audio session and playback speed over to the new playback`() {
        switcher.attachInitialPlayback()
        playbackA.setPlaybackSpeed(1.5f)
        repeatMode = QueueManager.RepeatMode.One
        events.clear()

        switcher.switchTo(playbackB)

        switcher.playback shouldBe playbackB
        playbackB.callback shouldBe callback
        playbackB.requestedAudioSessionId shouldBe AUDIO_SESSION_ID
        playbackB.getPlaybackSpeed() shouldBe 1.5f
        events shouldContainAll listOf("B setRepeatMode One", "B setPlaybackSpeed 1.5")
    }

    @Test
    fun `switch follows whether the new playback responds to audio focus`() {
        val cast = FakePlayback("Cast", events = events, respondsToAudioFocus = false)
        switcher.attachInitialPlayback()
        audioFocusHelper.enabled shouldBe true

        switcher.switchTo(cast)

        audioFocusHelper.enabled shouldBe false

        switcher.switchTo(playbackB)

        audioFocusHelper.enabled shouldBe true
    }

    @Test
    fun `switch pauses, releases and detaches the old playback before loading the new one`() {
        switcher.attachInitialPlayback()
        playbackA.state = PlaybackState.Playing
        playbackA.progressMs = 42_000
        events.clear()

        switcher.switchTo(playbackB)

        playbackA.isReleased shouldBe true
        playbackA.callback shouldBe null
        switchedTo shouldBe playbackB
        events shouldBe listOf("A pause", "B setRepeatMode Off", "B setPlaybackSpeed 1.0", "switched", "load 42000")
    }

    @Test
    fun `switch while a load is pending loads at the pending load's position, not the old playback's`() {
        switcher.attachInitialPlayback()
        playbackA.progressMs = 42_000
        pendingLoadPosition = 0

        switcher.switchTo(playbackB)

        loads.single().first shouldBe 0
    }

    @Test
    fun `switch loads at zero when the old playback has no position`() {
        switcher.attachInitialPlayback()

        switcher.switchTo(playbackB)

        loads.single().first shouldBe 0
    }

    @Test
    fun `effect session is rebound on switch and again once the new playback has loaded`() {
        switcher.attachInitialPlayback()

        switcher.switchTo(playbackB)

        audioEffectSessionManager.sessionId shouldBe playbackB.sessionId

        // The loaded player couldn't honour the id it was asked for.
        playbackB.sessionId = 7
        completeLoad()

        audioEffectSessionManager.sessionId shouldBe 7
    }

    @Test
    fun `completed switch seeks to the saved position and resumes if the old playback was playing`() {
        switcher.attachInitialPlayback()
        playbackA.state = PlaybackState.Playing
        switcher.switchTo(playbackB)
        events.clear()

        completeLoad()

        events shouldBe listOf("B seek $SAVED_POSITION", "B play")
    }

    @Test
    fun `completed switch does not seek without a saved position`() {
        savedPosition = null
        switcher.attachInitialPlayback()
        switcher.switchTo(playbackB)
        events.clear()

        completeLoad()

        events.shouldBeEmpty()
    }

    @Test
    fun `completed switch does not resume if the old playback was paused`() {
        switcher.attachInitialPlayback()
        switcher.switchTo(playbackB)
        events.clear()

        completeLoad()

        events shouldBe listOf("B seek $SAVED_POSITION")
    }

    @Test
    fun `completed switch does not resume if the new playback doesn't resume when switched`() {
        val noResume = FakePlayback("NoResume", sessionId = 4, events, resumeWhenSwitched = false)
        switcher.attachInitialPlayback()
        playbackA.state = PlaybackState.Playing
        switcher.switchTo(noResume)
        events.clear()

        completeLoad()

        events shouldBe listOf("NoResume seek $SAVED_POSITION")
    }

    @Test
    fun `failed switch load does not rebind, seek or play`() {
        switcher.attachInitialPlayback()
        playbackA.state = PlaybackState.Playing
        switcher.switchTo(playbackB)
        playbackB.sessionId = 7
        events.clear()

        failLoad()

        events.shouldBeEmpty()
        audioEffectSessionManager.sessionId shouldBe 2
    }

    @Test
    fun `superseded switch completing late does not act on the playback switched back to`() {
        // A -> B -> A: B's load completing after the switch back must not rebind, seek or play A.
        switcher.attachInitialPlayback()
        playbackA.state = PlaybackState.Playing
        switcher.switchTo(playbackB)
        playbackB.state = PlaybackState.Playing
        switcher.switchTo(playbackA)
        playbackB.sessionId = 7
        events.clear()

        completeLoad(index = 0)

        events.shouldBeEmpty()
        audioEffectSessionManager.sessionId shouldBe playbackA.sessionId

        completeLoad()

        events shouldBe listOf("A seek $SAVED_POSITION", "A play")
    }

    @Test
    fun `superseded switch to the playback that is active again does not act`() {
        // B -> A -> B: the first switch's playback is active again, so only the generation tells the
        // first completion is stale.
        switcher.attachInitialPlayback()
        switcher.switchTo(playbackB)
        switcher.switchTo(playbackA)
        switcher.switchTo(playbackB)
        events.clear()

        completeLoad(index = 0)
        completeLoad(index = 0)

        events.shouldBeEmpty()

        completeLoad()

        events shouldBe listOf("B seek $SAVED_POSITION")
    }

    private companion object {
        const val AUDIO_SESSION_ID = 99
        const val SAVED_POSITION = 5_000
    }
}
