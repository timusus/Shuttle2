package com.simplecityapps.playback.exoplayer

import com.google.android.exoplayer2.Format
import com.google.android.exoplayer2.audio.AudioSink
import com.google.android.exoplayer2.audio.ForwardingAudioSink
import com.simplecityapps.playback.dsp.replaygain.ReplayGainStreamTracker
import java.nio.ByteBuffer

/**
 * Reports stream boundaries to [ReplayGainStreamTracker], so the ReplayGain processor can switch
 * gain exactly where one track's audio ends and the next begins.
 */
class ReplayGainAudioSink(
    sink: AudioSink,
    private val tracker: ReplayGainStreamTracker
) : ForwardingAudioSink(sink) {
    override fun configure(
        inputFormat: Format,
        specifiedBufferSize: Int,
        outputChannels: IntArray?
    ) {
        tracker.onSinkConfigured()
        super.configure(inputFormat, specifiedBufferSize, outputChannels)
    }

    override fun handleBuffer(
        buffer: ByteBuffer,
        presentationTimeUs: Long,
        encodedAccessUnitCount: Int
    ): Boolean {
        tracker.onSinkBufferHandled()
        return super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
    }

    override fun flush() {
        tracker.onSinkRestarted()
        super.flush()
    }

    override fun experimentalFlushWithoutAudioTrackRelease() {
        tracker.onSinkRestarted()
        super.experimentalFlushWithoutAudioTrackRelease()
    }

    override fun reset() {
        tracker.onSinkRestarted()
        super.reset()
    }
}
