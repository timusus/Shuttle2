package com.simplecityapps.playback.capture

import android.media.AudioFormat
import com.simplecityapps.playback.fakes.testSong
import com.simplecityapps.playback.spec.PlaybackHarness
import com.simplecityapps.shuttle.model.Song
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The audio [PlaybackHarness]'s AudioTracks were given, decoded: [samples] are interleaved, scaled to [-1, 1). The sink
 * always writes 16 bit PCM (float output is off), whatever the source's bit depth.
 */
class CapturedAudio(
    val sampleRate: Int,
    val channelCount: Int,
    val samples: DoubleArray
) {
    val frameCount: Int get() = samples.size / channelCount

    /**
     * The sink fades audio in over its first 20 ms after playback starts or resumes from a flush (a seek, a skip), so
     * those frames are quieter than the source.
     */
    val fadeInFrames: Int get() = sampleRate / 50

    /** One channel's samples, frames [from] until [to]. */
    fun channel(
        channel: Int = 0,
        from: Int = 0,
        to: Int = frameCount
    ): DoubleArray = DoubleArray(to - from) { samples[(from + it) * channelCount + channel] }
}

/** Runs [block] with this harness, then releases it. */
fun <T> PlaybackHarness.use(block: (PlaybackHarness) -> T): T = try {
    block(this)
} finally {
    release()
}

/** The audio heard so far. */
fun PlaybackHarness.capturedAudio(): CapturedAudio {
    val format = checkNotNull(audioOutputFormat) { "Nothing reached an AudioTrack" }
    check(format.encoding == AudioFormat.ENCODING_PCM_16BIT) { "Unexpected output encoding ${format.encoding}" }
    val buffer = ByteBuffer.wrap(audioOutput()).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
    return CapturedAudio(format.sampleRate, format.channelCount, DoubleArray(buffer.remaining()) { buffer.get() / 32768.0 })
}

/**
 * Queues [songs] from an empty queue, which starts them playing, runs [whilePlaying], then plays on to the end of the
 * last and returns the audio heard.
 */
fun PlaybackHarness.playToEnd(
    songs: List<Song>,
    whilePlaying: () -> Unit = {}
): CapturedAudio {
    val ended = record(playbackOperations.trackEndedFlow)
    run { playbackOperations.addToQueue(songs) }
    whilePlaying()
    runUntil { ended.lastOrNull() == songs.last() }
    return capturedAudio()
}

/** A PCM WAV file's contents; [samples] are interleaved, scaled to [-1, 1]. */
class Wav(
    val sampleRate: Int,
    val channelCount: Int,
    /** 16 or 24. */
    val bitsPerSample: Int,
    val samples: DoubleArray
) {
    val frameCount: Int get() = samples.size / channelCount

    /** [samples] as the sink writes them from a 16 bit source. */
    fun int16Samples(): DoubleArray = DoubleArray(samples.size) { (samples[it] * 32768).roundToLong().coerceIn(-32768, 32767) / 32768.0 }

    /** Frames [from] until [to] of this file, as a file of its own. */
    fun slice(
        from: Int,
        to: Int
    ) = Wav(sampleRate, channelCount, bitsPerSample, samples.copyOfRange(from * channelCount, to * channelCount))

    /** A song that plays this, written to a temporary file, tagged with these ReplayGain values. */
    fun song(
        id: Long,
        replayGainTrack: Double? = null,
        replayGainAlbum: Double? = null
    ): Song {
        val file = File.createTempFile("capture-$id", ".wav").apply { deleteOnExit() }
        file.writeBytes(bytes())
        return testSong(
            id = id,
            path = file.toURI().toString(),
            mimeType = "audio/wav",
            duration = (frameCount * 1000L / sampleRate).toInt(),
            replayGainTrack = replayGainTrack,
            replayGainAlbum = replayGainAlbum
        )
    }

    private fun bytes(): ByteArray {
        val bytesPerSample = bitsPerSample / 8
        val dataSize = samples.size * bytesPerSample
        val buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray()).putInt(36 + dataSize).put("WAVE".toByteArray())
        buffer.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(channelCount.toShort()).putInt(sampleRate)
        buffer.putInt(sampleRate * channelCount * bytesPerSample).putShort((channelCount * bytesPerSample).toShort()).putShort(bitsPerSample.toShort())
        buffer.put("data".toByteArray()).putInt(dataSize)
        val fullScale = 1L shl (bitsPerSample - 1)
        samples.forEach { sample ->
            val value = (sample * fullScale).roundToLong().coerceIn(-fullScale, fullScale - 1).toInt()
            when (bitsPerSample) {
                16 -> buffer.putShort(value.toShort())
                24 -> buffer.put(value.toByte()).put((value shr 8).toByte()).put((value shr 16).toByte())
                else -> error("Unsupported bit depth $bitsPerSample")
            }
        }
        return buffer.array()
    }

    companion object {
        /**
         * [frames] of a sum of sine tones, each a (frequency in Hz, peak amplitude) pair, the same on every channel. The
         * phase runs from frame 0, so slices of one tone join without a break.
         */
        fun tones(
            vararg tones: Pair<Double, Double>,
            frames: Int,
            sampleRate: Int = 44_100,
            channelCount: Int = 1,
            bitsPerSample: Int = 16
        ): Wav {
            val samples =
                DoubleArray(frames * channelCount) { index ->
                    val frame = index / channelCount
                    tones.sumOf { (frequency, amplitude) -> amplitude * sin(2 * PI * frequency * frame / sampleRate) }
                }
            return Wav(sampleRate, channelCount, bitsPerSample, samples)
        }
    }
}

fun DoubleArray.rms(): Double = sqrt(sumOf { it * it } / size)

fun Double.toDb(): Double = 20 * log10(this)

/**
 * The peak amplitude of the [frequency] component, by a single-bin DFT (Goertzel). Exact for a steady tone when the
 * window holds a whole number of its cycles.
 */
fun DoubleArray.amplitudeAt(
    frequency: Double,
    sampleRate: Int
): Double {
    val coefficient = 2 * cos(2 * PI * frequency / sampleRate)
    var previous = 0.0
    var beforePrevious = 0.0
    forEach { sample ->
        val current = sample + coefficient * previous - beforePrevious
        beforePrevious = previous
        previous = current
    }
    val power = previous * previous + beforePrevious * beforePrevious - coefficient * previous * beforePrevious
    return 2 * sqrt(power) / size
}

/** The largest step between neighbouring samples. */
fun DoubleArray.maxStep(): Double = (1 until size).maxOfOrNull { abs(this[it] - this[it - 1]) } ?: 0.0

/** The longest run of consecutive samples that are exactly zero. */
fun DoubleArray.longestSilence(): Int {
    var longest = 0
    var run = 0
    forEach { sample ->
        run = if (sample == 0.0) run + 1 else 0
        if (run > longest) longest = run
    }
    return longest
}
