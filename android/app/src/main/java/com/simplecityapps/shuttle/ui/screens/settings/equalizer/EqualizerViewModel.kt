package com.simplecityapps.shuttle.ui.screens.settings.equalizer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.playback.dsp.equalizer.Equalizer
import com.simplecityapps.playback.exoplayer.EqualizerAudioProcessor
import com.simplecityapps.playback.persistence.PlaybackPreferenceManager
import com.simplecityapps.playback.settings.PlaybackSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class EqualizerBandState(
    val frequency: Int,
    val gainDb: Float
)

data class EqualizerUiState(
    val enabled: Boolean = false,
    val presets: List<Equalizer.Presets.Preset> = Equalizer.Presets.all,
    val selectedPreset: Equalizer.Presets.Preset = Equalizer.Presets.flat,
    val bands: List<EqualizerBandState> = emptyList()
)

/**
 * The equalizer: the on/off switch, the preset and the band gains. Changes go to the live
 * [EqualizerAudioProcessor] straight away, as the legacy DSP screen did; moving a band switches to the
 * Custom preset, which is stored once the drag ends.
 */
@HiltViewModel
class EqualizerViewModel @Inject constructor(
    private val playbackSettings: PlaybackSettings,
    private val playbackPreferenceManager: PlaybackPreferenceManager,
    private val equalizerAudioProcessor: EqualizerAudioProcessor
) : ViewModel() {
    private val preset = MutableStateFlow(equalizerAudioProcessor.preset)
    private val bands = MutableStateFlow(equalizerAudioProcessor.preset.bandStates())

    val uiState: StateFlow<EqualizerUiState> = combine(playbackSettings.equalizerEnabled.flow, preset, bands) { enabled, preset, bands ->
        EqualizerUiState(enabled = enabled, selectedPreset = preset, bands = bands)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = EqualizerUiState(enabled = playbackSettings.equalizerEnabled.value, selectedPreset = preset.value, bands = bands.value)
    )

    fun onEnabledChange(enabled: Boolean) {
        playbackSettings.equalizerEnabled.value = enabled
        equalizerAudioProcessor.enabled = enabled
    }

    fun onPresetSelect(selected: Equalizer.Presets.Preset) {
        equalizerAudioProcessor.preset = selected
        playbackPreferenceManager.preset = selected
        preset.value = selected
        bands.value = selected.bandStates()
    }

    /** Copies the current gains into the Custom preset with [frequency] moved to [gainDb], and plays it. */
    fun onBandGainChange(
        frequency: Int,
        gainDb: Float
    ) {
        val maxGain = equalizerAudioProcessor.maxBandGain.toFloat()
        val gains = bands.value.map { band -> if (band.frequency == frequency) band.copy(gainDb = gainDb.coerceIn(-maxGain, maxGain)) else band }
        val custom = Equalizer.Presets.custom
        gains.forEach { band -> custom.bands.first { it.centerFrequency == band.frequency }.gain = band.gainDb.toDouble() }
        equalizerAudioProcessor.preset = custom
        preset.value = custom
        bands.value = gains
    }

    /** Stores the Custom preset once a band stops moving. */
    fun onBandGainChangeFinished() {
        playbackPreferenceManager.preset = Equalizer.Presets.custom
        playbackPreferenceManager.customPresetBands = Equalizer.Presets.custom.bands
    }

    private fun Equalizer.Presets.Preset.bandStates(): List<EqualizerBandState> = bands.map { EqualizerBandState(it.centerFrequency, it.gain.toFloat()) }
}
