package com.simplecityapps.playback.equalizer

/** An equalizer band's centre frequency and gain, as the UI edits it. */
data class EqualizerBandGain(val frequency: Int, val gainDb: Float)

/** One point of an equalizer's plotted frequency-response curve. */
data class FrequencyResponsePoint(val frequencyHz: Float, val gainDb: Float)

/**
 * The equalizer cascade's plotted frequency response for [bands] at [outputSampleRateHz], matching what the live
 * DSP applies (headroom attenuation included), between [minFrequencyHz] and [maxFrequencyHz] (or Nyquist, if lower).
 */
fun interface EqualizerFrequencyResponse {
    operator fun invoke(
        bands: List<EqualizerBandGain>,
        outputSampleRateHz: Int?,
        minFrequencyHz: Float,
        maxFrequencyHz: Float,
        pointCount: Int
    ): List<FrequencyResponsePoint>
}
