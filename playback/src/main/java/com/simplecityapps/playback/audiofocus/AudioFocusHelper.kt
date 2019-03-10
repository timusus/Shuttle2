package com.simplecityapps.playback.audiofocus

interface AudioFocusHelper {

    /**
     * @return false if the focus request was denied
     */
    fun requestAudioFocus(): Boolean

    fun abandonAudioFocus()

    var listener: Listener?

    interface Listener {

        fun restoreVolumeAndplay()

        fun pause()

        fun duck()
    }
}