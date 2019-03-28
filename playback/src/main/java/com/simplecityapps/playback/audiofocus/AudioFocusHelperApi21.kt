package com.simplecityapps.playback.audiofocus

import android.content.Context
import android.media.AudioManager
import com.simplecityapps.playback.PlaybackWatcher

@Suppress("DEPRECATION")
class AudioFocusHelperApi21(context: Context, playbackWatcher: PlaybackWatcher) : AudioFocusHelperBase(context, playbackWatcher) {

    override fun requestAudioFocus(): Boolean {
        val result = audioManager?.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            return true
        }
        return false
    }

    override fun abandonAudioFocus() {
        audioManager?.abandonAudioFocus(this)
    }
}