package com.simplecityapps.shuttle.shared.playback

import com.simplecityapps.playback.dsp.equalizer.Equalizer
import com.simplecityapps.playback.dsp.equalizer.EqualizerBand
import com.simplecityapps.playback.dsp.equalizer.EqualizerCascade
import com.simplecityapps.playback.equalizer.EqualizerControl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The equalizer on iOS: the Swift engine filters, and this hands it an [IosEqualizerDesign] on each change, which the
 * engine designs for each rate the audio plays at. Built with the saved settings, which it hands the engine straight
 * away.
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

    /** Null: the engine designs for each item's rate and doesn't report it, so the chart uses its fallback. */
    override val outputSampleRateHz: StateFlow<Int?> = MutableStateFlow<Int?>(null).asStateFlow()

    override var enabled: Boolean = enabled
        set(value) {
            field = value
            apply()
        }

    /** The band gains are captured when it's set; edits made after that apply once it's set again. */
    override var preset: Equalizer.Presets.Preset = preset
        set(value) {
            field = value
            bands = value.snapshot()
            apply()
        }

    override var preampGainDb: Float = preampGainDb
        set(value) {
            field = value
            apply()
        }

    private var bands = preset.snapshot()

    init {
        apply()
    }

    private fun apply() {
        player.setEqualizer(IosEqualizerDesign(enabled, bands, preampGainDb))
    }
}

/**
 * One equalizer setting, for the engine to design at whatever rate it plays: [filtersAt] runs the shared
 * [EqualizerCascade], the maths Android's `EqualizerAudioProcessor` runs. Immutable, so the engine may call it on its
 * own queue.
 */
class IosEqualizerDesign internal constructor(
    val enabled: Boolean,
    private val bands: List<EqualizerBand>,
    private val preampGainDb: Float
) {
    fun filtersAt(sampleRateHz: Int): IosEqualizerFilters {
        val cascade = EqualizerCascade(bands, sampleRateHz)
        return IosEqualizerFilters(cascade.headroomDb + preampGainDb, cascade.coefficients)
    }
}

/**
 * The equalizer at one rate: [preampDb], the cascade's headroom attenuation plus the user's preamp, applied after the
 * filters as Android applies them (the engine's limiter stands in for Android's clamp), and one peaking biquad per band,
 * five [coefficients] each (b0, b1, b2, a1, a2, normalised to a0 = 1).
 */
class IosEqualizerFilters(
    val preampDb: Float,
    val coefficients: DoubleArray
)

/** Copies, so editing the custom preset's bands in place never reaches a design already handed over. */
private fun Equalizer.Presets.Preset.snapshot(): List<EqualizerBand> = bands.map { EqualizerBand(it.centerFrequency, it.gain) }
