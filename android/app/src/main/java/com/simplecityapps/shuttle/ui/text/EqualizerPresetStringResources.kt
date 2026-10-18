package com.simplecityapps.shuttle.ui.text

import androidx.annotation.StringRes
import com.simplecityapps.core.R as CoreR

@get:StringRes
val EqualizerPresetString.resId: Int
    get() = when (this) {
        EqualizerPresetString.EQ_PRESET_FLAT -> CoreR.string.eq_preset_flat
        EqualizerPresetString.EQ_PRESET_CUSTOM -> CoreR.string.eq_preset_custom
        EqualizerPresetString.EQ_PRESET_BASS_BOOST -> CoreR.string.eq_preset_bass_boost
        EqualizerPresetString.EQ_PRESET_BASS_REDUCE -> CoreR.string.eq_preset_bass_reduce
        EqualizerPresetString.EQ_PRESET_VOCAL_BOOST -> CoreR.string.eq_preset_vocal_boost
        EqualizerPresetString.EQ_PRESET_VOCAL_REDUCE -> CoreR.string.eq_preset_vocal_reduce
    }
