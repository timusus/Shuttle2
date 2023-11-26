package com.simplecityapps.playback.audiofocus

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
        fun restoreVolumeAndPlay()

        fun pause()

        fun duck()
    }
}
