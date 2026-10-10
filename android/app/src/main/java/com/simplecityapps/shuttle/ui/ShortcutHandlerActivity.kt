package com.simplecityapps.shuttle.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.simplecityapps.playback.PlaybackService

class ShortcutHandlerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        when (intent?.action) {
            ACTION_TOGGLE_PLAYBACK -> PlaybackService.startAction(this, PlaybackService.ACTION_TOGGLE_PLAYBACK)
            ACTION_SHUFFLE_ALL -> PlaybackService.startAction(this, PlaybackService.ACTION_SHUFFLE_ALL)
        }

        finish()
    }

    companion object {
        const val ACTION_TOGGLE_PLAYBACK = "com.simplecityapps.shuttle.shortcuts.TOGGLE_PLAYBACK"
        const val ACTION_SHUFFLE_ALL = "com.simplecityapps.shuttle.shortcuts.SHUFFLE_ALL"
    }
}
