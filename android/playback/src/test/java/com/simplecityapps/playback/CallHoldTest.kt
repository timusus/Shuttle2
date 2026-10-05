package com.simplecityapps.playback

import android.content.Context
import android.media.AudioManager
import android.os.Looper
import androidx.media3.common.Player
import com.simplecityapps.playback.fakes.FakeListenedPlayer
import com.simplecityapps.playback.fakes.FakePlaylistTimeline
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

// Robolectric: real AudioManager via RuntimeEnvironment.getApplication().
@RunWith(RobolectricTestRunner::class)
class CallHoldTest {
    private val audioManager = RuntimeEnvironment.getApplication().getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var remote = false

    private var serviceStarts = 0

    private val foregroundHold = ForegroundHold { serviceStarts++ }

    private val callHold = CallHold(FakeListenedPlayer(), CallMonitor(audioManager), isRemote = { remote }, foregroundHold)

    private var plays = 0

    /** Whether the service was still held in the foreground as each play started. */
    private val heldAtPlay = mutableListOf<Boolean>()

    private val play: () -> Unit = {
        plays++
        heldAtPlay += foregroundHold.isHeld
    }

    private fun setAudioMode(mode: Int) {
        audioManager.mode = mode
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `a play with no call goes ahead`() {
        callHold.holds(play) shouldBe false
        serviceStarts shouldBe 0
        foregroundHold.isHeld shouldBe false
    }

    @Test
    fun `a play during a call waits for the call to end, with the service held in the foreground until it has started`() {
        setAudioMode(AudioManager.MODE_IN_CALL)

        callHold.holds(play) shouldBe true
        plays shouldBe 0
        serviceStarts shouldBe 1
        foregroundHold.isHeld shouldBe true

        setAudioMode(AudioManager.MODE_NORMAL)
        plays shouldBe 1
        heldAtPlay shouldBe listOf(true)
        foregroundHold.isHeld shouldBe false
    }

    @Test
    fun `a held play held again by another call keeps the service held`() {
        setAudioMode(AudioManager.MODE_IN_CALL)
        var replays = 0
        callHold.holds {
            // Another call begins as the first ends: the play is held again.
            audioManager.mode = AudioManager.MODE_RINGTONE
            callHold.holds { replays++ } shouldBe true
        }

        setAudioMode(AudioManager.MODE_NORMAL)
        foregroundHold.isHeld shouldBe true
        replays shouldBe 0

        setAudioMode(AudioManager.MODE_NORMAL)
        replays shouldBe 1
        foregroundHold.isHeld shouldBe false
    }

    @Test
    fun `a play on a Cast receiver goes ahead during a call`() {
        setAudioMode(AudioManager.MODE_IN_COMMUNICATION)
        remote = true

        callHold.holds(play) shouldBe false
        serviceStarts shouldBe 0
    }

    @Test
    fun `a queue change drops the held play and releases the service`() {
        setAudioMode(AudioManager.MODE_RINGTONE)
        callHold.holds(play)

        callHold.onTimelineChanged(FakePlaylistTimeline(emptyList()), Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED)
        foregroundHold.isHeld shouldBe false
        setAudioMode(AudioManager.MODE_NORMAL)

        plays shouldBe 0
    }

    @Test
    fun `cancel drops the held play and releases the service`() {
        setAudioMode(AudioManager.MODE_IN_CALL)
        callHold.holds(play)

        callHold.cancel()
        foregroundHold.isHeld shouldBe false
        setAudioMode(AudioManager.MODE_NORMAL)

        plays shouldBe 0
    }
}
