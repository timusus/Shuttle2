package com.simplecityapps.playback.dsp.replaygain

import androidx.core.math.MathUtils.clamp
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import androidx.media3.common.audio.BaseAudioProcessor
import com.simplecityapps.playback.dsp.equalizer.fromDb
import com.simplecityapps.playback.dsp.mediaItem
import com.simplecityapps.playback.dsp.windowIndex
import com.simplecityapps.playback.exoplayer.ByteUtils.Int24_MAX_VALUE
import com.simplecityapps.playback.exoplayer.ByteUtils.Int24_MIN_VALUE
import com.simplecityapps.playback.exoplayer.ByteUtils.getInt24
import com.simplecityapps.playback.exoplayer.ByteUtils.putInt24
import com.simplecityapps.playback.queue.QueueEntry
import com.simplecityapps.playback.queue.queueEntry
import com.simplecityapps.shuttle.model.Song
import java.nio.ByteBuffer
import timber.log.Timber

/**
 * Applies ReplayGain. Each stream's gain comes from the [ReplayGain] tag on its playlist item, which
 * the audio sink identifies when it flushes the processors before the stream's first buffer, so each
 * track plays at its own level from the first sample whichever renderer decoded it.
 *
 * [mode] and [preAmpGain] are set from the main thread (settings) and read on the playback thread,
 * so they're volatile: a change reaches the audio on the next buffer.
 */
class ReplayGainAudioProcessor(
    @Volatile var mode: ReplayGainMode,
    @Volatile var preAmpGain: Double = 0.0
) : BaseAudioProcessor() {
    /**
     * Applies no gain, whatever the mode and pre-amp, while [com.simplecityapps.playback.BitPerfectOutput] sends
     * audio to a USB DAC unchanged. Set on the main thread; applies from the next buffer.
     */
    @Volatile
    var bypassed: Boolean = false

    /** The ReplayGain of the stream being processed. Only touched on the playback thread. */
    private var streamReplayGain: ReplayGain? = null

    /**
     * Whether Track mode gives the movements of a work their album gain (see [replayGainModeAt]). Set from the main
     * thread (the Classical display setting); applies from the next stream.
     */
    @Volatile
    var albumGainForWorks: Boolean = false

    /** Whether the player shuffles, so a stream's neighbours are the ones in play order. Set from the main thread. */
    @Volatile
    var shuffled: Boolean = false

    /** Whether the stream being processed is a movement of a work, decided when it started. Only touched on the playback thread. */
    private var streamInWork = false

    private val gain: Double
        get() = replayGainDb(if (mode == ReplayGainMode.Track && streamInWork) ReplayGainMode.Album else mode, preAmpGain, streamReplayGain)

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
        val currentGain = if (bypassed) 0.0 else gain
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
        // The sink flushes its processors before each stream's first buffer: after a seek or
        // restart, and at a gapless transition once the previous stream has drained. A flush that
        // doesn't identify an item (a bare flush()) keeps the current stream's gain.
        val item = streamMetadata.mediaItem() ?: return
        streamReplayGain = (item.localConfiguration?.tag as? QueueEntry)?.replayGain
        streamInWork = albumGainForWorks && isWorkMovement(streamMetadata)
        Timber.d("ReplayGain for ${item.localConfiguration?.uri?.lastPathSegment}: $streamReplayGain, ${gain}dB")
    }

    override fun onReset() {
        streamReplayGain = null
        streamInWork = false
    }

    // Neighbours come from the player's timeline, not a copy of the queue.
    private fun isWorkMovement(streamMetadata: AudioProcessor.StreamMetadata): Boolean {
        val index = streamMetadata.windowIndex() ?: return false
        return isWorkMovementAt(streamMetadata.timeline, index, shuffled)
    }
}

/**
 * Whether the window at [index] is a movement of a work, judged over the windows around it in play order: playlist
 * order, or the shuffle order when [shuffled] (a shuffle by work keeps a work's movements together; a song shuffle
 * doesn't).
 */
internal fun isWorkMovementAt(
    timeline: Timeline,
    index: Int,
    shuffled: Boolean
): Boolean {
    val window = Timeline.Window()
    fun songAt(i: Int): Song = timeline.getWindow(i, window).mediaItem.queueEntry.song
    val before = ArrayDeque<Song>()
    var i = timeline.getPreviousWindowIndex(index, Player.REPEAT_MODE_OFF, shuffled)
    while (i != C.INDEX_UNSET) {
        before.addFirst(songAt(i))
        i = timeline.getPreviousWindowIndex(i, Player.REPEAT_MODE_OFF, shuffled)
    }
    val songs = before + songAt(index)
    i = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, shuffled)
    val queue = songs.toMutableList()
    while (i != C.INDEX_UNSET) {
        queue += songAt(i)
        i = timeline.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, shuffled)
    }
    return replayGainModeAt(ReplayGainMode.Track, queue, before.size) == ReplayGainMode.Album
}
