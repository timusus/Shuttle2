package com.simplecityapps.playback.audiofocus

import com.simplecityapps.playback.PlaybackState
import kotlinx.coroutines.flow.StateFlow

interface AudioFocusHelper {
    /**
     * @return false if the focus request was denied
     */
    fun requestAudioFocus(): Boolean

    fun abandonAudioFocus()

    var listener: Listener?

    var enabled: Boolean

    var resumeOnFocusGain: Boolean

    interface Listener {
        /** The listener's playback state, read when focus is lost to decide whether to resume on regaining it. */
        val playbackStateFlow: StateFlow<PlaybackState>

        fun restoreVolumeAndPlay()

        fun pause()

        fun duck()
    }
}
