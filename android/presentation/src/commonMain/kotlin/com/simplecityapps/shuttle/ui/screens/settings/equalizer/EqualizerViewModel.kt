package com.simplecityapps.shuttle.ui.screens.settings.equalizer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.playback.dsp.equalizer.Equalizer
import com.simplecityapps.playback.equalizer.EqualizerControl
import com.simplecityapps.shuttle.settings.EqualizerSettings
import com.simplecityapps.shuttle.settings.ObserveSetting
import com.simplecityapps.shuttle.settings.ReadSetting
import com.simplecityapps.shuttle.settings.SaveSetting
import com.simplecityapps.shuttle.ui.screens.equalizer.FrequencyResponsePoint
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
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
    val bands: List<EqualizerBandState> = emptyList(),
    val preampGainDb: Float = 0f,
    val frequencyResponse: ImmutableList<FrequencyResponsePoint> = persistentListOf(),
    /** The automatic attenuation that keeps boosted bands from clipping, in dB: 0, or negative. */
    val headroomAttenuationDb: Float = 0f
)

/**
 * The equalizer: the on/off switch, the preset, the preamp and the band gains. Changes go to the live
 * [EqualizerControl] straight away, as the legacy DSP screen did; moving a band switches to the
 * Custom preset, which is stored once the drag ends.
 */
@ViewModelKey(EqualizerViewModel::class)
@ContributesIntoMap(AppScope::class)
class EqualizerViewModel @Inject constructor(
    observeSetting: ObserveSetting,
    readSetting: ReadSetting,
    private val saveSetting: SaveSetting,
    private val saveEqualizerPreset: SaveEqualizerPreset,
    private val equalizer: EqualizerControl,
    private val computeFrequencyResponse: ComputeFrequencyResponse
) : ViewModel() {
    private val preset = MutableStateFlow(equalizer.preset)
    private val bands = MutableStateFlow(equalizer.preset.bandStates())

    val uiState: StateFlow<EqualizerUiState> = combine(
        observeSetting(EqualizerSettings.Enabled),
        preset,
        bands,
        observeSetting(EqualizerSettings.PreampGain),
        equalizer.outputSampleRateHz,
        ::stateOf
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = stateOf(
            enabled = readSetting(EqualizerSettings.Enabled),
            preset = preset.value,
            bands = bands.value,
            preampGainDb = readSetting(EqualizerSettings.PreampGain),
            outputSampleRateHz = equalizer.outputSampleRateHz.value
        )
    )

    private fun stateOf(
        enabled: Boolean,
        preset: Equalizer.Presets.Preset,
        bands: List<EqualizerBandState>,
        preampGainDb: Float,
        outputSampleRateHz: Int?
    ): EqualizerUiState {
        val response = computeFrequencyResponse(bands, preampGainDb, outputSampleRateHz)
        return EqualizerUiState(
            enabled = enabled,
            selectedPreset = preset,
            bands = bands,
            preampGainDb = preampGainDb,
            frequencyResponse = response.points,
            headroomAttenuationDb = response.headroomAttenuationDb
        )
    }

    fun onEnabledChange(enabled: Boolean) {
        saveSetting(EqualizerSettings.Enabled, enabled)
        equalizer.enabled = enabled
    }

    fun onPresetSelect(selected: Equalizer.Presets.Preset) {
        equalizer.preset = selected
        saveEqualizerPreset(selected)
        preset.value = selected
        bands.value = selected.bandStates()
    }

    /** Copies the current gains into the Custom preset with [frequency] moved to [gainDb], and plays it. */
    fun onBandGainChange(
        frequency: Int,
        gainDb: Float
    ) {
        val maxGain = equalizer.maxBandGain.toFloat()
        val gains = bands.value.map { band -> if (band.frequency == frequency) band.copy(gainDb = gainDb.coerceIn(-maxGain, maxGain)) else band }
        val custom = Equalizer.Presets.custom
        gains.forEach { band -> custom.bands.first { it.centerFrequency == band.frequency }.gain = band.gainDb.toDouble() }
        equalizer.preset = custom
        preset.value = custom
        bands.value = gains
    }

    /** Plays and stores the preamp at [gainDb], within the processor's limit. */
    fun onPreampGainChange(gainDb: Float) {
        val maxGain = equalizer.maxPreampGain.toFloat()
        val gain = gainDb.coerceIn(-maxGain, maxGain)
        saveSetting(EqualizerSettings.PreampGain, gain)
        equalizer.preampGainDb = gain
    }

    /** Stores the Custom preset once a band stops moving. */
    fun onBandGainChangeFinished() {
        saveEqualizerPreset(Equalizer.Presets.custom)
    }

    private fun Equalizer.Presets.Preset.bandStates(): List<EqualizerBandState> = bands.map { EqualizerBandState(it.centerFrequency, it.gain.toFloat()) }
}
