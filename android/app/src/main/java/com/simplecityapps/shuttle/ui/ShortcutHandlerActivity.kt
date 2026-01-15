package com.simplecityapps.shuttle.ui

import android.app.ForegroundServiceStartNotAllowedException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.simplecityapps.playback.PlaybackService
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class ShortcutHandlerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        when (intent?.action) {
            ACTION_TOGGLE_PLAYBACK -> {
                val serviceIntent = Intent(this, PlaybackService::class.java).apply {
                    action = PlaybackService.ACTION_TOGGLE_PLAYBACK
                }

                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(serviceIntent)
                    } else {
                        startService(serviceIntent)
                    }
                } catch (e: IllegalStateException) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is ForegroundServiceStartNotAllowedException) {
                        Timber.w(e, "Cannot start foreground service from shortcut - app may be in restricted state")
                    } else {
                        throw e
                    }
                }
            }
        }

        finish()
    }

    companion object {
        const val ACTION_TOGGLE_PLAYBACK = "com.simplecityapps.shuttle.shortcuts.TOGGLE_PLAYBACK"
    }
}
