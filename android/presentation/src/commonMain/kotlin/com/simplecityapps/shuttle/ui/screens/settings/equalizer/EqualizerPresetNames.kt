package com.simplecityapps.shuttle.ui.screens.settings.equalizer

import com.simplecityapps.playback.dsp.equalizer.Equalizer
import com.simplecityapps.shuttle.ui.text.EqualizerPresetString
import com.simplecityapps.shuttle.ui.text.StringKey

/** The preset's name as the equalizer screen shows it. */
val Equalizer.Presets.Preset.nameKey: StringKey
    get() = when (this) {
        Equalizer.Presets.Preset.Flat -> EqualizerPresetString.EQ_PRESET_FLAT
        Equalizer.Presets.Preset.Custom -> EqualizerPresetString.EQ_PRESET_CUSTOM
        Equalizer.Presets.Preset.BassBoost -> EqualizerPresetString.EQ_PRESET_BASS_BOOST
        Equalizer.Presets.Preset.BassReducer -> EqualizerPresetString.EQ_PRESET_BASS_REDUCE
        Equalizer.Presets.Preset.VocalBoost -> EqualizerPresetString.EQ_PRESET_VOCAL_BOOST
        Equalizer.Presets.Preset.VocalReducer -> EqualizerPresetString.EQ_PRESET_VOCAL_REDUCE
    }
