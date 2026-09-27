package com.simplecityapps.shuttle.shared.playback

import com.simplecityapps.playback.dsp.equalizer.Equalizer
import com.simplecityapps.playback.dsp.equalizer.EqualizerCascade
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class IosEqualizerTest {
    private val player = FakeIosAudioPlayer().apply { sampleRate = 44_100 }

    private fun equalizer(
        enabled: Boolean = true,
        preset: Equalizer.Presets.Preset = Equalizer.Presets.bassBoost,
        preampGainDb: Float = 0f
    ) = IosEqualizer(player, enabled, preset, preampGainDb)

    @Test
    fun itHandsTheEngineTheSavedEqualizerWhenBuilt() {
        equalizer(enabled = true, preset = Equalizer.Presets.bassBoost, preampGainDb = 3f)

        val applied = player.equalizers.single()
        val expected = EqualizerCascade(Equalizer.Presets.bassBoost.bands, 44_100)
        applied.enabled shouldBe true
        applied.coefficients.toList() shouldBe expected.coefficients.toList()
        applied.preampDb shouldBe (expected.headroomDb + 3f plusOrMinus 1e-6f)
    }

    @Test
    fun theFiltersAreDesignedAtTheEnginesRate() {
        val equalizer = equalizer()

        equalizer.outputSampleRateHz.value shouldBe 44_100
        player.equalizers.last().coefficients.toList() shouldBe
            EqualizerCascade(Equalizer.Presets.bassBoost.bands, 44_100).coefficients.toList()
    }

    @Test
    fun aBoostedPresetLeavesHeadroomAndAFlatOneDoesNot() {
        equalizer(preset = Equalizer.Presets.flat)
        player.equalizers.last().preampDb shouldBe 0f

        equalizer(preset = Equalizer.Presets.bassBoost)
        (player.equalizers.last().preampDb < 0f) shouldBe true
    }

    @Test
    fun eachChangeReachesTheEngine() {
        val equalizer = equalizer(enabled = false, preset = Equalizer.Presets.flat)

        equalizer.enabled = true
        player.equalizers.last().enabled shouldBe true

        equalizer.preampGainDb = -4f
        player.equalizers.last().preampDb shouldBe -4f

        equalizer.preset = Equalizer.Presets.vocalBoost
        player.equalizers.last().coefficients.toList() shouldBe
            EqualizerCascade(Equalizer.Presets.vocalBoost.bands, 44_100).coefficients.toList()
    }

    @Test
    fun theBandGainsAreCapturedWhenThePresetIsSet() {
        val custom = Equalizer.Presets.custom
        val saved = custom.bands.map { it.gain }
        try {
            custom.bands.forEach { it.gain = 0.0 }
            val equalizer = equalizer(preset = custom)
            val flat = player.equalizers.last().coefficients.toList()

            custom.bands.first().gain = 6.0
            equalizer.enabled = true
            player.equalizers.last().coefficients.toList() shouldBe flat

            equalizer.preset = custom
            (player.equalizers.last().coefficients.toList() == flat) shouldBe false
        } finally {
            custom.bands.forEachIndexed { index, band -> band.gain = saved[index] }
        }
    }
}
