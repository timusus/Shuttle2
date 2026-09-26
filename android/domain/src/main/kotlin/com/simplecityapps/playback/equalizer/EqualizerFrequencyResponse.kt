package com.simplecityapps.playback.equalizer

/** An equalizer band's centre frequency and gain, as the UI edits it. */
data class EqualizerBandGain(val frequency: Int, val gainDb: Float)

/** One point of an equalizer's plotted frequency-response curve. */
data class FrequencyResponsePoint(val frequencyHz: Float, val gainDb: Float)

/**
 * The equalizer's plotted response: the curve [points], and the automatic headroom attenuation the curve includes,
 * in dB (0, or negative when the bands' boosts had to be pulled back so they can't clip).
 */
data class EqualizerResponse(val points: List<FrequencyResponsePoint>, val headroomAttenuationDb: Float)

/**
 * The equalizer cascade's plotted frequency response for [bands] and the user's [preampGainDb] at [outputSampleRateHz],
 * matching what the live DSP applies (headroom attenuation included), between [minFrequencyHz] and [maxFrequencyHz]
 * (or Nyquist, if lower).
 */
fun interface EqualizerFrequencyResponse {
    operator fun invoke(
        bands: List<EqualizerBandGain>,
        preampGainDb: Float,
        outputSampleRateHz: Int?,
        minFrequencyHz: Float,
        maxFrequencyHz: Float,
        pointCount: Int
    ): EqualizerResponse
}
