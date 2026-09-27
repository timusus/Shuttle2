package com.simplecityapps.playback.dsp.equalizer

import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.pow

/**
 * The equalizer cascade's magnitude response in dB at [frequencyHz], evaluating each band's
 * [BandProcessor.magnitudeAt] analytically at `z = e^(j.omega)` rather than measuring an impulse
 * response via FFT - exact rather than approximate, and well-defined all the way to DC and Nyquist.
 *
 * [preAmpGainDb] is added directly because the pre-amp is a linear scalar applied before the
 * bands: scaling an LTI filter's input shifts its magnitude response by a constant number of dB.
 */
fun frequencyResponseDb(
    bandProcessors: List<BandProcessor>,
    preAmpGainDb: Double,
    frequencyHz: Double,
    sampleRateHz: Int
): Double {
    val omega = 2.0 * PI * frequencyHz / sampleRateHz
    var magnitude = 1.0
    for (bandProcessor in bandProcessors) {
        magnitude *= bandProcessor.magnitudeAt(omega)
    }
    return preAmpGainDb + (20.0 * log10(magnitude))
}

/** Lowest frequency considered when measuring the cascade's peak gain. Below this is inaudible. */
private const val ATTENUATION_ANALYSIS_MIN_FREQUENCY = 20.0

/** Number of log-spaced points between [ATTENUATION_ANALYSIS_MIN_FREQUENCY] and Nyquist used to find the peak. */
private const val ATTENUATION_ANALYSIS_POINT_COUNT = 512

/**
 * The linear pre-attenuation that keeps [bandProcessors]' cascade from exceeding unity gain: the
 * inverse of the peak magnitude of the whole band cascade's frequency response (or 1 when that peak
 * doesn't exceed unity). Shared by [com.simplecityapps.playback.exoplayer.EqualizerAudioProcessor]
 * (which applies it to the signal) and the frequency-response chart (which plots the curve it
 * actually produces), so the two never disagree about how much headroom the cascade needs.
 *
 * Sweeps a log-spaced frequency grid, multiplying every band's magnitude response together to find
 * the cascade's peak gain. A flat or cut-only cascade can never exceed unity, so it returns 1.
 */
fun cascadeAttenuation(bandProcessors: List<BandProcessor>, sampleRateHz: Int): Float {
    val nyquist = sampleRateHz / 2.0
    if (bandProcessors.isEmpty() || nyquist <= ATTENUATION_ANALYSIS_MIN_FREQUENCY) {
        return 1f
    }

    var peak = 0.0
    val span = nyquist / ATTENUATION_ANALYSIS_MIN_FREQUENCY
    for (index in 0 until ATTENUATION_ANALYSIS_POINT_COUNT) {
        val frequency = ATTENUATION_ANALYSIS_MIN_FREQUENCY * span.pow(index.toDouble() / (ATTENUATION_ANALYSIS_POINT_COUNT - 1))
        val omega = 2.0 * PI * frequency / sampleRateHz
        var magnitude = 1.0
        for (bandProcessor in bandProcessors) {
            magnitude *= bandProcessor.magnitudeAt(omega)
        }
        if (magnitude > peak) {
            peak = magnitude
        }
    }

    return if (peak.isFinite() && peak > 1.0) (1.0 / peak).toFloat() else 1f
}
