package com.simplecityapps.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import com.simplecityapps.mediaprovider.model.Song

class NoiseManager(val context: Context, val playbackManager: PlaybackManager) : Playback.Callback {

    init {
        playbackManager.addCallback(this)
    }

    private val broadcastReceiver = NoisyReceiver(playbackManager)


    // Playback.Callback Implementation

    override fun onPlaystateChanged(isPlaying: Boolean) {
        if (isPlaying) {
            context.registerReceiver(broadcastReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        } else {
            context.safelyUnregisterReceiver(broadcastReceiver)
        }
    }

    override fun onPlaybackPrepared() {

    }

    override fun onPlaybackComplete(song: Song?) {

    }
}


class NoisyReceiver(val playbackManager: PlaybackManager) : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
            playbackManager.pause()
        }
    }
}


fun Context.safelyUnregisterReceiver(broadcastReceiver: BroadcastReceiver) {
    try {
        unregisterReceiver(broadcastReceiver)
    } catch (e: IllegalArgumentException) {

    }
}