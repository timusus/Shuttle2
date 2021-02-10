package com.simplecityapps.playback

import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect

class AudioEffectSessionManager(val context: Context) {

    var sessionId = 0

    fun openAudioEffectSession() {
        context.sendBroadcast(Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION).apply {
            putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
            putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
        })
    }

    fun closeAudioEffectSession() {
        context.sendBroadcast(Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION).apply {
            putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
            putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
        })
    }
}