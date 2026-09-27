package com.simplecityapps.playback.equalizer

import com.simplecityapps.playback.dsp.equalizer.Equalizer
import com.simplecityapps.playback.dsp.equalizer.EqualizerBand

/** Where the equalizer's chosen preset, and the Custom preset's band gains, are kept across restarts. */
interface EqualizerPresetStore {
    var preset: Equalizer.Presets.Preset

    var customPresetBands: List<EqualizerBand>?
}
