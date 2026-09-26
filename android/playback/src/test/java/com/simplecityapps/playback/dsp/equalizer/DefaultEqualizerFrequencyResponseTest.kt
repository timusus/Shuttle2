package com.simplecityapps.playback.dsp.equalizer

import com.simplecityapps.playback.equalizer.EqualizerBandGain
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import org.junit.Test

private const val MIN_FREQUENCY_HZ = 20f
private const val MAX_FREQUENCY_HZ = 20_500f

/** Covers #312 and #236: the chart's headroom attenuation, preamp and output-sample-rate handling. */
class DefaultEqualizerFrequencyResponseTest {
    private val computeFrequencyResponse = DefaultEqualizerFrequencyResponse()

    @Test
    fun `a flat preset produces a response that never exceeds 0 dB`() {
        val bands = listOf(EqualizerBandGain(1000, 0f))

        val points = computeFrequencyResponse(bands, preampGainDb = 0f, outputSampleRateHz = 44_100, MIN_FREQUENCY_HZ, MAX_FREQUENCY_HZ, pointCount = 240).points

        points.forEach { point -> point.gainDb shouldBe (0f plusOrMinus 0.1f) }
    }

    @Test
    fun `a boosted band is pulled back below its raw gain by the headroom attenuation`() {
        val bands = listOf(EqualizerBandGain(1000, 12f))

        val points = computeFrequencyResponse(bands, preampGainDb = 0f, outputSampleRateHz = 44_100, MIN_FREQUENCY_HZ, MAX_FREQUENCY_HZ, pointCount = 240).points

        val peakGainDb = points.maxOf { it.gainDb }
        // Without the #312 headroom attenuation the peak would sit at ~12 dB.
        peakGainDb shouldBeLessThan 1f
    }

    @Test
    fun `falls back to a named sample rate when the processor hasn't configured an output format`() {
        val bands = listOf(EqualizerBandGain(1000, 0f))

        val withNullSampleRate = computeFrequencyResponse(bands, preampGainDb = 0f, outputSampleRateHz = null, MIN_FREQUENCY_HZ, MAX_FREQUENCY_HZ, pointCount = 240).points
        val withFallbackSampleRate = computeFrequencyResponse(bands, preampGainDb = 0f, outputSampleRateHz = FALLBACK_OUTPUT_SAMPLE_RATE_HZ, MIN_FREQUENCY_HZ, MAX_FREQUENCY_HZ, pointCount = 240).points

        withNullSampleRate shouldBe withFallbackSampleRate
    }

    @Test
    fun `the plotted range extends to Nyquist of the given output sample rate`() {
        val bands = listOf(EqualizerBandGain(1000, 0f))

        val points = computeFrequencyResponse(bands, preampGainDb = 0f, outputSampleRateHz = 16_000, MIN_FREQUENCY_HZ, MAX_FREQUENCY_HZ, pointCount = 240).points

        points.last().frequencyHz shouldBe (8_000f plusOrMinus 1f)
    }

    @Test
    fun `the preamp shifts the whole curve by its gain`() {
        val bands = listOf(EqualizerBandGain(1000, 12f))

        val withoutPreamp = computeFrequencyResponse(bands, preampGainDb = 0f, outputSampleRateHz = 44_100, MIN_FREQUENCY_HZ, MAX_FREQUENCY_HZ, pointCount = 240)
        val withPreamp = computeFrequencyResponse(bands, preampGainDb = 6f, outputSampleRateHz = 44_100, MIN_FREQUENCY_HZ, MAX_FREQUENCY_HZ, pointCount = 240)

        withPreamp.points.zip(withoutPreamp.points).forEach { (boosted, plain) -> boosted.gainDb shouldBe ((plain.gainDb + 6f) plusOrMinus 0.01f) }
        withPreamp.headroomAttenuationDb shouldBe withoutPreamp.headroomAttenuationDb
    }

    @Test
    fun `reports the headroom attenuation a boost needs, and none for a flat preset`() {
        val flat = computeFrequencyResponse(listOf(EqualizerBandGain(1000, 0f)), preampGainDb = 0f, outputSampleRateHz = 44_100, MIN_FREQUENCY_HZ, MAX_FREQUENCY_HZ, pointCount = 240)
        val boosted = computeFrequencyResponse(listOf(EqualizerBandGain(1000, 12f)), preampGainDb = 0f, outputSampleRateHz = 44_100, MIN_FREQUENCY_HZ, MAX_FREQUENCY_HZ, pointCount = 240)

        flat.headroomAttenuationDb shouldBe 0f
        boosted.headroomAttenuationDb shouldBe (-12f plusOrMinus 0.2f)
    }
}
