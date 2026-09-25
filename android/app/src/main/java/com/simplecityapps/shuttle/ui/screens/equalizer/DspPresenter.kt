package com.simplecityapps.shuttle.ui.screens.equalizer

import com.simplecityapps.playback.dsp.equalizer.Equalizer
import com.simplecityapps.playback.dsp.equalizer.EqualizerBand
import com.simplecityapps.playback.dsp.replaygain.ReplayGainAudioProcessor
import com.simplecityapps.playback.dsp.replaygain.ReplayGainMode
import com.simplecityapps.playback.exoplayer.EqualizerAudioProcessor
import com.simplecityapps.playback.persistence.PlaybackPreferenceManager
import com.simplecityapps.playback.settings.PlaybackSettings
import com.simplecityapps.shuttle.ui.common.mvp.BaseContract
import com.simplecityapps.shuttle.ui.common.mvp.BasePresenter
import javax.inject.Inject

interface EqualizerContract {
    interface View {
        fun initializeEqualizerView(
            activated: Boolean,
            equalizer: Equalizer.Presets.Preset,
            maxBandGain: Int
        )

        fun updateEqualizerView(preset: Equalizer.Presets.Preset)

        fun updateSelectedEqPreset(preset: Equalizer.Presets.Preset)

        fun showEqEnabled(enabled: Boolean)

        fun updateSelectedReplayGainMode(mode: ReplayGainMode)

        fun updatePreAmpGain(gain: Double)
    }

    interface Presenter : BaseContract.Presenter<View> {
        fun toggleEqualizer(activated: Boolean)

        fun updateBand(band: EqualizerBand)

        fun setEqPreset(preset: Equalizer.Presets.Preset)

        fun setReplayGainMode(mode: ReplayGainMode)

        fun setPreAmpGain(gain: Double)
    }
}

class DspPresenter
@Inject
constructor(
    private val playbackPreferenceManager: PlaybackPreferenceManager,
    private val playbackSettings: PlaybackSettings,
    private val equalizerAudioProcessor: EqualizerAudioProcessor,
    private val replayGainAudioProcessor: ReplayGainAudioProcessor
) : BasePresenter<EqualizerContract.View>(),
    EqualizerContract.Presenter {
    override fun bindView(view: EqualizerContract.View) {
        super.bindView(view)

        view.initializeEqualizerView(equalizerAudioProcessor.enabled, equalizerAudioProcessor.preset, equalizerAudioProcessor.maxBandGain)
        view.updateSelectedReplayGainMode(playbackSettings.replayGainMode.value)
        view.updatePreAmpGain(playbackSettings.preAmpGain.value.toDouble())
    }

    override fun toggleEqualizer(activated: Boolean) {
        playbackSettings.equalizerEnabled.value = activated
        equalizerAudioProcessor.enabled = activated
        view?.showEqEnabled(activated)
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
        view?.updateSelectedEqPreset(Equalizer.Presets.custom)
    }

    override fun setEqPreset(preset: Equalizer.Presets.Preset) {
        equalizerAudioProcessor.preset = preset
        playbackPreferenceManager.preset = preset

        view?.updateEqualizerView(preset)
    }

    override fun setReplayGainMode(mode: ReplayGainMode) {
        replayGainAudioProcessor.mode = mode
        playbackSettings.replayGainMode.value = mode
    }

    override fun setPreAmpGain(gain: Double) {
        replayGainAudioProcessor.preAmpGain = gain
        playbackSettings.preAmpGain.value = gain.toFloat()
    }
}
