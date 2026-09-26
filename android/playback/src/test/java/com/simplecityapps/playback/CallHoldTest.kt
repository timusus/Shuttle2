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

    private val callHold = CallHold(FakeListenedPlayer(), CallMonitor(audioManager), isRemote = { remote })

    private var plays = 0

    private val play: () -> Unit = { plays++ }

    private fun setAudioMode(mode: Int) {
        audioManager.mode = mode
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `a play with no call goes ahead`() {
        callHold.holds(play) shouldBe false
    }

    @Test
    fun `a play during a call waits for the call to end`() {
        setAudioMode(AudioManager.MODE_IN_CALL)

        callHold.holds(play) shouldBe true
        plays shouldBe 0

        setAudioMode(AudioManager.MODE_NORMAL)
        plays shouldBe 1
    }

    @Test
    fun `a play on a Cast receiver goes ahead during a call`() {
        setAudioMode(AudioManager.MODE_IN_COMMUNICATION)
        remote = true

        callHold.holds(play) shouldBe false
    }

    @Test
    fun `a queue change drops the held play`() {
        setAudioMode(AudioManager.MODE_RINGTONE)
        callHold.holds(play)

        callHold.onTimelineChanged(FakePlaylistTimeline(emptyList()), Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED)
        setAudioMode(AudioManager.MODE_NORMAL)

        plays shouldBe 0
    }

    @Test
    fun `cancel drops the held play`() {
        setAudioMode(AudioManager.MODE_IN_CALL)
        callHold.holds(play)

        callHold.cancel()
        setAudioMode(AudioManager.MODE_NORMAL)

        plays shouldBe 0
    }
}
