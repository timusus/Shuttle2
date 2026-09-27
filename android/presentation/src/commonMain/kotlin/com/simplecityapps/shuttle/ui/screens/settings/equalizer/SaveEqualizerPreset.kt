package com.simplecityapps.shuttle.ui.screens.settings.equalizer

import com.simplecityapps.playback.dsp.equalizer.Equalizer
import com.simplecityapps.playback.equalizer.EqualizerPresetStore
import dev.zacsweers.metro.Inject

/** Stores [Equalizer.Presets.Preset] as the equalizer's preset; for the Custom preset, its band gains too. */
class SaveEqualizerPreset @Inject constructor(
    private val presetStore: EqualizerPresetStore
) {
    operator fun invoke(preset: Equalizer.Presets.Preset) {
        presetStore.preset = preset
        if (preset == Equalizer.Presets.custom) {
            presetStore.customPresetBands = preset.bands
        }
    }
}
