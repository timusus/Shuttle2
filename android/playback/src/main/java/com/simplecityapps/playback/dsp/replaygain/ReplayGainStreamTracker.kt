package com.simplecityapps.playback.dsp.replaygain

import com.google.android.exoplayer2.Player

/**
 * Works out which playlist item the audio flowing through [ReplayGainAudioProcessor] belongs to,
 * so each track's gain is in effect from its first sample.
 *
 * ExoPlayer decodes ahead of what is audible: by the time a track transition reaches the app, the
 * start of the next track has already been through the audio processors. So the gain can't follow
 * transition callbacks. Instead it follows the audio sink, which reports two kinds of boundary:
 *
 * - A restart (flush or reset, after a load or a seek): the audio that follows comes from the
 *   playing item.
 * - A reconfigure while a stream is already flowing: the renderer has moved on to the next item in
 *   the playlist. The sink drains the old stream through the processors, then flushes them before
 *   queuing the new stream's first buffer, so the switch happens in [onProcessorFlushed].
 *
 * The playlist and playing index are written on the main thread; sink events arrive on the playback
 * thread.
 */
class ReplayGainStreamTracker {
    /** The ReplayGain of each item in the player's playlist, in playlist order. */
    private var playlist: List<ReplayGain?> = emptyList()

    private var repeatMode: Int = Player.REPEAT_MODE_OFF

    private var playingIndex = 0

    /** The playlist index of the audio currently being fed through the processors. */
    private var fedIndex = 0

    /** Whether the sink has been handed audio since it was last restarted. */
    private var streamStarted = false

    /** Whether the sink has been reconfigured for the next item, awaiting the processor flush. */
    private var nextStreamPending = false

    @Synchronized
    fun setPlaylist(playlist: List<ReplayGain?>) {
        this.playlist = playlist
    }

    /**
     * Called when the player starts playing the item at [index]. Normally the sink has already
     * moved on to it, so this is a no-op; it also resynchronises if the two ever disagree.
     */
    @Synchronized
    fun setPlayingIndex(index: Int) {
        playingIndex = index
        fedIndex = index
    }

    /**
     * @param repeatMode one of the [Player] repeat modes
     */
    @Synchronized
    fun setRepeatMode(repeatMode: Int) {
        this.repeatMode = repeatMode
    }

    @Synchronized
    fun onSinkRestarted() {
        fedIndex = playingIndex
        streamStarted = false
        nextStreamPending = false
    }

    @Synchronized
    fun onSinkConfigured() {
        if (streamStarted) {
            nextStreamPending = true
        }
    }

    @Synchronized
    fun onSinkBufferHandled() {
        streamStarted = true
    }

    @Synchronized
    fun onProcessorFlushed() {
        if (nextStreamPending) {
            fedIndex = nextIndex(fedIndex)
            nextStreamPending = false
        }
    }

    /** The ReplayGain of the item currently being fed through the processors, if known. */
    @Synchronized
    fun currentReplayGain(): ReplayGain? = playlist.getOrNull(fedIndex)

    private fun nextIndex(index: Int): Int = when {
        repeatMode == Player.REPEAT_MODE_ONE -> index
        index + 1 < playlist.size -> index + 1
        repeatMode == Player.REPEAT_MODE_ALL -> 0
        else -> index + 1
    }
}
