package com.simplecityapps.playback.audiofocus

import android.media.AudioManager
import com.simplecityapps.playback.PlaybackState
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Test

/**
 * [AudioFocusHelperBase] ducks on a transient loss it may duck through, pauses on any other loss, and
 * resumes on regaining focus only after a transient loss that interrupted playback. It also
 * interprets an Api26 focus request result: a delayed grant resumes playback once focus is later
 * gained.
 */
class AudioFocusHelperBaseTest {
    private val playbackState = MutableStateFlow<PlaybackState>(PlaybackState.Paused)
    private val events = mutableListOf<String>()

    private val helper = TestAudioFocusHelper()

    @Before
    fun setUp() {
        helper.listener =
            object : AudioFocusHelper.Listener {
                override val playbackStateFlow = playbackState

                override fun restoreVolumeAndPlay() {
                    events += "restoreVolumeAndPlay"
                    playbackState.value = PlaybackState.Playing
                }

                override fun pauseForFocusLoss() {
                    events += "pause"
                    playbackState.value = PlaybackState.Paused
                }

                override fun duck() {
                    events += "duck"
                }
            }
    }

    @Test
    fun `a transient loss that can duck ducks without pausing`() {
        playbackState.value = PlaybackState.Playing

        helper.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)

        events shouldBe listOf("duck")
    }

    @Test
    fun `regaining focus after a transient loss while playing resumes playback`() {
        playbackState.value = PlaybackState.Playing

        helper.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        helper.resumeOnFocusGain shouldBe true
        helper.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)

        events shouldBe listOf("pause", "restoreVolumeAndPlay")
        helper.resumeOnFocusGain shouldBe false
    }

    @Test
    fun `a transient loss while loading counts as playing`() {
        playbackState.value = PlaybackState.Loading

        helper.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        helper.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)

        events shouldBe listOf("pause", "restoreVolumeAndPlay")
    }

    @Test
    fun `regaining focus after a transient loss while paused does not resume`() {
        helper.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        helper.resumeOnFocusGain shouldBe false
        helper.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)

        events shouldBe listOf("pause")
    }

    @Test
    fun `regaining focus after a full loss does not resume`() {
        playbackState.value = PlaybackState.Playing

        helper.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS)
        helper.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)

        events shouldBe listOf("pause")
    }

    @Test
    fun `a full loss after a transient one cancels the pending resume`() {
        playbackState.value = PlaybackState.Playing

        helper.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        helper.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS)
        helper.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)

        events shouldBe listOf("pause", "pause")
    }

    @Test
    fun `a granted focus request result reports granted and isn't delayed`() {
        val granted = helper.onFocusRequestResult(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)

        granted shouldBe true
        helper.playbackDelayed shouldBe false
    }

    @Test
    fun `a delayed focus request result reports not granted, and a later gain resumes playback`() {
        playbackState.value = PlaybackState.Playing

        val granted = helper.onFocusRequestResult(AudioManager.AUDIOFOCUS_REQUEST_DELAYED)

        granted shouldBe false
        helper.playbackDelayed shouldBe true

        helper.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)

        events shouldBe listOf("restoreVolumeAndPlay")
    }

    @Test
    fun `a failed focus request result reports not granted and isn't delayed`() {
        val granted = helper.onFocusRequestResult(AudioManager.AUDIOFOCUS_REQUEST_FAILED)

        granted shouldBe false
        helper.playbackDelayed shouldBe false
    }

    @Test
    fun `a disabled helper ignores focus changes`() {
        playbackState.value = PlaybackState.Playing
        helper.enabled = false

        helper.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)
        helper.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        helper.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)

        events shouldBe emptyList()
    }

    private class TestAudioFocusHelper : AudioFocusHelperBase(lazyOf(null)) {
        override fun requestAudioFocus(): Boolean = true

        override fun abandonAudioFocus() {}
    }
}
