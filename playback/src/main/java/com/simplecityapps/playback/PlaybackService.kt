package com.simplecityapps.playback

import android.app.Service
import android.content.Intent
import android.os.IBinder
import dagger.android.AndroidInjection
import timber.log.Timber
import javax.inject.Inject

class PlaybackService : Service() {

    @Inject lateinit var playbackManager: PlaybackManager

    @Inject lateinit var notificationManager: PlaybackNotificationManager

    override fun onCreate() {
        AndroidInjection.inject(this)
        super.onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        Timber.d("onStartCommand() action: ${intent?.action}")

        val notification = notificationManager.displayNotification()
        startForeground(PlaybackNotificationManager.NOTIFICATION_ID, notification)

        when (intent?.action) {
            PlaybackService.ACTION_TOGGLE_PLAYBACK -> playbackManager.togglePlayback()
            PlaybackService.ACTION_SKIP_PREV -> playbackManager.skipToPrev()
            PlaybackService.ACTION_SKIP_NEXT -> playbackManager.skipToNext()
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {

        playbackManager.pause()

        super.onDestroy()
    }

    companion object {

        const val ACTION_TOGGLE_PLAYBACK: String = "com.simplectiyapps.playback.toggle"
        const val ACTION_SKIP_PREV: String = "com.simplectiyapps.playback.prev"
        const val ACTION_SKIP_NEXT: String = "com.simplectiyapps.playback.next"
    }

}