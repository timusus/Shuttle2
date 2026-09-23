package com.simplecityapps.playback.dsp.equalizer

import kotlin.math.PI
import kotlin.math.log10

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
