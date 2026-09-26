package com.simplecityapps.shuttle.ui.screens.settings.equalizer

import com.simplecityapps.playback.dsp.equalizer.BandProcessor
import com.simplecityapps.playback.dsp.equalizer.EqualizerBand
import com.simplecityapps.playback.dsp.equalizer.cascadeAttenuation
import com.simplecityapps.playback.dsp.equalizer.frequencyResponseDb
import com.simplecityapps.playback.dsp.equalizer.toNyquistBand
import com.simplecityapps.shuttle.ui.screens.equalizer.FrequencyResponsePoint
import com.simplecityapps.shuttle.ui.screens.equalizer.MAX_FREQUENCY_HZ
import com.simplecityapps.shuttle.ui.screens.equalizer.MIN_FREQUENCY_HZ
import javax.inject.Inject
import kotlin.math.log10
import kotlin.math.pow
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/** Output sample rate assumed before playback has configured the real one, so the chart still has a curve to show. */
internal const val FALLBACK_OUTPUT_SAMPLE_RATE_HZ = 48_000

private const val RESPONSE_POINT_COUNT = 240

/**
 * Builds the equalizer's plotted frequency response for [bands] at [outputSampleRateHz]: the band
 * cascade's magnitude response ([frequencyResponseDb]), minus the headroom attenuation
 * [cascadeAttenuation] applies at that sample rate - the same function
 * `EqualizerAudioProcessor` uses - so the chart matches what actually reaches the output (#312).
 */
class ComputeFrequencyResponse @Inject constructor() {
    operator fun invoke(
        bands: List<EqualizerBandState>,
        outputSampleRateHz: Int?
    ): ImmutableList<FrequencyResponsePoint> {
        val sampleRateHz = outputSampleRateHz ?: FALLBACK_OUTPUT_SAMPLE_RATE_HZ
        val bandProcessors = bands.map { band ->
            BandProcessor(
                EqualizerBand(band.frequency, band.gainDb.toDouble()).toNyquistBand(),
                sampleRate = sampleRateHz,
                channelCount = 1,
                referenceGain = 0.0
            )
        }
        val attenuationDb = 20.0 * log10(cascadeAttenuation(bandProcessors, sampleRateHz).toDouble())

        val minHz = MIN_FREQUENCY_HZ.toDouble()
        val maxHz = minOf(MAX_FREQUENCY_HZ.toDouble(), sampleRateHz / 2.0)
        val span = maxHz / minHz

        return (0 until RESPONSE_POINT_COUNT).map { index ->
            val frequencyHz = minHz * span.pow(index.toDouble() / (RESPONSE_POINT_COUNT - 1))
            val gainDb = frequencyResponseDb(bandProcessors, preAmpGainDb = attenuationDb, frequencyHz, sampleRateHz)
            FrequencyResponsePoint(frequencyHz.toFloat(), gainDb.toFloat())
        }.toImmutableList()
    }
}
