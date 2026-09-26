package com.simplecityapps.shuttle.ui.screens.settings.equalizer

import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import org.junit.Test

/** Covers #312: the chart's headroom attenuation and output-sample-rate handling. */
class ComputeFrequencyResponseTest {
    private val computeFrequencyResponse = ComputeFrequencyResponse()

    @Test
    fun `a flat preset produces a response that never exceeds 0 dB`() {
        val bands = listOf(EqualizerBandState(1000, 0f))

        val points = computeFrequencyResponse(bands, outputSampleRateHz = 44_100)

        points.forEach { point -> point.gainDb shouldBe (0f plusOrMinus 0.1f) }
    }

    @Test
    fun `a boosted band is pulled back below its raw gain by the headroom attenuation`() {
        val bands = listOf(EqualizerBandState(1000, 12f))

        val points = computeFrequencyResponse(bands, outputSampleRateHz = 44_100)

        val peakGainDb = points.maxOf { it.gainDb }
        // Without the #312 headroom attenuation the peak would sit at ~12 dB.
        peakGainDb shouldBeLessThan 1f
    }

    @Test
    fun `falls back to a named sample rate when the processor hasn't configured an output format`() {
        val bands = listOf(EqualizerBandState(1000, 0f))

        val withNullSampleRate = computeFrequencyResponse(bands, outputSampleRateHz = null)
        val withFallbackSampleRate = computeFrequencyResponse(bands, outputSampleRateHz = FALLBACK_OUTPUT_SAMPLE_RATE_HZ)

        withNullSampleRate shouldBe withFallbackSampleRate
    }

    @Test
    fun `the plotted range extends to Nyquist of the given output sample rate`() {
        val bands = listOf(EqualizerBandState(1000, 0f))

        val points = computeFrequencyResponse(bands, outputSampleRateHz = 16_000)

        points.last().frequencyHz shouldBe (8_000f plusOrMinus 1f)
    }
}
