package com.simplecityapps.shuttle.debug.crossfadetap

import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicReference

/**
 * Passes audio through unchanged while, once [start]ed, appending it to a WAV file. It sits straight after the
 * CrossfadeMixer, so the file is what the mixer output: the fades and joins, before the equalizer. The file takes the
 * format of the first buffer after [start]; a later stream in a different format is left out and counted in
 * [StopResult.skippedBytes], as the WAV has one format.
 */
class WavTapAudioProcessor : BaseAudioProcessor() {
    class StopResult(
        val file: File,
        val dataBytes: Long,
        val skippedBytes: Long,
        val sampleRate: Int,
        val channelCount: Int,
        val bitsPerSample: Int
    )

    private class Recording(val file: File) {
        var writer: WavWriter? = null
        var skippedBytes = 0L
    }

    private val recording = AtomicReference<Recording?>(null)

    val isRecording: Boolean get() = recording.get() != null

    /** Records to [file] from the next buffer on. Throws if already recording. */
    fun start(file: File) {
        check(recording.compareAndSet(null, Recording(file))) { "already recording" }
    }

    /** Ends the recording and finalises the file; null if nothing was recording or no audio arrived. */
    @Synchronized
    fun stop(): StopResult? {
        val recording = recording.getAndSet(null) ?: return null
        val writer = recording.writer ?: return null
        writer.close()
        return StopResult(recording.file, writer.dataBytes, recording.skippedBytes, writer.sampleRate, writer.channelCount, writer.bitsPerSample)
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_24BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    @Synchronized
    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        recording.get()?.let { record(it, inputBuffer.duplicate()) }
        replaceOutputBuffer(size).put(inputBuffer).flip()
    }

    private fun record(
        recording: Recording,
        copy: ByteBuffer
    ) {
        val format = inputAudioFormat
        val bits = if (format.encoding == C.ENCODING_PCM_24BIT) 24 else 16
        val writer = recording.writer ?: try {
            recording.file.parentFile?.mkdirs()
            WavWriter(recording.file, format.sampleRate, format.channelCount, bits).also { recording.writer = it }
        } catch (e: Exception) {
            Log.e("S2Debug", "WAV tap: can't open ${recording.file}", e)
            this.recording.compareAndSet(recording, null)
            return
        }
        if (writer.sampleRate == format.sampleRate && writer.channelCount == format.channelCount && writer.bitsPerSample == bits) {
            writer.write(copy)
        } else {
            recording.skippedBytes += copy.remaining()
        }
    }

    override fun onReset() {
        stop()
    }
}
