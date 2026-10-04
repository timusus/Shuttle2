package com.simplecityapps.playback.equalizer

import com.simplecityapps.playback.dsp.equalizer.Equalizer
import com.simplecityapps.playback.dsp.equalizer.EqualizerBand
import kotlinx.coroutines.flow.Flow

/** Where the equalizer's chosen preset, and the Custom preset's band gains, are kept across restarts. */
interface EqualizerPresetStore {
    var preset: Equalizer.Presets.Preset

    var customPresetBands: List<EqualizerBand>?

    /** The stored preset now, then again each time it changes. */
    fun observePreset(): Flow<Equalizer.Presets.Preset>
}
