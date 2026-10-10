package com.simplecityapps.playback

import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.simplecityapps.shuttle.di.ApplicationContext
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import timber.log.Timber

@ContributesBinding(AppScope::class)
class AndroidPlaybackServiceStarter
@Inject
constructor(
    @ApplicationContext private val context: Context
) : PlaybackServiceStarter {
    override fun start(action: PlaybackServiceAction) {
        val intent = Intent(context, PlaybackService::class.java).setAction(intentAction(action))
        try {
            ContextCompat.startForegroundService(context, intent)
        } catch (e: IllegalStateException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is ForegroundServiceStartNotAllowedException) {
                Timber.w(e, "Cannot start the playback service for $action - app may be in a restricted state")
            } else {
                throw e
            }
        }
    }

    internal companion object {
        fun intentAction(action: PlaybackServiceAction): String = when (action) {
            PlaybackServiceAction.TogglePlayback -> PlaybackService.ACTION_TOGGLE_PLAYBACK
            PlaybackServiceAction.SkipPrevious -> PlaybackService.ACTION_SKIP_PREV
            PlaybackServiceAction.SkipNext -> PlaybackService.ACTION_SKIP_NEXT
            PlaybackServiceAction.ToggleShuffle -> PlaybackService.ACTION_TOGGLE_SHUFFLE
            PlaybackServiceAction.ToggleRepeat -> PlaybackService.ACTION_TOGGLE_REPEAT
            PlaybackServiceAction.ShuffleAll -> PlaybackService.ACTION_SHUFFLE_ALL
        }
    }
}
