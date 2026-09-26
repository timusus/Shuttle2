package com.simplecityapps.playback.dsp.crossfade

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import com.simplecityapps.playback.exoplayer.ByteUtils.getInt24
import com.simplecityapps.playback.exoplayer.ByteUtils.putInt24
import java.nio.ByteBuffer
import kotlin.math.roundToInt

/**
 * The last seconds of a queue entry's audio, decoded ahead of time by [TailDecoder]: the part its clipped item doesn't
 * play, plus a margin before it. [CrossfadeMixer] plays it from wherever the clipped item actually stopped.
 */
class Tail(
    val entryUid: Long,
    val sampleRate: Int,
    val channelCount: Int,
    /** The window position of the first frame, in µs. */
    val startUs: Long,
    /** Where the entry's clipped item ends, in µs. */
    val clipEndUs: Long,
    /** Interleaved, scaled to [-1, 1], with the entry's ReplayGain applied. */
    val samples: FloatArray,
    /** The wall time the decode took. */
    val decodeNanos: Long
) {
    val frameCount: Int get() = samples.size / channelCount

    val startFrame: Long get() = startUs.usToFrames(sampleRate)

    val clipEndFrame: Long get() = clipEndUs.usToFrames(sampleRate)

    /** How many times faster than real time the decode ran. */
    val decodeSpeed: Double get() = frameCount * 1e9 / sampleRate / decodeNanos.coerceAtLeast(1)

    fun matches(format: AudioProcessor.AudioFormat) = format.sampleRate == sampleRate && format.channelCount == channelCount
}

/** What [CrossfadeMixer] does with [tail] when its entry ends. */
class CrossfadePlan(
    val tail: Tail,
    val next: Next
) {
    sealed interface Next {
        /** Mixes the tail into the head of the entry that follows, [entryUid], if the player moves on to it gaplessly. */
        data class MixInto(val entryUid: Long) : Next

        /** Plays the tail out, fading, as the queue ends. */
        data object FadeOut : Next

        /** Plays the tail out unfaded, as if the item hadn't been clipped: no crossfade into what follows. */
        data object Join : Next
    }
}

/**
 * The frame at or before this position: a seek to it starts there (a WAV seek lands on the frame before, and the sink
 * reports the seek position as the stream's), so a tail decoded from a position starts at this frame.
 */
internal fun Long.usToFrames(sampleRate: Int): Long = this * sampleRate / 1_000_000

internal fun AudioProcessor.AudioFormat.requirePcm16Or24() {
    if (encoding != C.ENCODING_PCM_16BIT && encoding != C.ENCODING_PCM_24BIT) throw UnhandledAudioFormatException(this)
}

/** Reads one sample of [encoding] (16 or 24 bit PCM), scaled to [-1, 1]. */
internal fun ByteBuffer.getSample(encoding: Int): Float = when (encoding) {
    C.ENCODING_PCM_16BIT -> short / 32768f
    else -> getInt24() / 8388608f
}

/** Writes one sample of [encoding] (16 or 24 bit PCM), clamping it to full scale. */
internal fun ByteBuffer.putSample(
    encoding: Int,
    sample: Float
) {
    when (encoding) {
        C.ENCODING_PCM_16BIT -> putShort((sample * 32768f).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
        else -> putInt24((sample * 8388608f).roundToInt())
    }
}
