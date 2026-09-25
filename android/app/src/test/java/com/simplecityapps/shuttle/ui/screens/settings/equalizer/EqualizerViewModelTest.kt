package com.simplecityapps.shuttle.ui.screens.settings.equalizer

import android.content.SharedPreferences
import com.simplecityapps.playback.dsp.equalizer.Equalizer
import com.simplecityapps.playback.exoplayer.EqualizerAudioProcessor
import com.simplecityapps.playback.persistence.PlaybackPreferenceManager
import com.simplecityapps.playback.settings.PlaybackSettings
import com.simplecityapps.shuttle.settings.SettingsStore
import com.simplecityapps.shuttle.settings.defaultSharedPreferences
import com.simplecityapps.testing.MainDispatcherRule
import com.squareup.moshi.Moshi
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class EqualizerViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val prefs: SharedPreferences = RuntimeEnvironment.getApplication().defaultSharedPreferences().apply { edit().clear().commit() }
    private val playbackSettings = PlaybackSettings(SettingsStore(prefs))
    private val preferenceManager = PlaybackPreferenceManager(prefs, Moshi.Builder().build())
    private val processor = EqualizerAudioProcessor(enabled = false).apply { preset = Equalizer.Presets.flat }

    private fun viewModel() = EqualizerViewModel(playbackSettings, preferenceManager, processor)

    /** A view model whose state is being collected, as the screen would. */
    private fun TestScope.collectedViewModel() = viewModel().also { viewModel -> backgroundScope.launch { viewModel.uiState.collect {} } }

    @After
    fun resetCustomPreset() {
        // The Custom preset is a process-wide object; leave it flat for the next test.
        Equalizer.Presets.custom.bands.forEach { it.gain = 0.0 }
    }

    @Test
    fun `the state starts from the playing preset`() {
        val state = viewModel().uiState.value

        state.enabled shouldBe false
        state.selectedPreset shouldBe Equalizer.Presets.flat
        state.bands.map { it.frequency } shouldBe Equalizer.Presets.flat.bands.map { it.centerFrequency }
    }

    @Test
    fun `the switch turns the processor on and stores it`() {
        viewModel().onEnabledChange(true)

        processor.enabled shouldBe true
        playbackSettings.equalizerEnabled.value shouldBe true
    }

    @Test
    fun `a preset plays and is stored`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = collectedViewModel()

        viewModel.onPresetSelect(Equalizer.Presets.bassBoost)

        processor.preset shouldBe Equalizer.Presets.bassBoost
        preferenceManager.preset shouldBe Equalizer.Presets.bassBoost
        viewModel.uiState.value.selectedPreset shouldBe Equalizer.Presets.bassBoost
        viewModel.uiState.value.bands.map { it.gainDb } shouldBe Equalizer.Presets.bassBoost.bands.map { it.gain.toFloat() }
    }

    @Test
    fun `moving a band switches to custom, keeping the other bands`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = collectedViewModel()
        viewModel.onPresetSelect(Equalizer.Presets.bassBoost)
        val before = viewModel.uiState.value.bands

        viewModel.onBandGainChange(1000, 4f)

        val state = viewModel.uiState.value
        state.selectedPreset shouldBe Equalizer.Presets.custom
        processor.preset shouldBe Equalizer.Presets.custom
        state.bands shouldBe before.map { if (it.frequency == 1000) it.copy(gainDb = 4f) else it }
        Equalizer.Presets.custom.bands.first { it.centerFrequency == 1000 }.gain shouldBe 4.0
    }

    @Test
    fun `a band can't go past the processor's limit`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = collectedViewModel()

        viewModel.onBandGainChange(32, 40f)

        viewModel.uiState.value.bands.first { it.frequency == 32 }.gainDb shouldBe processor.maxBandGain.toFloat()
    }

    @Test
    fun `custom gains are stored once the band stops moving`() {
        val viewModel = viewModel()
        viewModel.onBandGainChange(63, -3f)
        preferenceManager.customPresetBands shouldBe null

        viewModel.onBandGainChangeFinished()

        preferenceManager.preset shouldBe Equalizer.Presets.custom
        preferenceManager.customPresetBands!!.first { it.centerFrequency == 63 }.gain shouldBe -3.0
    }

    @Test
    fun `frequency labels stay short`() {
        listOf(32, 500, 1000, 16000).map(::frequencyLabel) shouldBe listOf("32", "500", "1k", "16k")
    }
}
