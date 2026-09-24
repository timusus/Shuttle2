package com.simplecityapps.playback.dsp

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import com.simplecityapps.playback.dsp.equalizer.Equalizer
import com.simplecityapps.playback.exoplayer.EqualizerAudioProcessor
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random
import org.junit.After
import org.junit.Test

private const val CHANNEL_COUNT = 2

/** -1 dBFS, the level real masters are pushed to and the worst case the equalizer has to survive. */
private const val PEAK_AMPLITUDE = 29204

/**
 * Pins the headroom behaviour of [EqualizerAudioProcessor].
 *
 * The ten peaking filters overlap, so a preset with every band at +12 dB produces a cascade that
 * peaks around +17.5 dB, not +12 dB. The processor measures that peak and pre-attenuates by its
 * inverse, so no steady tone leaves the equalizer louder than it arrived.
 *
 * Note that peak-gain normalisation bounds the *steady state* gain, not the instantaneous peak of a
 * broadband or transient signal - the guaranteed bound for those is the L1 norm of the cascade's
 * impulse response, which is another ~9.6 dB down and would gut the level of every boosted preset.
 * White noise therefore overshoots the input peak by a fraction of a dB; the bounds below pin that
 * overshoot without pretending it is zero.
 */
class EqualizerAudioProcessorTest {
    @After
    fun resetCustomPreset() {
        Equalizer.Presets.custom.bands.forEach { band -> band.gain = 0.0 }
    }

    @Test
    fun `fully boosted preset keeps white noise close to the input peak at 44_1 kHz`() {
        assertNoiseStaysWithinInputPeak(sampleRate = 44100)
    }

    @Test
    fun `fully boosted preset keeps white noise close to the input peak at 48 kHz`() {
        assertNoiseStaysWithinInputPeak(sampleRate = 48000)
    }

    @Test
    fun `fully boosted preset keeps a sine sweep at the input peak at 44_1 kHz`() {
        assertSweepStaysWithinInputPeak(sampleRate = 44100)
    }

    @Test
    fun `fully boosted preset keeps a sine sweep at the input peak at 48 kHz`() {
        assertSweepStaysWithinInputPeak(sampleRate = 48000)
    }

    @Test
    fun `fully boosted preset attenuates by the cascade peak, not the band peak`() {
        val equalizer = equalizerWithAllBandsAt(12.0, sampleRate = 44100)

        val attenuationDb = 20 * log10(equalizer.attenuation.toDouble())

        // The largest single band gain is +12 dB; the cascade peaks near +17.5 dB.
        attenuationDb shouldBeLessThan -16.0
        attenuationDb shouldBeGreaterThanOrEqualTo -19.0
    }

    @Test
    fun `flat preset passes audio through unchanged`() {
        val equalizer =
            EqualizerAudioProcessor(enabled = true).apply {
                preset = Equalizer.Presets.flat
            }.configured(44100)

        val input = whiteNoise(44100)
        val output = equalizer.process(input)

        equalizer.attenuation shouldBe 1f
        output.toList() shouldBe input.toList()
    }

    @Test
    fun `cut only preset is not attenuated further`() {
        val equalizer = equalizerWithAllBandsAt(-12.0, sampleRate = 44100)

        val input = whiteNoise(44100)
        val output = equalizer.process(input)

        // A cut-only cascade can never exceed unity gain, so nothing is taken off the top of it.
        equalizer.attenuation shouldBe 1f
        // It is still filtering, though - this is not the bypass path.
        output.indices.count { index -> output[index] != input[index] } shouldBeGreaterThan input.size / 2
        output.peak() shouldBeLessThan input.peak()
    }

    @Test
    fun `disabled equalizer passes audio through unchanged`() {
        val equalizer = equalizerWithAllBandsAt(12.0, sampleRate = 44100).also { processor -> processor.enabled = false }

        val input = whiteNoise(44100)
        val output = equalizer.process(input)

        output.toList() shouldBe input.toList()
    }

    @Test
    fun `switching from a boosted preset back to flat removes the attenuation`() {
        val equalizer = equalizerWithAllBandsAt(12.0, sampleRate = 44100)
        equalizer.attenuation shouldBeLessThan 1f

        equalizer.preset = Equalizer.Presets.flat
        equalizer.flush(AudioProcessor.StreamMetadata.DEFAULT)

        val input = whiteNoise(44100)
        val output = equalizer.process(input)

        equalizer.attenuation shouldBe 1f
        output.toList() shouldBe input.toList()
    }

    @Test
    fun `a preset set during playback applies from the next buffer`() {
        val equalizer = equalizerWithAllBandsAt(12.0, sampleRate = 44100)

        equalizer.preset = Equalizer.Presets.flat
        val input = whiteNoise(44100)
        val output = equalizer.process(input)

        equalizer.attenuation shouldBe 1f
        output.toList() shouldBe input.toList()
    }

    @Test
    fun `custom band edits apply only once the preset is set again`() {
        val equalizer = equalizerWithAllBandsAt(0.0, sampleRate = 44100)
        val input = whiteNoise(44100)

        // The presenter edits the custom preset's bands in place, then sets the preset.
        Equalizer.Presets.custom.bands.forEach { band -> band.gain = 12.0 }
        equalizer.process(input).toList() shouldBe input.toList()

        equalizer.preset = Equalizer.Presets.custom
        equalizer.process(input).toList() shouldNotBe input.toList()
        equalizer.attenuation shouldBeLessThan 1f
    }

