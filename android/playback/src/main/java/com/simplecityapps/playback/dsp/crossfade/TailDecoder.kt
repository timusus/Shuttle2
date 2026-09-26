package com.simplecityapps.playback.dsp.crossfade

import android.media.AudioDeviceInfo
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioOutput
import androidx.media3.exoplayer.audio.AudioOutputProvider
import androidx.media3.exoplayer.audio.AudioTrackAudioOutputProvider
import androidx.media3.exoplayer.audio.ForwardingAudioOutputProvider
import com.simplecityapps.playback.dsp.replaygain.ReplayGainAudioProcessor
import com.simplecityapps.playback.queue.queueEntry
import java.nio.ByteBuffer
import timber.log.Timber

/**
 * Decodes the [Tail] of a queue entry, faster than real time, on a helper player that never outputs audio: it's built
 * by [newPlayer] from the playback player's renderers (so the FLAC and Opus extensions decode) and media sources (so
 * remote songs resolve their stream), around an audio sink whose output takes every buffer at once and counts it as
 * played. The sink runs the entry's ReplayGain, as the playback player's does, then [TailCaptureProcessor].
 *
 * One decode at a time, on the main thread.
 */
class TailDecoder(
    /** Builds a helper player whose audio sink runs these processors and outputs to [CapturingAudioOutputProvider]. */
    private val newPlayer: (Array<AudioProcessor>) -> ExoPlayer,
    /** The playback player's ReplayGain: its mode and pre-amp apply to the tail. */
    private val replayGain: ReplayGainAudioProcessor
) {
    private var player: ExoPlayer? = null

    /**
     * Decodes [item] from [marginMs] before [clipEndMs] to its end, then calls [onDecoded] with the tail, or with null
     * if it can't: an error, or a stream that can't seek.
     */
    fun decode(
        item: MediaItem,
        clipEndMs: Long,
        onDecoded: (Tail?) -> Unit,
        marginMs: Long = 500
    ) {
        cancel()
        val capture = TailCaptureProcessor()
        val gain = ReplayGainAudioProcessor(replayGain.mode, replayGain.preAmpGain).also { it.bypassed = replayGain.bypassed }
        val player = newPlayer(arrayOf(gain, capture))
        this.player = player
        val started = System.nanoTime()

        fun finish(tail: Tail?) {
            if (this.player !== player) return
            cancel()
            onDecoded(tail)
        }
        player.addListener(
            object : Player.Listener {
                override fun onTimelineChanged(
                    timeline: Timeline,
                    reason: Int
                ) {
                    val window = if (timeline.isEmpty) return else timeline.getWindow(0, Timeline.Window())
                    if (!window.isPlaceholder && !window.isSeekable) {
                        Timber.w("Crossfade: ${item.mediaId} can't seek, so it has no tail")
                        finish(null)
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState != Player.STATE_ENDED) return
                    val tail = capture.tail(item.queueEntry.uid, clipEndUs = clipEndMs * 1000, decodeNanos = System.nanoTime() - started)
                    tail?.let { Timber.d("Crossfade: decoded ${it.frameCount * 1000L / it.sampleRate} ms of ${item.mediaId} at ${"%.1f".format(it.decodeSpeed)}x real time") }
                    finish(tail)
                }

                override fun onPlayerError(error: PlaybackException) {
                    Timber.w(error, "Crossfade: couldn't decode the tail of ${item.mediaId}")
                    finish(null)
                }
            }
        )
        // The playback player's copy of the item may carry its crossfade clip; the tail is what that clip cuts off.
        val unclipped = item.buildUpon().setClippingConfiguration(MediaItem.ClippingConfiguration.UNSET).build()
        player.setMediaItem(unclipped, (clipEndMs - marginMs).coerceAtLeast(0))
        player.prepare()
        player.play()
    }

    /** Stops the decode in progress, if there is one, without reporting it. */
    fun cancel() {
        player?.release()
        player = null
    }
}

