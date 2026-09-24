package com.simplecityapps.playback.spec

import com.simplecityapps.playback.dsp.equalizer.Equalizer
import com.simplecityapps.playback.dsp.replaygain.ReplayGainMode
import com.simplecityapps.playback.spec.PlaybackHarness.Companion.BYTES_PER_MS
import com.simplecityapps.playback.spec.PlaybackHarness.Companion.TONE_2S_MS
import com.simplecityapps.playback.spec.PlaybackHarness.Companion.song
import com.simplecityapps.shuttle.model.Song
import io.kotest.matchers.ints.shouldBeBetween
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The spec's rules about the audio that comes out (docs/testing/playback-behaviour-spec.md), on the real
 * ExoPlayer and audio sink with the production EQ and ReplayGain processors, observed as the PCM written to the
 * AudioTrack. The test files are a sine at half scale, so an untouched peak is about 16384.
 *
 * RS-17 (24-bit PCM) is device-only: Robolectric's AudioTrack has no minimum buffer size for 24-bit PCM.
 */
@RunWith(RobolectricTestRunner::class)
class AudioOutputSpecTest {
    private var harness: PlaybackHarness? = null

    @After
    fun tearDown() {
        harness?.release()
        Equalizer.Presets.custom.bands.forEach { it.gain = 0.0 }
    }

    private fun harness(
        replayGainMode: ReplayGainMode = ReplayGainMode.Off,
        equalizerEnabled: Boolean = false
    ) = PlaybackHarness(replayGainMode, equalizerEnabled).also { harness = it }

    @Test
    fun `RS-15 ReplayGain applies from a song's first sample`() {
        // The sink ramps the volume up over the first milliseconds after playback starts, so the gained output is
        // compared sample for sample with the same song played without gain.
        val ungained = playToEnd(harness(), song(1)).int16Samples()
        harness?.release()
        val gained = playToEnd(harness(replayGainMode = ReplayGainMode.Track), song(1, replayGainTrack = HALF_GAIN_DB)).int16Samples()

        gained.size shouldBe ungained.size
        gained.indices.forEach { i -> (gained[i] - ungained[i] / 2).shouldBeBetween(-2, 2) }
        gained.peak(0, gained.size).shouldBeBetween(QUARTER_SCALE - TOLERANCE, QUARTER_SCALE + TOLERANCE)
    }

    @Test
    fun `RS-16 each song in a gapless run plays at its own ReplayGain, switching at the song boundary`() {
        val harness = harness(replayGainMode = ReplayGainMode.Track)
        val ended = harness.record(harness.playbackOperations.trackEndedFlow)

        harness.run { harness.playbackOperations.addToQueue(listOf(song(1, replayGainTrack = 0.0), song(2, replayGainTrack = HALF_GAIN_DB))) }
        harness.runUntil { ended.size == 2 }

        val samples = harness.audioOutput().int16Samples()
        val boundary = TONE_2S_MS * BYTES_PER_MS / 2
        samples.size shouldBe 2 * boundary
        samples.peak(0, boundary).shouldBeBetween(HALF_SCALE - TOLERANCE, HALF_SCALE + TOLERANCE)
        samples.peak(boundary - FIRST_10_MS, boundary).shouldBeBetween(HALF_SCALE - TOLERANCE, HALF_SCALE + TOLERANCE)
        samples.peak(boundary, boundary + FIRST_10_MS).shouldBeBetween(QUARTER_SCALE - TOLERANCE, QUARTER_SCALE + TOLERANCE)
        samples.peak(boundary, samples.size).shouldBeBetween(QUARTER_SCALE - TOLERANCE, QUARTER_SCALE + TOLERANCE)
    }

    @Test
    fun `RS-18 a boosted EQ preset leaves headroom rather than clipping`() {
        val harness = harness(equalizerEnabled = true)
        Equalizer.Presets.custom.bands.forEach { it.gain = 12.0 }
        harness.equalizer.preset = Equalizer.Presets.custom
        val ended = harness.record(harness.playbackOperations.trackEndedFlow)

        harness.run { harness.playbackOperations.addToQueue(listOf(song(1))) }
        harness.runUntil { ended.isNotEmpty() }

        val samples = harness.audioOutput().int16Samples()
        samples.peak(0, samples.size) shouldBeLessThan Short.MAX_VALUE.toInt()
    }

    private fun playToEnd(
        harness: PlaybackHarness,
        song: Song
    ): ByteArray {
        val ended = harness.record(harness.playbackOperations.trackEndedFlow)
        harness.run { harness.playbackOperations.addToQueue(listOf(song)) }
        harness.runUntil { ended.isNotEmpty() }
        return harness.audioOutput()
    }

    private fun ByteArray.int16Samples(): ShortArray {
        val buffer = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        return ShortArray(buffer.remaining()).also(buffer::get)
    }

    private fun ShortArray.peak(
        from: Int,
        to: Int
    ): Int = (from until to).maxOf { abs(this[it].toInt()) }

    private companion object {
        /** Halves the amplitude. */
        const val HALF_GAIN_DB = -6.0206
        const val HALF_SCALE = 16_384
        const val QUARTER_SCALE = 8_192
        const val TOLERANCE = 400

        /** Samples in 10 ms of the 16 kHz test files. */
        const val FIRST_10_MS = 160
    }
}
