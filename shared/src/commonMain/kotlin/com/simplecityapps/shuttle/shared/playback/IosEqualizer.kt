package com.simplecityapps.shuttle.shared.playback

import com.simplecityapps.playback.dsp.equalizer.Equalizer
import com.simplecityapps.playback.dsp.equalizer.EqualizerCascade
import com.simplecityapps.playback.equalizer.EqualizerControl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The equalizer on iOS: the Swift engine filters, and this designs what it filters with. Each change re-designs the
 * [preset]'s bands at the engine's fixed rate ([IosAudioPlayer.engineSampleRate]) with the shared [EqualizerCascade],
 * the maths Android's `EqualizerAudioProcessor` runs, and hands the engine the biquad coefficients and one preamp: the
 * cascade's headroom attenuation plus the user's [preampGainDb], as Android applies them. The engine's limiter then
 * stands in for Android's clamp. Built with the saved settings, which it hands the engine straight away.
 *
 * Main thread only, as the equalizer screen and the engine adapter are.
 */
class IosEqualizer(
    private val player: IosAudioPlayer,
    enabled: Boolean,
    preset: Equalizer.Presets.Preset,
    preampGainDb: Float
) : EqualizerControl {
    // As Android's EqualizerAudioProcessor
    override val maxBandGain = 12

    override val maxPreampGain = 12

    private val sampleRateHz = player.engineSampleRate()

    /** The engine's rate: it's fixed, and known before any audio plays. */
    override val outputSampleRateHz: StateFlow<Int?> = MutableStateFlow<Int?>(sampleRateHz).asStateFlow()

    override var enabled: Boolean = enabled
        set(value) {
            field = value
            apply()
        }

    /** The band gains are captured when it's set; edits made after that apply once it's set again. */
    override var preset: Equalizer.Presets.Preset = preset
        set(value) {
            field = value
            cascade = EqualizerCascade(value.bands, sampleRateHz)
            apply()
        }

    override var preampGainDb: Float = preampGainDb
        set(value) {
            field = value
            apply()
        }

    private var cascade = EqualizerCascade(preset.bands, sampleRateHz)

    init {
        apply()
    }

    private fun apply() {
        player.setEqualizer(enabled, cascade.headroomDb + preampGainDb, cascade.coefficients)
    }
}
