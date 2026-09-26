package com.simplecityapps.shuttle.ui.screens.settings.equalizer

import androidx.compose.ui.test.junit4.createComposeRule
import com.simplecityapps.playback.dsp.equalizer.Equalizer
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Characterisation tests for [EqualizerScreen]. */
@RunWith(RobolectricTestRunner::class)
class EqualizerScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val robot = EqualizerRobot(composeTestRule)

    private fun state(
        enabled: Boolean,
        preset: Equalizer.Presets.Preset = Equalizer.Presets.bassBoost,
        preampGainDb: Float = 0f,
        headroomAttenuationDb: Float = 0f
    ) = EqualizerUiState(
        enabled = enabled,
        selectedPreset = preset,
        bands = preset.bands.map { EqualizerBandState(it.centerFrequency, it.gain.toFloat()) },
        preampGainDb = preampGainDb,
        headroomAttenuationDb = headroomAttenuationDb
    )

    @Test
    fun `shows the switch, the preset and a slider per band`() {
        robot.setContent(state(enabled = true))

        robot.assertSwitchOn()
        robot.assertDisplayed("Bass boost")
        robot.assertChartDisplayed()
        listOf("32", "63", "125", "250", "500", "1k", "2k", "4k", "8k", "16k").forEach(robot::assertDisplayed)
    }

    @Test
    fun `the bands are disabled while the equalizer is off`() {
        robot.setContent(state(enabled = false))

        robot.assertSwitchOff()
        robot.assertBandNotEnabled("1k")
    }

    @Test
    fun `tapping the switch turns it on`() {
        robot.setContent(state(enabled = false))

        robot.tapText("Use equalizer")

        robot.enabledChanges shouldBe listOf(true)
    }

    @Test
    fun `the preset dialog reports the preset picked`() {
        robot.setContent(state(enabled = true))

        robot.tapText("Preset")
        robot.tapText("Flat")

        robot.presetsSelected shouldBe listOf(Equalizer.Presets.flat)
    }

    @Test
    fun `moving a band reports its frequency and gain`() {
        robot.setContent(state(enabled = true))

        robot.setBand("1k", 3f)

        robot.bandChanges shouldBe listOf(1000 to 3f)
    }

    @Test
    fun `shows the preamp with its gain`() {
        robot.setContent(state(enabled = true, preampGainDb = 3f))

        robot.assertDisplayed("Preamp")
        robot.assertDisplayed("+3.0 dB")
    }

    @Test
    fun `moving the preamp reports its gain`() {
        robot.setContent(state(enabled = true))

        robot.setPreamp(4f)

        robot.preampChanges shouldBe listOf(4f)
    }

    @Test
    fun `the preamp is disabled while the equalizer is off`() {
        robot.setContent(state(enabled = false))

        robot.assertPreampNotEnabled()
    }

    @Test
    fun `says how far boosted bands are turned down`() {
        robot.setContent(state(enabled = true, headroomAttenuationDb = -6f))

        robot.assertTextContainingDisplayed("Turned down 6.0 dB")
    }

    @Test
    fun `says nothing about headroom when nothing is turned down`() {
        robot.setContent(state(enabled = true, preset = Equalizer.Presets.flat))

        robot.assertNoTextContaining("Turned down")
    }
}
