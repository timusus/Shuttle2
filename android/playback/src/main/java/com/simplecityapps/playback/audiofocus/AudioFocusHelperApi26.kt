package com.simplecityapps.playback.audiofocus

import android.annotation.TargetApi
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build

@TargetApi(Build.VERSION_CODES.O)
class AudioFocusHelperApi26(context: Context) : AudioFocusHelperBase(context) {
    private val focusRequest: AudioFocusRequest =
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder().run {
                    setUsage(AudioAttributes.USAGE_MEDIA)
                    setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    build()
                }
            )
            .setAcceptsDelayedFocusGain(true)
            .setOnAudioFocusChangeListener(this)
            .build()

    override fun requestAudioFocus(): Boolean {
        if (!enabled) return true

        return onFocusRequestResult(audioManager?.requestAudioFocus(focusRequest))
    }

    override fun abandonAudioFocus() {
        if (!enabled) return

        audioManager?.abandonAudioFocusRequest(focusRequest)
    }
}
