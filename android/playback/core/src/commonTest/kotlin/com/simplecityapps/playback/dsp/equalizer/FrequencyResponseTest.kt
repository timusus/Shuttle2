package com.simplecityapps.playback.dsp.equalizer

import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import kotlin.math.log10
import kotlin.test.AfterTest
import kotlin.test.Test

private const val SAMPLE_RATE = 44100

/** The sample rates iOS actually renders at - #602 pins the coefficient maths against both. */
private val COMMON_SAMPLE_RATES = listOf(44_100, 48_000)

/**
 * Pins the analytical magnitude-response math that backs the EQ frequency-response chart, replacing
 * an FFT-of-an-impulse approach with the exact biquad magnitude response ([BandProcessor.magnitudeAt]).
 */
class FrequencyResponseTest {
    @AfterTest
    fun resetCustomPreset() {
        Equalizer.Presets.custom.bands.forEach { band -> band.gain = 0.0 }
    }

    @Test
    fun `flat preset gives 0 dB across the spectrum`() {
        val bandProcessors = bandProcessorsFor(Equalizer.Presets.flat)

        listOf(20.0, 100.0, 1_000.0, 4_000.0, 16_000.0, 20_000.0).forEach { frequency ->
            frequencyResponseDb(bandProcessors, preAmpGainDb = 0.0, frequency, SAMPLE_RATE) shouldBe (0.0 plusOrMinus 0.01)
        }
    }

    @Test
    fun `boosted band peaks at its centre frequency`() {
        Equalizer.Presets.custom.bands.first { band -> band.centerFrequency == 1000 }.gain = 12.0
        val bandProcessors = bandProcessorsFor(Equalizer.Presets.custom)

        val atCentre = frequencyResponseDb(bandProcessors, preAmpGainDb = 0.0, frequencyHz = 1000.0, SAMPLE_RATE)
        val farBelow = frequencyResponseDb(bandProcessors, preAmpGainDb = 0.0, frequencyHz = 32.0, SAMPLE_RATE)
        val farAbove = frequencyResponseDb(bandProcessors, preAmpGainDb = 0.0, frequencyHz = 16000.0, SAMPLE_RATE)

        // The peaking filter is designed to hit exactly its band gain at the centre frequency.
        atCentre shouldBe (12.0 plusOrMinus 0.1)
        // Far from the centre frequency the response returns to the 0 dB reference.
        farBelow shouldBe (0.0 plusOrMinus 0.5)
        farAbove shouldBe (0.0 plusOrMinus 0.5)
        atCentre shouldBeGreaterThan farBelow
        atCentre shouldBeGreaterThan farAbove
    }

    @Test
    fun `cut band dips at its centre frequency`() {
        Equalizer.Presets.custom.bands.first { band -> band.centerFrequency == 1000 }.gain = -12.0
        val bandProcessors = bandProcessorsFor(Equalizer.Presets.custom)

        val atCentre = frequencyResponseDb(bandProcessors, preAmpGainDb = 0.0, frequencyHz = 1000.0, SAMPLE_RATE)
        val farAbove = frequencyResponseDb(bandProcessors, preAmpGainDb = 0.0, frequencyHz = 16000.0, SAMPLE_RATE)

        atCentre shouldBe (-12.0 plusOrMinus 0.1)
        atCentre shouldBeLessThan farAbove
    }

    @Test
    fun `pre-amp gain shifts a flat response by a constant number of dB`() {
        val bandProcessors = bandProcessorsFor(Equalizer.Presets.flat)

        listOf(20.0, 1_000.0, 20_000.0).forEach { frequency ->
            frequencyResponseDb(bandProcessors, preAmpGainDb = 6.0, frequency, SAMPLE_RATE) shouldBe (6.0 plusOrMinus 0.01)
        }
    }

