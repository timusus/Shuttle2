package com.simplecityapps.playback.equalizer

import com.simplecityapps.playback.dsp.equalizer.Equalizer
import kotlinx.coroutines.flow.StateFlow

/** The live equalizer in the audio path: what the equalizer screen changes as the user moves it. */
interface EqualizerControl {
    var enabled: Boolean

    var preset: Equalizer.Presets.Preset

    var preampGainDb: Float

    /** The largest boost or cut a band takes, in dB. */
    val maxBandGain: Int

    /** The largest boost or cut the preamp takes, in dB. */
    val maxPreampGain: Int

    /** The sample rate the equalizer is filtering at, once audio has reached it. */
    val outputSampleRateHz: StateFlow<Int?>
}
