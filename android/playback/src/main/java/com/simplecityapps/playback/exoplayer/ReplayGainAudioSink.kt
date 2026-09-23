package com.simplecityapps.playback.exoplayer

import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.AudioSink.AudioSinkConfig
import androidx.media3.exoplayer.audio.ForwardingAudioSink
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
    override fun configure(audioSinkConfig: AudioSinkConfig) {
        tracker.onSinkConfigured()
        super.configure(audioSinkConfig)
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

    override fun reset() {
        tracker.onSinkRestarted()
        super.reset()
    }
}
