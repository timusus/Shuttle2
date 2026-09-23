package com.simplecityapps.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Pauses playback when audio is about to become noisy (e.g. headphones are unplugged), by keeping a
 * [NoisyReceiver] registered while playback is loading or playing, for as long as [appCoroutineScope] lives.
 */
class NoiseManager(
    private val context: Context,
    playbackManager: PlaybackOperations,
    appCoroutineScope: CoroutineScope
) {
    private val broadcastReceiver = NoisyReceiver(playbackManager)

    init {
        appCoroutineScope.launchNoisyReceiverUpdates(
            playbackStateFlow = playbackManager.playbackStateFlow,
            context = Dispatchers.Main.immediate,
            register = { context.registerReceiver(broadcastReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)) },
            unregister = { context.safelyUnregisterReceiver(broadcastReceiver) }
        )
    }
}

/**
 * Calls [register] when playback starts loading or playing and [unregister] when it stops, starting from
 * the current state, so the receiver is registered once per stretch of playback rather than on every state.
 */
internal fun CoroutineScope.launchNoisyReceiverUpdates(
    playbackStateFlow: StateFlow<PlaybackState>,
    context: CoroutineContext,
    register: () -> Unit,
    unregister: () -> Unit
): Job = launch(context) {
    playbackStateFlow
        .map { playbackState -> playbackState is PlaybackState.Loading || playbackState is PlaybackState.Playing }
        .distinctUntilChanged()
        .collect { isPlaying -> if (isPlaying) register() else unregister() }
}

class NoisyReceiver(val playbackManager: PlaybackOperations) : BroadcastReceiver() {
    override fun onReceive(
        context: Context?,
        intent: Intent?
    ) {
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
