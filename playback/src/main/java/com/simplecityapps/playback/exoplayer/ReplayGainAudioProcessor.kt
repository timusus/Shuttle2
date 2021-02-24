package com.simplecityapps.playback.exoplayer

import androidx.core.math.MathUtils.clamp
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.audio.AudioProcessor
import com.google.android.exoplayer2.audio.AudioProcessor.UnhandledAudioFormatException
import com.google.android.exoplayer2.audio.BaseAudioProcessor
import com.simplecityapps.playback.equalizer.fromDb
import com.simplecityapps.playback.exoplayer.ByteUtils.Int24_MAX_VALUE
import com.simplecityapps.playback.exoplayer.ByteUtils.Int24_MIN_VALUE
import com.simplecityapps.playback.exoplayer.ByteUtils.getInt24
import com.simplecityapps.playback.exoplayer.ByteUtils.putInt24
import timber.log.Timber
import java.nio.ByteBuffer

class ReplayGainAudioProcessor(mode: Mode) : BaseAudioProcessor() {

    enum class Mode {
        Track, Album, Off;

        companion object {
            fun init(ordinal: Int): Mode {
                return when (ordinal) {
                    Track.ordinal -> Track
                    Album.ordinal -> Album
                    Off.ordinal -> Off
                    else -> Off
                }
            }
        }
    }

    var trackGain: Double? = null
    var albumGain: Double? = null

    var mode: Mode = mode
        set(value) {
            field = value
            Timber.v("Replay gain mode: $value")
        }

    private val gain: Double
        get() = when (mode) {
            Mode.Track -> trackGain ?: albumGain ?: 0.0
            Mode.Album -> albumGain ?: trackGain ?: 0.0
            Mode.Off -> 0.0
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
}