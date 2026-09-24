package com.simplecityapps.playback.exoplayer

import com.simplecityapps.playback.OutputFormat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The format of the AudioTrack the local player last opened, as its audio sink reports it, and a way to make
 * the player open a new one. Main thread only.
 *
 * [format] keeps the last format once the player is released, so the next track in the same format can open
 * straight onto whatever output was chosen for it.
 */
class AudioTrackMonitor {
    /** Something that has an AudioTrack open and can open a new one: the current player. */
    fun interface Owner {
        fun reopenAudioTrack()
    }

    private val _format = MutableStateFlow<OutputFormat?>(null)
    val format: StateFlow<OutputFormat?> = _format.asStateFlow()

    private var owner: Owner? = null

    fun onAudioTrackInitialized(
        owner: Owner,
        format: OutputFormat
    ) {
        this.owner = owner
        _format.value = format
    }

    /** [owner] is released: it has no track to reopen. */
    fun onReleased(owner: Owner) {
        if (this.owner === owner) {
            this.owner = null
        }
    }

    /**
     * Makes the current player open a new AudioTrack, which picks up a change to the output it's routed to
     * (the track in use keeps the output it was opened on). A no-op without a player.
     */
    fun reopenAudioTrack() {
        owner?.reopenAudioTrack()
    }
}
