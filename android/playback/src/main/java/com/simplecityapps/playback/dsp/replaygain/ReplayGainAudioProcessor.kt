package com.simplecityapps.playback.dsp.replaygain

import androidx.core.math.MathUtils.clamp
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import androidx.media3.common.audio.BaseAudioProcessor
import com.simplecityapps.playback.dsp.equalizer.fromDb
import com.simplecityapps.playback.exoplayer.ByteUtils.Int24_MAX_VALUE
import com.simplecityapps.playback.exoplayer.ByteUtils.Int24_MIN_VALUE
import com.simplecityapps.playback.exoplayer.ByteUtils.getInt24
import com.simplecityapps.playback.exoplayer.ByteUtils.putInt24
import java.nio.ByteBuffer

/**
 * Applies ReplayGain. The gain follows [streamTracker], which switches it at the boundary between
 * one track's audio and the next, so each track plays at its own level from the first sample.
 *
 * [mode] and [preAmpGain] are set from the main thread (settings) and read on the playback thread,
 * so they're volatile: a change reaches the audio on the next buffer.
 */
class ReplayGainAudioProcessor(
    @Volatile var mode: ReplayGainMode,
    @Volatile var preAmpGain: Double = 0.0,
    val streamTracker: ReplayGainStreamTracker = ReplayGainStreamTracker()
) : BaseAudioProcessor() {
    private val gain: Double
        get() = replayGainDb(mode, preAmpGain, streamTracker.currentReplayGain())

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT &&
            inputAudioFormat.encoding != C.ENCODING_PCM_24BIT
        ) {
            throw UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        // Read the gain once per buffer - the mode, pre-amp and stream can all change underneath us.
        val currentGain = gain
        if (currentGain != 0.0) {
            val size = inputBuffer.remaining()
            val buffer = replaceOutputBuffer(size)
            val delta = currentGain.fromDb()
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

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        // The sink flushes its processors between one stream and the next, after the old stream
        // has drained and before the new stream's first buffer is queued.
        streamTracker.onProcessorFlushed()
    }

    companion object {
        const val maxPreAmpGain = 12
    }
}
