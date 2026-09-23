package com.simplecityapps.playback.audiofocus

import android.content.Context
import android.media.AudioManager

@Suppress("DEPRECATION")
class AudioFocusHelperApi21(context: Context) : AudioFocusHelperBase(context) {
    override fun requestAudioFocus(): Boolean {
        if (!enabled) return true

        val result = audioManager?.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            return true
        }
        return false
    }

    override fun abandonAudioFocus() {
        if (!enabled) return

        audioManager?.abandonAudioFocus(this)
    }
}
