package com.simplecityapps.playback.dsp.replaygain

import androidx.core.math.MathUtils.clamp
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.audio.AudioProcessor
import com.google.android.exoplayer2.audio.AudioProcessor.UnhandledAudioFormatException
import com.google.android.exoplayer2.audio.BaseAudioProcessor
import com.simplecityapps.playback.dsp.equalizer.fromDb
import com.simplecityapps.playback.exoplayer.ByteUtils.Int24_MAX_VALUE
import com.simplecityapps.playback.exoplayer.ByteUtils.Int24_MIN_VALUE
import com.simplecityapps.playback.exoplayer.ByteUtils.getInt24
import com.simplecityapps.playback.exoplayer.ByteUtils.putInt24
import java.nio.ByteBuffer

/**
 * Audio processor that applies ReplayGain volume normalization.
 *
 * Thread Safety:
 * - trackGain and albumGain are updated from the main thread (via setReplayGain)
 * - gain getter is accessed from the audio rendering thread (in queueInput)
 * - @Synchronized ensures thread-safe access
 *
 * Note on Timing:
 * During gapless transitions, there may be a brief period (a few audio buffers, ~10-50ms)
 * where new track samples are processed with the previous track's gain before the update
 * takes effect. This is a fundamental limitation of the async nature of Player.Listener
 * callbacks vs continuous audio rendering. In practice, this is imperceptible compared
 * to the original issue where the delay was ~500ms.
 */
class ReplayGainAudioProcessor(var mode: ReplayGainMode, var preAmpGain: Double = 0.0) : BaseAudioProcessor() {
    @Volatile
    var trackGain: Double? = null
        @Synchronized get
        @Synchronized set

    @Volatile
    var albumGain: Double? = null
        @Synchronized get
        @Synchronized set

    private val gain: Double
        @Synchronized get() =
            preAmpGain +
                when (mode) {
                    ReplayGainMode.Track -> trackGain ?: albumGain ?: 0.0
                    ReplayGainMode.Album -> albumGain ?: trackGain ?: 0.0
                    ReplayGainMode.Off -> 0.0
                }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT &&
            inputAudioFormat.encoding != C.ENCODING_PCM_24BIT
        ) {
            throw UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (gain != 0.0) {
            val size = inputBuffer.remaining()
            val buffer = replaceOutputBuffer(size)
            val delta = gain.fromDb()
            when (outputAudioFormat.encoding) {
                C.ENCODING_PCM_16BIT -> {
                    while (inputBuffer.hasRemaining()) {
                        val sample = inputBuffer.short
                        val targetSample = clamp((sample * delta), Short.MIN_VALUE.toDouble(), Short.MAX_VALUE.toDouble()).toInt().toShort()
                        buffer.putShort(targetSample)
                    }
                }

                C.ENCODING_PCM_24BIT -> {
                    while (inputBuffer.hasRemaining()) {
                        val sample = inputBuffer.getInt24()
                        val targetSample = clamp(sample * delta, Int24_MIN_VALUE.toDouble(), Int24_MAX_VALUE.toDouble()).toInt()
                        buffer.putInt24(targetSample)
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

    companion object {
        const val maxPreAmpGain = 12
    }
}