    @Test
    fun `enabling during playback applies from the next buffer`() {
        val equalizer = equalizerWithAllBandsAt(12.0, sampleRate = 44100).also { processor -> processor.enabled = false }
        val input = whiteNoise(44100)
        equalizer.process(input).toList() shouldBe input.toList()

        equalizer.enabled = true

        equalizer.process(input).toList() shouldNotBe input.toList()
    }

    @Test
    fun `a bypassed equalizer passes audio through unchanged until the bypass ends`() {
        val equalizer = equalizerWithAllBandsAt(12.0, sampleRate = 44100)
        val input = whiteNoise(44100)

        equalizer.bypassed = true
        equalizer.process(input).toList() shouldBe input.toList()

        equalizer.bypassed = false
        equalizer.process(input).toList() shouldNotBe input.toList()
    }

    @Test
    fun `24 bit is accepted and 32 bit is rejected`() {
        // The only encodings onConfigure accepts are 16 and 24 bit PCM; float and 8/32 bit throw.
        EqualizerAudioProcessor(enabled = true).configure(AudioProcessor.AudioFormat(44100, CHANNEL_COUNT, C.ENCODING_PCM_24BIT))

        try {
            EqualizerAudioProcessor(enabled = true).configure(AudioProcessor.AudioFormat(44100, CHANNEL_COUNT, C.ENCODING_PCM_FLOAT))
            throw AssertionError("Expected UnhandledAudioFormatException for ENCODING_PCM_FLOAT")
        } catch (exception: AudioProcessor.UnhandledAudioFormatException) {
            // Expected
        }
    }

    private fun assertNoiseStaysWithinInputPeak(sampleRate: Int) {
        val equalizer = equalizerWithAllBandsAt(12.0, sampleRate)

        val input = whiteNoise(sampleRate)
        val output = equalizer.process(input)

        // Full band noise overshoots the steady state bound by 1 - 1.5 dB (see the class comment),
        // so a handful of samples out of a second of audio can graze full scale. Anything beyond a
        // stray sample would mean the attenuation had stopped tracking the cascade.
        output.peak() shouldBeLessThan input.peak() * 1.25
        output.saturatedSampleCount() shouldBeLessThan input.size / 1000
        // ...and the attenuation isn't so heavy that boosted presets become inaudible.
        output.peak() shouldBeGreaterThanOrEqualTo input.peak() * 0.5
    }

    private fun assertSweepStaysWithinInputPeak(sampleRate: Int) {
        val equalizer = equalizerWithAllBandsAt(12.0, sampleRate)

        val input = logSineSweep(sampleRate)
        val output = equalizer.process(input)

        // A swept sine is exactly the signal the peak gain bound is derived for, so the equalizer
        // gives back no more than it was handed.
        output.peak() shouldBeLessThanOrEqualTo input.peak()
        output.saturatedSampleCount() shouldBe 0
        // ...and the attenuation isn't so heavy that boosted presets become inaudible.
        output.peak() shouldBeGreaterThanOrEqualTo input.peak() * 0.5
    }

    private fun equalizerWithAllBandsAt(
        gain: Double,
        sampleRate: Int
    ): EqualizerAudioProcessor {
        Equalizer.Presets.custom.bands.forEach { band -> band.gain = gain }
        return EqualizerAudioProcessor(enabled = true).apply {
            preset = Equalizer.Presets.custom
        }.configured(sampleRate)
    }
}

private fun <T : AudioProcessor> T.configured(sampleRate: Int): T = apply {
    configure(AudioProcessor.AudioFormat(sampleRate, CHANNEL_COUNT, C.ENCODING_PCM_16BIT))
    flush()
}

/** Runs [input] through the processor in a single pass and returns the interleaved output. */
private fun AudioProcessor.process(input: ShortArray): ShortArray {
    val inputBuffer = ByteBuffer.allocateDirect(input.size * Short.SIZE_BYTES).order(ByteOrder.nativeOrder())
    inputBuffer.asShortBuffer().put(input)
    queueInput(inputBuffer)

    val outputBuffer = getOutput()
    val samples = ShortArray(outputBuffer.remaining() / Short.SIZE_BYTES)
    outputBuffer.asShortBuffer().get(samples)
    return samples
}

/** One second of stereo white noise peaking at [PEAK_AMPLITUDE]. */
private fun whiteNoise(sampleRate: Int): ShortArray {
    val random = Random(seed = 20260923)
    return ShortArray(sampleRate * CHANNEL_COUNT) { ((random.nextDouble() * 2.0 - 1.0) * PEAK_AMPLITUDE).toInt().toShort() }
}

/** One second of a stereo 20 Hz - 20 kHz logarithmic sine sweep peaking at [PEAK_AMPLITUDE]. */
private fun logSineSweep(sampleRate: Int): ShortArray {
    val samples = ShortArray(sampleRate * CHANNEL_COUNT)
    val startFrequency = 20.0
    val endFrequency = 20000.0
    val rate = ln(endFrequency / startFrequency)
    for (frame in 0 until sampleRate) {
        val time = frame.toDouble() / sampleRate
        val phase = 2.0 * PI * startFrequency * (endFrequency / startFrequency).pow(time).minus(1.0) / rate
        val value = (PEAK_AMPLITUDE * sin(phase)).toInt().toShort()
        for (channel in 0 until CHANNEL_COUNT) {
            samples[(frame * CHANNEL_COUNT) + channel] = value
        }
    }
    return samples
}

private fun ShortArray.peak(): Double = maxOf { sample -> abs(sample.toInt()) }.toDouble()

private fun ShortArray.saturatedSampleCount(): Int = count { sample -> sample == Short.MAX_VALUE || sample == Short.MIN_VALUE }
