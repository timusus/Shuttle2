package com.simplecityapps.shuttle.shared.playback

import com.simplecityapps.playback.dsp.equalizer.Equalizer
import com.simplecityapps.playback.dsp.equalizer.EqualizerCascade
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class IosEqualizerTest {
    private val player = FakeIosAudioPlayer()

    private fun equalizer(
        enabled: Boolean = true,
        preset: Equalizer.Presets.Preset = Equalizer.Presets.bassBoost,
        preampGainDb: Float = 0f
    ) = IosEqualizer(player, enabled, preset, preampGainDb)

    @Test
    fun itHandsTheEngineTheSavedEqualizerWhenBuilt() {
        equalizer(enabled = true, preset = Equalizer.Presets.bassBoost, preampGainDb = 3f)

        val applied = player.equalizers.single()
        val filters = applied.filtersAt(44_100)
        val expected = EqualizerCascade(Equalizer.Presets.bassBoost.bands, 44_100)
        applied.enabled shouldBe true
        filters.coefficients.toList() shouldBe expected.coefficients.toList()
        filters.preampDb shouldBe (expected.headroomDb + 3f plusOrMinus 1e-6f)
    }

    @Test
    fun theFiltersAreDesignedAtWhicheverRateTheEngineAsksFor() {
        equalizer()

        for (rate in listOf(44_100, 48_000, 96_000)) {
            player.equalizers.last().filtersAt(rate).coefficients.toList() shouldBe
                EqualizerCascade(Equalizer.Presets.bassBoost.bands, rate).coefficients.toList()
        }
    }

    @Test
    fun theChartFallsBackToItsDefaultRateUntilAudioIsHeard() {
        equalizer().outputSampleRateHz.value shouldBe null
    }

    @Test
    fun theChartFollowsTheRateThePlayerReports() {
        val equalizer = equalizer()

        player.reportOutputSampleRate(96_000)
        equalizer.outputSampleRateHz.value shouldBe 96_000

        player.reportOutputSampleRate(44_100)
        equalizer.outputSampleRateHz.value shouldBe 44_100

        player.reportOutputSampleRate(null)
        equalizer.outputSampleRateHz.value shouldBe null
    }

    @Test
    fun aBoostedPresetLeavesHeadroomAndAFlatOneDoesNot() {
        equalizer(preset = Equalizer.Presets.flat)
        player.equalizers.last().filtersAt(48_000).preampDb shouldBe 0f

        equalizer(preset = Equalizer.Presets.bassBoost)
        (player.equalizers.last().filtersAt(48_000).preampDb < 0f) shouldBe true
    }

    @Test
    fun eachChangeReachesTheEngine() {
        val equalizer = equalizer(enabled = false, preset = Equalizer.Presets.flat)

        equalizer.enabled = true
        player.equalizers.last().enabled shouldBe true

        equalizer.preampGainDb = -4f
        player.equalizers.last().filtersAt(48_000).preampDb shouldBe -4f

        equalizer.preset = Equalizer.Presets.vocalBoost
        player.equalizers.last().filtersAt(48_000).coefficients.toList() shouldBe
            EqualizerCascade(Equalizer.Presets.vocalBoost.bands, 48_000).coefficients.toList()
    }

    @Test
    fun theBandGainsAreCapturedWhenThePresetIsSet() {
        val custom = Equalizer.Presets.custom
        val saved = custom.bands.map { it.gain }
        try {
            custom.bands.forEach { it.gain = 0.0 }
            val equalizer = equalizer(preset = custom)
            val handedOver = player.equalizers.last()
            val flat = handedOver.filtersAt(48_000).coefficients.toList()

            custom.bands.first().gain = 6.0
            handedOver.filtersAt(48_000).coefficients.toList() shouldBe flat
            equalizer.enabled = true
            player.equalizers.last().filtersAt(48_000).coefficients.toList() shouldBe flat

            equalizer.preset = custom
            (player.equalizers.last().filtersAt(48_000).coefficients.toList() == flat) shouldBe false
        } finally {
            custom.bands.forEachIndexed { index, band -> band.gain = saved[index] }
        }
    }
}
