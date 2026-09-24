package com.simplecityapps.playback.exoplayer

import android.media.AudioFormat
import androidx.media3.exoplayer.audio.AudioSink
import com.simplecityapps.playback.OutputFormat
import io.kotest.matchers.shouldBe
import org.junit.Test

/** The player reports each AudioTrack it opens, and the current one is the one asked to open a new track. */
class AudioTrackMonitorTest {
    private val monitor = AudioTrackMonitor()
    private val cd = OutputFormat(44_100, 2, AudioFormat.ENCODING_PCM_16BIT)
    private val hiRes = OutputFormat(96_000, 2, AudioFormat.ENCODING_PCM_16BIT)

    private class Owner : AudioTrackMonitor.Owner {
        var reopened = 0

        override fun reopenAudioTrack() {
            reopened++
        }
    }

    @Test
    fun `follows the format of the track last opened`() {
        val player = Owner()
        monitor.format.value shouldBe null

        monitor.onAudioTrackInitialized(player, cd)
        monitor.format.value shouldBe cd

        monitor.onAudioTrackInitialized(player, hiRes)
        monitor.format.value shouldBe hiRes
    }

    @Test
    fun `asks the player that opened the last track to open a new one`() {
        val first = Owner()
        val second = Owner()
        monitor.onAudioTrackInitialized(first, cd)
        monitor.onAudioTrackInitialized(second, cd)

        monitor.reopenAudioTrack()

        first.reopened shouldBe 0
        second.reopened shouldBe 1
    }

    @Test
    fun `a released player isn't asked, but its format stays`() {
        val player = Owner()
        monitor.onAudioTrackInitialized(player, cd)

        monitor.onReleased(player)
        monitor.reopenAudioTrack()

        player.reopened shouldBe 0
        monitor.format.value shouldBe cd
    }

    @Test
    fun `releasing an earlier player leaves the current one`() {
        val first = Owner()
        val second = Owner()
        monitor.onAudioTrackInitialized(first, cd)
        monitor.onAudioTrackInitialized(second, cd)

        monitor.onReleased(first)
        monitor.reopenAudioTrack()

        second.reopened shouldBe 1
    }

    @Test
    fun `the track's channel count comes from its channel mask`() {
        AudioSink.AudioTrackConfig(AudioFormat.ENCODING_PCM_16BIT, 96_000, AudioFormat.CHANNEL_OUT_STEREO, false, false, 0).toOutputFormat() shouldBe hiRes
        AudioSink.AudioTrackConfig(AudioFormat.ENCODING_PCM_16BIT, 48_000, AudioFormat.CHANNEL_OUT_5POINT1, false, false, 0).toOutputFormat().channelCount shouldBe 6
    }
}
