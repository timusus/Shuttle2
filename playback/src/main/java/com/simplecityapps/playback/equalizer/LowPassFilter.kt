package com.simplecityapps.playback.equalizer

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class LowPassFilter(
    val freq: Int,
    val sampleRate: Int,
    val channelCount: Int
) {

    private val q = 1.0
    private val omega = 2.0 * PI * freq / sampleRate
    private val sin = sin(omega)
    private val cos = cos(omega)
    private val alpha = sin / (2.0 * q)

    private val a0 = 1.0 + alpha
    private val a1 = -2.0 * cos
    private val a2 = 1.0 - alpha

    private val b0 = (1.0 - cos) / 2.0
    private val b1 = 1.0 - cos
    private val b2 = (1.0 - cos) / 2.0

    private val xHist = Array(channelCount) { FloatArray(2) { 0f } }
    private val yHist = Array(channelCount) { FloatArray(2) { 0f } }

    fun processSample(sample: Float, channelIndex: Int): Float {

        val adjustedSample = (
                ((b0 / a0) * sample)
                        + ((b1 / a0) * xHist[channelIndex][0])
                        + ((b2 / a0) * xHist[channelIndex][1])
                        - ((a1 / a0) * yHist[channelIndex][0])
                        - ((a2 / a0) * yHist[channelIndex][1])
                ).toFloat()

        xHist[channelIndex][1] = xHist[channelIndex][0]
        xHist[channelIndex][0] = sample

        yHist[channelIndex][1] = yHist[channelIndex][0]
        yHist[channelIndex][0] = adjustedSample

        return adjustedSample
    }

    fun reset() {
        for (i in 0 until channelCount) {
            xHist[i] = FloatArray(2) { 0f }
            yHist[i] = FloatArray(2) { 0f }
        }
    }
}