    @Test
    fun `cascadeAttenuation is unity for a flat or cut-only cascade`() {
        cascadeAttenuation(bandProcessorsFor(Equalizer.Presets.flat), SAMPLE_RATE) shouldBe (1.0f plusOrMinus 0.001f)

        Equalizer.Presets.custom.bands.first { band -> band.centerFrequency == 1000 }.gain = -6.0
        cascadeAttenuation(bandProcessorsFor(Equalizer.Presets.custom), SAMPLE_RATE) shouldBe (1.0f plusOrMinus 0.001f)
    }

    @Test
    fun `cascadeAttenuation pulls a boosted cascade back to unity gain at its peak`() {
        Equalizer.Presets.custom.bands.first { band -> band.centerFrequency == 1000 }.gain = 12.0
        val bandProcessors = bandProcessorsFor(Equalizer.Presets.custom)

        val attenuation = cascadeAttenuation(bandProcessors, SAMPLE_RATE)
        val attenuationDb = 20.0 * log10(attenuation.toDouble())

        // 12 dB of boost needs roughly that much attenuation to bring the cascade back to unity gain.
        attenuationDb shouldBeLessThan -10.0
        attenuationDb shouldBeGreaterThanOrEqualTo -12.5

        val peakAfterAttenuation = frequencyResponseDb(bandProcessors, preAmpGainDb = attenuationDb, frequencyHz = 1000.0, SAMPLE_RATE)
        peakAfterAttenuation shouldBe (0.0 plusOrMinus 0.5)
    }

    @Test
    fun `cascadeAttenuation is unity when there is no band above Nyquist's analysis floor`() {
        cascadeAttenuation(emptyList(), SAMPLE_RATE) shouldBe 1.0f
        cascadeAttenuation(bandProcessorsFor(Equalizer.Presets.flat), sampleRateHz = 8) shouldBe 1.0f
    }

    /** #602: the coefficient maths is shared with iOS, which renders at 44.1 kHz or 48 kHz depending on the device output. */
    @Test
    fun `a boosted band peaks at its centre frequency at both 44_1kHz and 48kHz`() {
        Equalizer.Presets.custom.bands.first { band -> band.centerFrequency == 1000 }.gain = 12.0

        COMMON_SAMPLE_RATES.forEach { sampleRate ->
            val bandProcessors = bandProcessorsFor(Equalizer.Presets.custom, sampleRate)

            val atCentre = frequencyResponseDb(bandProcessors, preAmpGainDb = 0.0, frequencyHz = 1000.0, sampleRate)
            val farBelow = frequencyResponseDb(bandProcessors, preAmpGainDb = 0.0, frequencyHz = 32.0, sampleRate)

            atCentre shouldBe (12.0 plusOrMinus 0.1)
            farBelow shouldBe (0.0 plusOrMinus 0.5)
        }
    }

    /** #602: the headroom attenuation the coefficients need to stay at unity gain doesn't depend on the sample rate. */
    @Test
    fun `cascadeAttenuation pulls a boosted cascade back to unity gain at both 44_1kHz and 48kHz`() {
        Equalizer.Presets.custom.bands.first { band -> band.centerFrequency == 1000 }.gain = 12.0

        COMMON_SAMPLE_RATES.forEach { sampleRate ->
            val bandProcessors = bandProcessorsFor(Equalizer.Presets.custom, sampleRate)

            val attenuation = cascadeAttenuation(bandProcessors, sampleRate)
            val attenuationDb = 20.0 * log10(attenuation.toDouble())

            attenuationDb shouldBeLessThan -10.0
            attenuationDb shouldBeGreaterThanOrEqualTo -12.5
        }
    }

    private fun bandProcessorsFor(preset: Equalizer.Presets.Preset, sampleRate: Int = SAMPLE_RATE): List<BandProcessor> = preset.bands.map { band ->
        // toNyquistBand() re-derives bandwidthGain from the current gain, matching
        // EqualizerAudioProcessor.updateBandProcessors() - a NyquistBand's own bandwidthGain field
        // is only valid at the gain it was constructed with.
        BandProcessor(band.toNyquistBand(), sampleRate = sampleRate, channelCount = 1, referenceGain = 0.0)
    }
}
