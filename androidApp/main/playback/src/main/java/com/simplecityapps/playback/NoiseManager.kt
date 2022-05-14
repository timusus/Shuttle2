package com.simplecityapps.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager

class NoiseManager(
    private val context: Context,
    playbackManager: PlaybackManager,
    playbackWatcher: PlaybackWatcher
) : PlaybackWatcherCallback {

    init {
        playbackWatcher.addCallback(this)
    }

    private val broadcastReceiver = NoisyReceiver(playbackManager)

    // PlaybackWatcherCallback Implementation

    override fun onPlaybackStateChanged(playbackState: PlaybackState) {
        when (playbackState) {
            is PlaybackState.Loading, PlaybackState.Playing -> {
                context.registerReceiver(broadcastReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
            }
            else -> {
                context.safelyUnregisterReceiver(broadcastReceiver)
            }
        }
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
