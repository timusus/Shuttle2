package com.simplecityapps.playback.spec

import android.media.AudioTrack
import androidx.media3.common.util.Clock
import kotlin.math.min
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.RealObject
import org.robolectric.annotation.Resetter
import org.robolectric.shadows.ShadowAudioTrack
import org.robolectric.util.reflector.Direct
import org.robolectric.util.reflector.ForType
import org.robolectric.util.reflector.Reflector.reflector

/**
 * Robolectric's AudioTrack, whose playback head moves on with [clock], as a real track's moves on in real time: at the
 * track's sample rate while it plays (and, once stopped, until it has played out what it was given), never past what
 * it's been written, and not at all while paused. Robolectric's own head is simply the frames written, so while a
 * track played (before the sink stopped it at the end of the queue) the sink's position jumped to whatever the
 * renderer had written, which depended on how far the loading thread had got in wall time (#551).
 *
 * Without a [clock] (outside a [PlaybackHarness]), the head is the frames written, as Robolectric's is.
 */
@Implements(AudioTrack::class)
class ClockedShadowAudioTrack : ShadowAudioTrack() {
    @RealObject
    private lateinit var track: AudioTrack

    /** Whether the head moves on: from [play] until [pause]. A stopped track plays out what it has. */
    private var running = false

    private var playedFrames = 0L

    /** The [clock] time the head was last moved on to. */
    private var headTimeMs = 0L

    /** How many times the track has been flushed, dropping what it had yet to play. */
    @get:Synchronized
    var flushes = 0
        private set

    @Implementation
    @Synchronized
    override fun getPlaybackHeadPosition(): Int {
        if (clock == null) return super.getPlaybackHeadPosition()
        moveHead()
        return playedFrames.toInt()
    }

    @Implementation
    @Synchronized
    override fun play() {
        moveHead()
        super.play()
        running = true
    }

    @Implementation
    @Synchronized
    fun pause() {
        moveHead()
        reflector(AudioTrackReflector::class.java, track).pause()
        running = false
    }

    @Implementation
    @Synchronized
    fun stop() {
        moveHead()
        reflector(AudioTrackReflector::class.java, track).stop()
    }

    @Implementation
    @Synchronized
    override fun flush() {
        super.flush()
        playedFrames = 0
        flushes++
    }

    /** Plays on from [headTimeMs] to now, up to the frames written: a track that runs out of audio stalls until it gets more. */
    private fun moveHead() {
        val nowMs = clock?.elapsedRealtime() ?: return
        if (running) {
            val sampleRate = track.sampleRate.toLong()
            val frames = nowMs * sampleRate / 1000 - headTimeMs * sampleRate / 1000
            playedFrames = min(playedFrames + frames, super.getPlaybackHeadPosition().toLong())
        }
        headTimeMs = nowMs
    }

    @ForType(AudioTrack::class)
    private interface AudioTrackReflector {
        @Direct
        fun pause()

        @Direct
        fun stop()
    }

    companion object {
        /** The clock every AudioTrack's head moves on with, or null for Robolectric's head (the frames written). */
        @Volatile
        var clock: Clock? = null

        @Resetter
        @JvmStatic
        fun reset() {
            clock = null
            ShadowAudioTrack.resetTest()
        }
    }
}
