package com.simplecityapps.shuttle.ui

import android.content.Context
import android.os.Build
import com.simplecityapps.playback.PlaybackOperations
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.shuttle.coroutines.launchCollectingChanges
import com.simplecityapps.shuttle.di.AppCoroutineScope
import com.simplecityapps.shuttle.di.ApplicationContext
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope

@SingleIn(AppScope::class)
class ShortcutManager
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val playbackOperations: PlaybackOperations,
    private val shortcutHelper: ShortcutHelper,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope
) {

    fun registerCallbacks() {
        // The shortcut is created from a live read, which is at least as new as the flow's value, so the flow
        // is snapshotted before that read and a change after it is still delivered.
        val initialPlaybackState = playbackOperations.playbackStateFlow.value

        // Initialize shortcut with current state
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            val isPlaying = playbackOperations.playbackState() == PlaybackState.Playing
            shortcutHelper.createPlaybackShortcut(context, isPlaying)
        }

        appCoroutineScope.launchCollectingChanges(playbackOperations.playbackStateFlow, initialPlaybackState) { _, playbackState ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                val isPlaying = playbackState == PlaybackState.Playing
                shortcutHelper.updatePlaybackShortcut(context, isPlaying)
            }
        }
    }
}
