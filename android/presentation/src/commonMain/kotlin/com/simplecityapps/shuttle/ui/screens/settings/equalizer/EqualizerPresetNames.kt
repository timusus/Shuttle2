package com.simplecityapps.shuttle.ui.screens.settings.equalizer

import com.simplecityapps.playback.dsp.equalizer.Equalizer
import com.simplecityapps.shuttle.ui.text.StringKey

/** The preset's name as the equalizer screen shows it. */
val Equalizer.Presets.Preset.nameKey: StringKey
    get() = when (this) {
        Equalizer.Presets.Preset.Flat -> StringKey.EQ_PRESET_FLAT
        Equalizer.Presets.Preset.Custom -> StringKey.EQ_PRESET_CUSTOM
        Equalizer.Presets.Preset.BassBoost -> StringKey.EQ_PRESET_BASS_BOOST
        Equalizer.Presets.Preset.BassReducer -> StringKey.EQ_PRESET_BASS_REDUCE
        Equalizer.Presets.Preset.VocalBoost -> StringKey.EQ_PRESET_VOCAL_BOOST
        Equalizer.Presets.Preset.VocalReducer -> StringKey.EQ_PRESET_VOCAL_REDUCE
    }
