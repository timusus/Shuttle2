package com.simplecityapps.playback.dsp.equalizer

import com.squareup.moshi.JsonClass
import java.io.Serializable
import kotlin.math.pow
import kotlin.math.sqrt

@JsonClass(generateAdapter = true)
open class EqualizerBand(val centerFrequency: Int, var gain: Double) : Serializable {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EqualizerBand

        if (centerFrequency != other.centerFrequency) return false
        if (gain != other.gain) return false

        return true
    }

    override fun hashCode(): Int {
        var result = centerFrequency
        result = 31 * result + gain.hashCode()
        return result
    }
}

fun EqualizerBand.toNyquistBand(): NyquistBand {

    val bandWidthGain = if (gain > 0) {
        sqrt((gain.pow(2) / 2)) // Boost
    } else {
        -sqrt((gain.pow(2) / 2)) // Cut
    }

    return NyquistBand(centerFrequency, (centerFrequency * 0.35f).toInt(), gain, bandWidthGain)
}

class NyquistBand(centerFrequency: Int, val bandwidth: Int, peakGain: Double, val bandwidthGain: Double) : EqualizerBand(centerFrequency, peakGain)