/**
 * The last processor in [TailDecoder]'s sink: copies what it passes through, from the position of the sink's last flush
 * (a seek, and the first buffer after it, flush; the tail is everything since).
 */
private class TailCaptureProcessor : BaseAudioProcessor() {
    private val lock = Any()
    private var startUs = 0L
    private var samples = FloatArray(0)
    private var sampleCount = 0

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        inputAudioFormat.requirePcm16Or24()
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        val encoding = inputAudioFormat.encoding
        val read = inputBuffer.duplicate().order(inputBuffer.order())
        synchronized(lock) {
            val count = size / (inputAudioFormat.bytesPerFrame / inputAudioFormat.channelCount)
            if (sampleCount + count > samples.size) samples = samples.copyOf(maxOf(samples.size * 2, sampleCount + count))
            repeat(count) { samples[sampleCount++] = read.getSample(encoding) }
        }
        replaceOutputBuffer(size).put(inputBuffer).flip()
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        synchronized(lock) {
            startUs = streamMetadata.positionOffsetUs.takeIf { it != C.TIME_UNSET } ?: 0
            sampleCount = 0
        }
    }

    fun tail(
        entryUid: Long,
        clipEndUs: Long,
        decodeNanos: Long
    ): Tail? = synchronized(lock) {
        val format = inputAudioFormat
        if (sampleCount == 0 || format == AudioProcessor.AudioFormat.NOT_SET) return null
        Tail(entryUid, format.sampleRate, format.channelCount, startUs, clipEndUs, samples.copyOf(sampleCount), decodeNanos)
    }
}

/**
 * Hands the sink a [CapturingAudioOutput] instead of an AudioTrack, and otherwise answers as the AudioTrack provider
 * does (with the default capabilities: PCM only).
 */
class CapturingAudioOutputProvider : ForwardingAudioOutputProvider(AudioTrackAudioOutputProvider.Builder(null).build()) {
    override fun getAudioOutput(config: AudioOutputProvider.OutputConfig): AudioOutput = CapturingAudioOutput(config)
}

/** Takes every write whole and counts it as played at once, so the player decodes as fast as it can. */
private class CapturingAudioOutput(private val config: AudioOutputProvider.OutputConfig) : AudioOutput {
    private val frameSize = Util.getPcmFrameSize(config.encoding, Integer.bitCount(config.channelMask))
    private var frames = 0L
    private var playbackParameters = PlaybackParameters.DEFAULT

    override fun play() = Unit

    override fun pause() = Unit

    override fun write(
        buffer: ByteBuffer,
        encodedAccessUnitCount: Int,
        presentationTimeUs: Long
    ): Boolean {
        frames += buffer.remaining() / frameSize
        buffer.position(buffer.limit())
        return true
    }

    override fun flush() {
        frames = 0
    }

    override fun stop() = Unit

    override fun release() = Unit

    override fun setVolume(volume: Float) = Unit

    override fun isOffloadedPlayback() = false

    override fun getAudioSessionId() = config.audioSessionId

    override fun getSampleRate() = config.sampleRate

    override fun getBufferSizeInFrames() = (config.bufferSize / frameSize).toLong()

    // Rounded up, so the sink never sees a frame it wrote as still pending.
    override fun getPositionUs() = (frames * 1_000_000 + config.sampleRate - 1) / config.sampleRate

    override fun getPlaybackParameters(): PlaybackParameters = playbackParameters

    override fun isStalled() = false

    override fun addListener(listener: AudioOutput.Listener) = Unit

    override fun removeListener(listener: AudioOutput.Listener) = Unit

    override fun setPlaybackParameters(playbackParams: PlaybackParameters) {
        playbackParameters = playbackParams
    }

    override fun setOffloadDelayPadding(
        delayInFrames: Int,
        paddingInFrames: Int
    ) = Unit

    override fun setOffloadEndOfStream() = Unit

    override fun attachAuxEffect(effectId: Int) = Unit

    override fun setAuxEffectSendLevel(level: Float) = Unit

    override fun setPreferredDevice(preferredDevice: AudioDeviceInfo?) = Unit
}
