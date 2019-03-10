package com.simplecityapps.playback.audiofocus

import android.annotation.TargetApi
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build

@TargetApi(Build.VERSION_CODES.O)
class AudioFocusHelperApi26(context: Context) : AudioFocusHelperBase(context) {

    private val focusRequest: AudioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(AudioAttributes.Builder().run {
            setUsage(AudioAttributes.USAGE_GAME)
            setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            build()
        })
        .setAcceptsDelayedFocusGain(true)
        .setOnAudioFocusChangeListener(this)
        .build()

    override fun requestAudioFocus(): Boolean {
        val result = audioManager?.requestAudioFocus(focusRequest)
        synchronized(focusLock) {
            when (result) {
                AudioManager.AUDIOFOCUS_REQUEST_FAILED -> playbackNowAuthorized = false
                AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> {
                    playbackNowAuthorized = true
                    return true
                }
                AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> {
                    playbackDelayed = true
                    playbackNowAuthorized = false
                }
                else -> playbackNowAuthorized = false
            }
            return false
        }
    }

    override fun abandonAudioFocus() {
        audioManager?.abandonAudioFocusRequest(focusRequest)
    }
}