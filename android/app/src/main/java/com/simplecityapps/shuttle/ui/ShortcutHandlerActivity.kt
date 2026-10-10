package com.simplecityapps.shuttle.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.simplecityapps.playback.PlaybackServiceAction
import com.simplecityapps.playback.PlaybackServiceStarter
import com.simplecityapps.shuttle.di.appGraph
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject

class ShortcutHandlerActivity : AppCompatActivity() {

    @Inject
    lateinit var playbackServiceStarter: PlaybackServiceStarter

    @ContributesTo(AppScope::class)
    interface Injector {
        fun inject(activity: ShortcutHandlerActivity)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applicationContext.appGraph<Injector>().inject(this)

        when (intent?.action) {
            ACTION_TOGGLE_PLAYBACK -> playbackServiceStarter.start(PlaybackServiceAction.TogglePlayback)
            ACTION_SHUFFLE_ALL -> playbackServiceStarter.start(PlaybackServiceAction.ShuffleAll)
        }

        finish()
    }

    companion object {
        const val ACTION_TOGGLE_PLAYBACK = "com.simplecityapps.shuttle.shortcuts.TOGGLE_PLAYBACK"
        const val ACTION_SHUFFLE_ALL = "com.simplecityapps.shuttle.shortcuts.SHUFFLE_ALL"
    }
}
