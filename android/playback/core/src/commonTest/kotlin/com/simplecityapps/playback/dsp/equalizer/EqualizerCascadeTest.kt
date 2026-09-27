package com.simplecityapps.playback.dsp.equalizer

import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test

class EqualizerCascadeTest {
    /**
     * A 1 kHz band at +6 dB, designed at 48 kHz: the fixture the S2Playback package's
     * `testAKotlinDesignedBandBoostsItsFrequencyAtTheEngineRate` runs through the engine. Change both together.
     */
    @Test
    fun aOneKilohertzBoostAt48kHzHasThePinnedCoefficients() {
        val cascade = EqualizerCascade(listOf(EqualizerBand(1_000, 6.0)), sampleRateHz = 48_000)

        val expected = doubleArrayOf(1.024858815651008, -1.9333627896640524, 0.92518688531219, -1.9333627896640524, 0.9500457009631977)
        cascade.coefficients.size shouldBe 5
        cascade.coefficients.forEachIndexed { index, coefficient -> coefficient shouldBe (expected[index] plusOrMinus 1e-12) }
    }

    @Test
    fun theHeadroomCancelsTheCascadesPeak() {
        EqualizerCascade(listOf(EqualizerBand(1_000, 6.0)), sampleRateHz = 48_000).headroomDb shouldBe (-6f plusOrMinus 0.05f)
        EqualizerCascade(Equalizer.Presets.flat.bands, sampleRateHz = 48_000).headroomDb shouldBe 0f
        EqualizerCascade(Equalizer.Presets.bassReducer.bands, sampleRateHz = 48_000).headroomDb shouldBe 0f
    }

    @Test
    fun aFlatBandIsTheIdentityFilter() {
        EqualizerCascade(listOf(EqualizerBand(1_000, 0.0)), sampleRateHz = 44_100).coefficients.toList() shouldBe
            listOf(1.0, 0.0, 0.0, 0.0, 0.0)
    }

    @Test
    fun theCoefficientsDependOnTheRateTheyreDesignedAt() {
        val bands = Equalizer.Presets.bassBoost.bands
        (EqualizerCascade(bands, 44_100).coefficients.toList() == EqualizerCascade(bands, 48_000).coefficients.toList()) shouldBe false
    }

    /** Running the handed-over coefficients as a biquad gives what [BandProcessor.processSample] does: same filter. */
    @Test
    fun theCoefficientsRunAsTheBandProcessorDoes() {
        val band = EqualizerBand(250, -5.0)
        val processor = BandProcessor(band.toNyquistBand(), sampleRate = 44_100, channelCount = 1, referenceGain = 0.0)
        val (b0, b1, b2, a1, a2) = processor.coefficients.toList()

        var x1 = 0.0
        var x2 = 0.0
        var y1 = 0.0
        var y2 = 0.0
        repeat(2_000) { n ->
            val x = sin(2 * PI * 300 * n / 44_100).toFloat()
            val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1
            x1 = x.toDouble()
            y2 = y1
            y1 = y
            processor.processSample(x, 0).toDouble() shouldBe (y plusOrMinus 1e-5)
        }
    }

    /** At its centre, a band's steady-state gain is its gain: the coefficients are the band's. */
    @Test
    fun aSineAtTheCentreComesOutBoostedByTheBandsGain() {
        val cascade = EqualizerCascade(listOf(EqualizerBand(1_000, 6.0)), sampleRateHz = 48_000)
        val (b0, b1, b2, a1, a2) = cascade.coefficients.toList()

        var x1 = 0.0
        var x2 = 0.0
        var y1 = 0.0
        var y2 = 0.0
        var inputEnergy = 0.0
        var outputEnergy = 0.0
        repeat(48_000) { n ->
            val x = sin(2 * PI * 1_000 * n / 48_000)
            val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1
            x1 = x
            y2 = y1
            y1 = y
            // Past the filter's settling
            if (n >= 4_800) {
                inputEnergy += x * x
                outputEnergy += y * y
            }
        }
        20 * log10(sqrt(outputEnergy / inputEnergy)) shouldBe (6.0 plusOrMinus 0.05)
    }
}
