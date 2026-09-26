package com.simplecityapps.shuttle.ui.screens.settings.equalizer

import com.simplecityapps.playback.dsp.equalizer.Equalizer
import com.simplecityapps.playback.persistence.PlaybackPreferenceManager
import javax.inject.Inject

/** Stores [Equalizer.Presets.Preset] as the equalizer's preset; for the Custom preset, its band gains too. */
class SaveEqualizerPreset @Inject constructor(
    private val playbackPreferenceManager: PlaybackPreferenceManager,
) {
    operator fun invoke(preset: Equalizer.Presets.Preset) {
        playbackPreferenceManager.preset = preset
        if (preset == Equalizer.Presets.custom) {
            playbackPreferenceManager.customPresetBands = preset.bands
        }
    }
}
