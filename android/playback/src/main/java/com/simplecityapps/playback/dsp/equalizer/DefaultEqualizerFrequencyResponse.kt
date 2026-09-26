package com.simplecityapps.playback.dsp.equalizer

import com.simplecityapps.playback.equalizer.EqualizerBandGain
import com.simplecityapps.playback.equalizer.EqualizerFrequencyResponse
import com.simplecityapps.playback.equalizer.EqualizerResponse
import com.simplecityapps.playback.equalizer.FrequencyResponsePoint
import javax.inject.Inject
import kotlin.math.log10
import kotlin.math.pow

/** Output sample rate assumed before playback has configured the real one, so the chart still has a curve to show. */
internal const val FALLBACK_OUTPUT_SAMPLE_RATE_HZ = 48_000

/**
 * Builds the equalizer's plotted frequency response: the band cascade's magnitude response
 * ([frequencyResponseDb]), minus the headroom attenuation [cascadeAttenuation] applies at that sample rate -
 * the same function `EqualizerAudioProcessor` uses - plus the user's preamp, so the chart matches what actually
 * reaches the output (#312, #236).
 */
class DefaultEqualizerFrequencyResponse
@Inject
constructor() : EqualizerFrequencyResponse {
    override fun invoke(
        bands: List<EqualizerBandGain>,
        preampGainDb: Float,
        outputSampleRateHz: Int?,
        minFrequencyHz: Float,
        maxFrequencyHz: Float,
        pointCount: Int
    ): EqualizerResponse {
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

        val minHz = minFrequencyHz.toDouble()
        val maxHz = minOf(maxFrequencyHz.toDouble(), sampleRateHz / 2.0)
        val span = maxHz / minHz

        val points = (0 until pointCount).map { index ->
            val frequencyHz = minHz * span.pow(index.toDouble() / (pointCount - 1))
            val gainDb = frequencyResponseDb(bandProcessors, preAmpGainDb = attenuationDb + preampGainDb, frequencyHz, sampleRateHz)
            FrequencyResponsePoint(frequencyHz.toFloat(), gainDb.toFloat())
        }
        return EqualizerResponse(points, attenuationDb.toFloat())
    }
}
