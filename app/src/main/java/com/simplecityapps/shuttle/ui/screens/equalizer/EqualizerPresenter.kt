package com.simplecityapps.shuttle.ui.screens.equalizer

import com.simplecityapps.playback.equalizer.Equalizer
import com.simplecityapps.playback.equalizer.EqualizerBand
import com.simplecityapps.playback.local.exoplayer.EqualizerAudioProcessor
import com.simplecityapps.playback.persistence.PlaybackPreferenceManager
import com.simplecityapps.shuttle.ui.common.mvp.BaseContract
import com.simplecityapps.shuttle.ui.common.mvp.BasePresenter
import javax.inject.Inject

interface EqualizerContract {

    interface View {
        fun initializeEqualizerView(activated: Boolean, equalizer: Equalizer.Presets.Preset, maxBandGain: Int)
        fun updateEqualizerView(preset: Equalizer.Presets.Preset)
        fun updateSelectedPreset(preset: Equalizer.Presets.Preset)
        fun showEnabled(enabled: Boolean)
    }

    interface Presenter : BaseContract.Presenter<View> {
        fun toggleEqualizer(activated: Boolean)
        fun updateBand(band: EqualizerBand)
        fun setPreset(preset: Equalizer.Presets.Preset)
    }
}

class EqualizerPresenter @Inject constructor(
    private val equalizerAudioProcessor: EqualizerAudioProcessor,
    private val playbackPreferenceManager: PlaybackPreferenceManager
) : BasePresenter<EqualizerContract.View>(), EqualizerContract.Presenter {

    override fun bindView(view: EqualizerContract.View) {
        super.bindView(view)

        view.initializeEqualizerView(equalizerAudioProcessor.enabled, equalizerAudioProcessor.preset, equalizerAudioProcessor.maxBandGain)
    }

    override fun toggleEqualizer(activated: Boolean) {
        playbackPreferenceManager.equalizerEnabled = activated
        equalizerAudioProcessor.enabled = activated
        view?.showEnabled(activated)
    }

    override fun updateBand(band: EqualizerBand) {
        equalizerAudioProcessor.preset.bands.forEach { currentPresetBand ->
            // Copy the current preset over to the custom preset
            Equalizer.Presets.custom.bands.first { it.centerFrequency == currentPresetBand.centerFrequency }.apply {
                gain = currentPresetBand.gain

                // Update the gain of the band in question
                if (currentPresetBand.centerFrequency == band.centerFrequency) {
                    gain = band.gain
                }
            }
        }
        equalizerAudioProcessor.preset = Equalizer.Presets.custom
        playbackPreferenceManager.preset = Equalizer.Presets.custom
        playbackPreferenceManager.customPresetBands = Equalizer.Presets.custom.bands
        view?.updateSelectedPreset(Equalizer.Presets.custom)
    }

    override fun setPreset(preset: Equalizer.Presets.Preset) {
        equalizerAudioProcessor.preset = preset
        playbackPreferenceManager.preset = preset

        view?.updateEqualizerView(preset)
    }
}