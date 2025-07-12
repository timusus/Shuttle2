package com.simplecityapps.shuttle.ui

import android.content.Context
import android.os.Build
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.PlaybackWatcher
import com.simplecityapps.playback.PlaybackWatcherCallback
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShortcutManager
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val playbackWatcher: PlaybackWatcher,
    private val playbackManager: PlaybackManager,
    private val shortcutHelper: ShortcutHelper
) : PlaybackWatcherCallback {

    fun registerCallbacks() {
        playbackWatcher.addCallback(this)

        // Initialize shortcut with current state
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            val isPlaying = playbackManager.playbackState() == PlaybackState.Playing
            shortcutHelper.createPlaybackShortcut(context, isPlaying)
        }
    }

    // PlaybackWatcherCallback Implementation

    override fun onPlaybackStateChanged(playbackState: PlaybackState) {
        super.onPlaybackStateChanged(playbackState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            val isPlaying = playbackState == PlaybackState.Playing
            shortcutHelper.updatePlaybackShortcut(context, isPlaying)
        }
    }
}
