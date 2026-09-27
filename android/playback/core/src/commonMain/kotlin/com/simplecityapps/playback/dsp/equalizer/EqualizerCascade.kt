package com.simplecityapps.playback.dsp.equalizer

import kotlin.math.log10

/**
 * The equalizer as an engine outside Media3 runs it (iOS's `PCMProcessor`): [bands] as [BandProcessor]s designed at
 * [sampleRateHz], the rate the audio is filtered at, handed over as raw biquad [coefficients], and the headroom
 * attenuation Android's `EqualizerAudioProcessor` applies before its clamp, from the same [cascadeAttenuation].
 */
class EqualizerCascade(
    bands: List<EqualizerBand>,
    val sampleRateHz: Int
) {
    private val bandProcessors = bands.map { band ->
        BandProcessor(band.toNyquistBand(), sampleRate = sampleRateHz, channelCount = 1, referenceGain = 0.0)
    }

    /** Five per band, in band order: b0, b1, b2, a1, a2, normalised to a0 = 1 ([BandProcessor.coefficients]). */
    val coefficients: DoubleArray = DoubleArray(bandProcessors.size * 5).also { all ->
        bandProcessors.forEachIndexed { index, processor -> processor.coefficients.copyInto(all, index * 5) }
    }

    /** The attenuation, in dB (0, or negative), that keeps the cascade's peak at unity gain. */
    val headroomDb: Float = (20.0 * log10(cascadeAttenuation(bandProcessors, sampleRateHz).toDouble())).toFloat()
}
