package com.simplecityapps.playback.dsp.replaygain

import androidx.core.math.MathUtils.clamp
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.audio.AudioProcessor
import com.google.android.exoplayer2.audio.AudioProcessor.UnhandledAudioFormatException
import com.google.android.exoplayer2.audio.BaseAudioProcessor
import com.simplecityapps.playback.dsp.equalizer.fromDb
import com.simplecityapps.playback.exoplayer.ByteUtils.Int24_MAX_VALUE
import com.simplecityapps.playback.exoplayer.ByteUtils.Int24_MIN_VALUE
import com.simplecityapps.playback.exoplayer.ByteUtils.getInt24
import com.simplecityapps.playback.exoplayer.ByteUtils.putInt24
import com.simplecityapps.playback.exoplayer.ExoPlayerPlayback
import timber.log.Timber
import java.nio.ByteBuffer

class ReplayGainAudioProcessor(var mode: ReplayGainMode, var preAmpGain: Double = 0.0) : BaseAudioProcessor() {
    var trackGain: Double? = null
        @Synchronized get

        @Synchronized set

    var albumGain: Double? = null
        @Synchronized get

        @Synchronized set

    // Reference to player to query current MediaItem
    var player: Player? = null

    private val gain: Double
        get() {
            // Try to get gain from current MediaItem's tag first
            player?.currentMediaItem?.localConfiguration?.tag?.let { tag ->
                if (tag is ExoPlayerPlayback.ReplayGainTag) {
                    val itemTrackGain = tag.trackGain
                    val itemAlbumGain = tag.albumGain
                    return preAmpGain +
                        when (mode) {
                            ReplayGainMode.Track -> itemTrackGain ?: itemAlbumGain ?: 0.0
                            ReplayGainMode.Album -> itemAlbumGain ?: itemTrackGain ?: 0.0
                            ReplayGainMode.Off -> 0.0
                        }
                }
            }

            // Fall back to manually set values
            return preAmpGain +
                when (mode) {
                    ReplayGainMode.Track -> trackGain ?: albumGain ?: 0.0
                    ReplayGainMode.Album -> albumGain ?: trackGain ?: 0.0
                    ReplayGainMode.Off -> 0.0
                }
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
