package com.simplecityapps.playback.dsp.equalizer

import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import org.junit.After
import org.junit.Test

private const val SAMPLE_RATE = 44100

/**
 * Pins the analytical magnitude-response math that backs the EQ frequency-response chart, replacing
 * an FFT-of-an-impulse approach with the exact biquad magnitude response ([BandProcessor.magnitudeAt]).
 */
class FrequencyResponseTest {
    @After
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

    private fun bandProcessorsFor(preset: Equalizer.Presets.Preset): List<BandProcessor> = preset.bands.map { band ->
        // toNyquistBand() re-derives bandwidthGain from the current gain, matching
        // EqualizerAudioProcessor.updateBandProcessors() - a NyquistBand's own bandwidthGain field
        // is only valid at the gain it was constructed with.
        BandProcessor(band.toNyquistBand(), sampleRate = SAMPLE_RATE, channelCount = 1, referenceGain = 0.0)
    }
}
