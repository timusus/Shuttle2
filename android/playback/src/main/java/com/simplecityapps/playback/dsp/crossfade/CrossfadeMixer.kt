package com.simplecityapps.playback.dsp.crossfade

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import com.simplecityapps.playback.dsp.crossfade.CrossfadePlan.Next
import com.simplecityapps.playback.dsp.mediaItem
import com.simplecityapps.playback.queue.queueEntryOrNull
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Crossfades between queue entries on the one player: each entry's item is clipped to end the crossfade's length early
 * (see [CrossfadeClippingMediaSourceFactory]), and this plays the [Tail] it cut off, decoded ahead of time, over the
 * head of the next entry, on equal-power curves.
 *
 * It follows the audio sink's stream boundaries. At a gapless transition the sink queues the end of the ending stream,
 * configures the processors for the next, and flushes them with the next stream's [AudioProcessor.StreamMetadata]; a
 * seek or skip flushes without an end of stream first. So:
 * - At the end of a stream whose entry has a plan in [plans], it looks at [CrossfadePlan.next]: [Next.MixInto] holds
 *   the tail back for the next stream (if a next stream has been configured, in the tail's format), [Next.FadeOut]
 *   plays it out fading, and [Next.Join] plays it out unfaded.
 * - A flush straight after that end of stream, for the entry the tail was held for, starts mixing it into the new
 *   stream from where the ending stream stopped. Any other flush drops the tail: a seek or skip cuts hard.
 *
 * Clipping isn't sample-accurate (a stream stops at the end of the access unit that crosses the clip point), so this
 * counts the frames of each stream from the position its flush gives, drops any past the clip end, and plays the tail
 * from the frame the stream actually stopped at: nothing repeats or goes missing.
 *
 * [plans] is set on the main thread and read on the playback thread.
 */
class CrossfadeMixer : BaseAudioProcessor() {
    /**
     * The plans for the entries whose tails are ready, by entry uid. Keyed by entry, not just the current one's, since
     * the sink runs ahead of the player's position: it can reach the end of the next entry before the player moves on.
     */
    @Volatile
    var plans: Map<Long, CrossfadePlan> = emptyMap()

    // Only touched on the playback thread.

    /** The entry the current stream plays, or null when the last flush didn't identify one. */
    private var streamUid: Long? = null

    /** The window position of the stream's next input frame. */
    private var streamFrame = 0L

    /** Whether the sink configured the processors (for the next stream, at a transition) since the last flush. */
    private var configuredSinceFlush = false

    /** The format configured since the last flush: the next stream's, at a transition. */
    private var configuredFormat: AudioProcessor.AudioFormat = AudioProcessor.AudioFormat.NOT_SET

    private var endedBeforeFlush = false

    private class Held(
        val tail: Tail,
        val index: Int,
        val nextUid: Long
    )

    /** A tail waiting for the next stream's flush. */
    private var held: Held? = null

    /** Mixes [tail] from frame [index] into the stream's first [length] frames. */
    private class Fade(
        val tail: Tail,
        val index: Int
    ) {
        val length = tail.frameCount - index
        var position = 0
    }

    private var fade: Fade? = null

    /** The rest of a tail played out at the end of a stream, emitted once the stream's own output is taken. */
    private var playout: ByteBuffer? = null

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        inputAudioFormat.requirePcm16Or24()
        configuredSinceFlush = true
        configuredFormat = inputAudioFormat
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val format = inputAudioFormat
        val frameSize = format.bytesPerFrame
        val frames = inputBuffer.remaining() / frameSize
        if (frames == 0) return
        // Frames past the clip end are played from the tail instead.
        val tail = planForStream()?.tail
        val kept = if (tail == null) frames else (tail.clipEndFrame - streamFrame).coerceIn(0, frames.toLong()).toInt()
        val output = replaceOutputBuffer(kept * frameSize)
        val fade = fade
        if (fade == null) {
            val limit = inputBuffer.limit()
            inputBuffer.limit(inputBuffer.position() + kept * frameSize)
            output.put(inputBuffer)
            inputBuffer.limit(limit)
        } else {
            val channels = format.channelCount
            repeat(kept) {
                if (fade.position < fade.length) {
                    val angle = PI / 2 * fade.position / fade.length
                    val headGain = sin(angle).toFloat()
                    val tailGain = cos(angle).toFloat()
                    val tailOffset = (fade.index + fade.position) * channels
                    for (channel in 0 until channels) {
                        val head = inputBuffer.getSample(format.encoding)
                        output.putSample(format.encoding, head * headGain + fade.tail.samples[tailOffset + channel] * tailGain)
                    }
                    fade.position++
                } else {
                    for (channel in 0 until channels) output.putSample(format.encoding, inputBuffer.getSample(format.encoding))
                }
            }
            if (fade.position >= fade.length) this.fade = null
        }
        inputBuffer.position(inputBuffer.limit())
        streamFrame += frames
        output.flip()
    }

    override fun onQueueEndOfStream() {
        // The pipeline queues the end of stream again on every pass until this processor has ended.
        if (endedBeforeFlush) return
        endedBeforeFlush = true
        val plan = planForStream() ?: return
        val tail = plan.tail
        // A drain short of the clip end (a speed change, a discontinuity) isn't the entry ending.
        if (streamFrame < tail.clipEndFrame - inputAudioFormat.sampleRate / 100) return
        // The stream's output stopped at the clip end, if the stream overshot it.
        val index = minOf(streamFrame, tail.clipEndFrame) - tail.startFrame
        if (index < 0 || index >= tail.frameCount) return
        when (val next = plan.next) {
            is Next.MixInto ->
                when {
                    // The queue ends here after all (the next item isn't being played gaplessly).
                    !configuredSinceFlush -> playout = playout(tail, index.toInt(), fadeOut = true)

                    // Mixing streams of different rates or channel counts needs resampling first.
                    !tail.matches(configuredFormat) -> playout = playout(tail, index.toInt(), fadeOut = false)

                    else -> held = Held(tail, index.toInt(), next.entryUid)
                }

            Next.FadeOut -> playout = playout(tail, index.toInt(), fadeOut = true)

            Next.Join -> playout = playout(tail, index.toInt(), fadeOut = false)
        }
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        val uid = streamMetadata.mediaItem()?.queueEntryOrNull?.uid
        val held = held
        this.held = null
        val continuing = endedBeforeFlush && uid != null && uid == streamUid
        fade =
            when {
                endedBeforeFlush && held != null && uid == held.nextUid && held.tail.matches(inputAudioFormat) -> Fade(held.tail, held.index)

                // The sink drained mid-stream (a speed change, a discontinuity) and carries on with the same stream.
                continuing -> fade

                else -> null
            }
        playout = null
        streamUid = uid
        val offsetUs = streamMetadata.positionOffsetUs
        streamFrame = if (offsetUs == C.TIME_UNSET) 0 else offsetUs.coerceAtLeast(0).usToFrames(inputAudioFormat.sampleRate)
        endedBeforeFlush = false
        configuredSinceFlush = false
    }

    override fun onReset() {
        streamUid = null
        streamFrame = 0
        configuredSinceFlush = false
        configuredFormat = AudioProcessor.AudioFormat.NOT_SET
        endedBeforeFlush = false
        held = null
        fade = null
        playout = null
    }

    override fun getOutput(): ByteBuffer {
        val output = super.getOutput()
        if (output.hasRemaining()) return output
        val playout = playout ?: return output
        this.playout = null
        return playout
    }

    override fun isEnded(): Boolean = super.isEnded() && playout == null

    private fun planForStream(): CrossfadePlan? = streamUid?.let { plans[it] }?.takeIf { it.tail.matches(inputAudioFormat) }

    private fun playout(
        tail: Tail,
        index: Int,
        fadeOut: Boolean
    ): ByteBuffer {
        val format = inputAudioFormat
        val frames = tail.frameCount - index
        val buffer = ByteBuffer.allocateDirect(frames * format.bytesPerFrame).order(ByteOrder.nativeOrder())
        for (frame in 0 until frames) {
            val gain = if (fadeOut) cos(PI / 2 * frame / frames).toFloat() else 1f
            val offset = (index + frame) * format.channelCount
            for (channel in 0 until format.channelCount) buffer.putSample(format.encoding, tail.samples[offset + channel] * gain)
        }
        buffer.flip()
        return buffer
    }
}
