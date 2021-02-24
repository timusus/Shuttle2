package com.simplecityapps.playback.equalizer

import kotlin.math.*

class BandProcessor(val band: NyquistBand, val sampleRate: Int, val channelCount: Int, val referenceGain: Int) {

    private val G0 = referenceGain.toDouble().fromDb()
    private val GB = band.bandwidthGain.toDouble().fromDb()
    private val G1 = band.gain.toDouble().fromDb()

    private val xHist = Array(channelCount) { FloatArray(2) { 0f } }
    private val yHist = Array(channelCount) { FloatArray(2) { 0f } }

    private val beta = tan((band.bandwidth / 2.0) * PI / (sampleRate / 2.0)) * (sqrt(abs((GB.pow(2)) - (G0.pow(2)))) / sqrt(abs((G1.pow(2)) - (GB.pow(2)))))

    private val a1 = -2.0 * cos(band.centerFrequency * PI / (sampleRate / 2.0)) / (1.0 + beta)
    private val a2 = (1.0 - beta) / (1.0 + beta)

    private val b0 = (G0 + (G1 * beta)) / (1.0 + beta)
    private val b1 = -2.0 * G0 * cos(band.centerFrequency * PI / (sampleRate / 2.0)) / (1.0 + beta)
    private val b2 = (G0 - (G1 * beta)) / (1.0 + beta)

    init {
        if (band.gain > 0) {
            // Boost
            if (band.bandwidthGain < referenceGain || band.gain < band.bandwidthGain) {
                throw IllegalArgumentException("Invalid parameters. Boost gain ($band.gain) must be greater than bandwidth gain ($band.bandwidthGain), which must be greater than reference ($referenceGain)")
            }
        } else if (band.gain < 0) {
            // Cut
            if (band.bandwidthGain > referenceGain || band.gain > band.bandwidthGain) {
                throw IllegalArgumentException("Invalid parameters. Cut gain ($band.gain) must be less than bandwidth gain ($band.bandwidthGain), which must be less than reference ($referenceGain)")
            }
        }
    }

    fun processSample(sample: Float, channelIndex: Int): Float {

        if (band.bandwidthGain == 0 && band.gain == 0) {
            return sample
        }

        val adjustedSample = (
                (b0 * sample)
                        + (b1 * xHist[channelIndex][0])
                        + (b2 * xHist[channelIndex][1])
                        - (a1 * yHist[channelIndex][0])
                        - (a2 * yHist[channelIndex][1])
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