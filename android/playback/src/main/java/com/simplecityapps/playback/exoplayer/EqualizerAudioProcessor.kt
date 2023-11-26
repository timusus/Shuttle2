package com.simplecityapps.playback.exoplayer

import androidx.core.math.MathUtils.clamp
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.audio.AudioProcessor
import com.google.android.exoplayer2.audio.AudioProcessor.UnhandledAudioFormatException
import com.google.android.exoplayer2.audio.BaseAudioProcessor
import com.simplecityapps.playback.dsp.equalizer.BandProcessor
import com.simplecityapps.playback.dsp.equalizer.Equalizer
import com.simplecityapps.playback.dsp.equalizer.toNyquistBand
import com.simplecityapps.playback.exoplayer.ByteUtils.getInt24
import com.simplecityapps.playback.exoplayer.ByteUtils.putInt24
import timber.log.Timber
import java.nio.ByteBuffer

class EqualizerAudioProcessor(enabled: Boolean) : BaseAudioProcessor() {

    var bandProcessors = emptyList<BandProcessor>()

    var preset: Equalizer.Presets.Preset = Equalizer.Presets.flat
        set(value) {
            field = value
            updateBandProcessors()
        }

    // Maximum allowed gain/cut for each band
    val maxBandGain = 12

    var enabled: Boolean = enabled
        set(value) {
            field = value

            Timber.v("Equalizer enabled: $value")
        }

    private fun updateBandProcessors() {
        if (outputAudioFormat.channelCount <= 0) {
            return
        }

        bandProcessors = preset.bands.map { band ->
            BandProcessor(
                band.toNyquistBand(),
                sampleRate = outputAudioFormat.sampleRate,
                channelCount = outputAudioFormat.channelCount,
                referenceGain = 0.0
            )
        }.toList()
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        super.onConfigure(inputAudioFormat)

        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_24BIT) {
            throw UnhandledAudioFormatException(inputAudioFormat)
        }

        updateBandProcessors()

        return inputAudioFormat
    }

    override fun onFlush() {
        super.onFlush()

        Timber.v("onFlush() called")
        updateBandProcessors()
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (enabled) {
            val size = inputBuffer.remaining()
            val buffer = replaceOutputBuffer(size)

            when (outputAudioFormat.encoding) {
                C.ENCODING_PCM_16BIT -> {
                    while (inputBuffer.hasRemaining()) {
                        for (channelIndex in 0 until outputAudioFormat.channelCount) {
                            val sample = inputBuffer.short
                            var targetSample = sample.toFloat()
                            for (band in bandProcessors) {
                                targetSample = band.processSample(targetSample, channelIndex)
                            }
                            buffer.putShort(clamp(targetSample, Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort())
                            if (!inputBuffer.hasRemaining()) {
                                break
                            }
                        }
                    }
                }
                C.ENCODING_PCM_24BIT -> {
                    while (inputBuffer.hasRemaining()) {
                        for (channelIndex in 0 until outputAudioFormat.channelCount) {
                            val sample = inputBuffer.getInt24()
                            var targetSample = sample.toFloat()
                            for (band in bandProcessors) {
                                targetSample = band.processSample(targetSample, channelIndex)
                            }
                            buffer.putInt24(clamp(targetSample, ByteUtils.Int24_MIN_VALUE.toFloat(), ByteUtils.Int24_MAX_VALUE.toFloat()).toInt())
                            if (!inputBuffer.hasRemaining()) {
                                break
                            }
                        }
                    }
                }
                else -> {
                    // No op
                }
            }
            inputBuffer.position(inputBuffer.limit())
            buffer.flip()
        } else {
            val remaining = inputBuffer.remaining()
            if (remaining == 0) {
                return
            }
            replaceOutputBuffer(remaining).put(inputBuffer).flip()
        }
    }
}
