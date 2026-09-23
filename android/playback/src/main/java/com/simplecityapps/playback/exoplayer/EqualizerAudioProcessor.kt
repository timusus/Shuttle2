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
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.pow
import timber.log.Timber

/** Lowest frequency considered when measuring the cascade's peak gain. Below this is inaudible. */
private const val ANALYSIS_MIN_FREQUENCY = 20.0

/** Number of log-spaced points between [ANALYSIS_MIN_FREQUENCY] and Nyquist used to find the peak. */
private const val ANALYSIS_POINT_COUNT = 512

class EqualizerAudioProcessor(enabled: Boolean) : BaseAudioProcessor() {
    var bandProcessors = emptyList<BandProcessor>()

    var preset: Equalizer.Presets.Preset = Equalizer.Presets.flat
        set(value) {
            field = value
            updateBandProcessors()
        }

    // Maximum allowed gain/cut for each band
    val maxBandGain = 12

    /**
     * Linear pre-attenuation applied to the filtered signal, equal to the inverse of the peak
     * magnitude of the whole band cascade's frequency response (or 1 when that peak doesn't exceed
     * unity).
     *
     * The ten peaking filters overlap, so their gains compound: with every band at +12 dB the
     * cascade peaks near +17.5 dB around 8 kHz, not +12 dB. Without this the equalizer leaves the
     * signal well past full scale and the hard clamp below flattens the waveform tops. Dividing by
     * the measured peak means no steady tone can come out louder than it went in, so the
     * ReplayGain stage that follows keeps the headroom it started with.
     *
     * Recomputed only when the preset or the audio format changes - never per buffer.
     */
    internal var attenuation: Float = 1f
        private set

    var enabled: Boolean = enabled
        set(value) {
            field = value

            Timber.v("Equalizer enabled: $value")
        }

    private fun updateBandProcessors() {
        if (outputAudioFormat.channelCount <= 0) {
            return
        }

        bandProcessors =
            preset.bands.map { band ->
                BandProcessor(
                    band.toNyquistBand(),
                    sampleRate = outputAudioFormat.sampleRate,
                    channelCount = outputAudioFormat.channelCount,
                    referenceGain = 0.0
                )
            }.toList()

        attenuation = calculateAttenuation(outputAudioFormat.sampleRate)
    }

    /**
     * Sweeps a log-spaced frequency grid, multiplying every band's magnitude response together to
     * find the cascade's peak gain, and returns its inverse when it exceeds unity. A flat or
     * cut-only preset can never exceed unity, so it returns 1 and the signal stays untouched.
     */
    private fun calculateAttenuation(sampleRate: Int): Float {
        val nyquist = sampleRate / 2.0
        if (bandProcessors.isEmpty() || nyquist <= ANALYSIS_MIN_FREQUENCY) {
            return 1f
        }

        var peak = 0.0
        val span = nyquist / ANALYSIS_MIN_FREQUENCY
        for (index in 0 until ANALYSIS_POINT_COUNT) {
            val frequency = ANALYSIS_MIN_FREQUENCY * span.pow(index.toDouble() / (ANALYSIS_POINT_COUNT - 1))
            val omega = 2.0 * PI * frequency / sampleRate
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
            val preAttenuation = attenuation

            when (outputAudioFormat.encoding) {
                C.ENCODING_PCM_16BIT -> {
                    while (inputBuffer.hasRemaining()) {
                        for (channelIndex in 0 until outputAudioFormat.channelCount) {
                            val sample = inputBuffer.short
                            var targetSample = sample.toFloat()
                            for (band in bandProcessors) {
                                targetSample = band.processSample(targetSample, channelIndex)
                            }
                            targetSample *= preAttenuation
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
                            targetSample *= preAttenuation
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
