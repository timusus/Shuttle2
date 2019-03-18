package com.simplecityapps.playback

import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import androidx.media.session.MediaButtonReceiver
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.playback.mediasession.MediaSessionManager
import dagger.android.AndroidInjection
import timber.log.Timber
import javax.inject.Inject

class PlaybackService : Service(), Playback.Callback {

    @Inject lateinit var playbackManager: PlaybackManager

    @Inject lateinit var mediaSessionManager: MediaSessionManager

    @Inject lateinit var notificationManager: PlaybackNotificationManager

    private var foregroundNotificationHandler: Handler? = null

    private var delayedShutdownHandler: Handler? = null

    override fun onCreate() {
        Timber.v("onCreate()")

        AndroidInjection.inject(this)
        super.onCreate()

        playbackManager.addCallback(this)

        foregroundNotificationHandler = Handler()
        delayedShutdownHandler = Handler()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        Timber.v("onStartCommand() intent: ${intent?.toString()} action: ${intent?.action}")

        delayedShutdownHandler?.removeCallbacksAndMessages(null)

        MediaButtonReceiver.handleIntent(mediaSessionManager.mediaSession, intent)

        when (intent?.action) {
            PlaybackService.ACTION_TOGGLE_PLAYBACK -> playbackManager.togglePlayback()
            PlaybackService.ACTION_SKIP_PREV -> playbackManager.skipToPrev()
            PlaybackService.ACTION_SKIP_NEXT -> playbackManager.skipToNext(true)
            PlaybackService.ACTION_NOTIFICATION_DISMISS -> {
                stopSelf()
                return START_STICKY
            }
        }

        val notification = notificationManager.displayNotification()

        Timber.v("startForeground() called")
        startForeground(PlaybackNotificationManager.NOTIFICATION_ID, notification)

        return Service.START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {

        Timber.v("onDestroy()")

        playbackManager.removeCallback(this)
        playbackManager.pause()

        foregroundNotificationHandler?.removeCallbacksAndMessages(null)
        delayedShutdownHandler?.removeCallbacksAndMessages(null)

        super.onDestroy()
    }


    // Playback.Callback Implementation

    override fun onPlaystateChanged(isPlaying: Boolean) {

        // We use the foreground notification handler here to slightly delay the call to stopForeground().
        // This appears to be necessary in order to allow our notification to become dismissable if pause() is called via onStartCommand() to this service.
        // Presumably, there is an issue in calling stopForeground() too soon after startForeground() which causes the notification to be stuck in the 'ongoing' state and not able to be dismissed.

        foregroundNotificationHandler?.removeCallbacksAndMessages(null)

        delayedShutdownHandler?.removeCallbacksAndMessages(null)

        if (!isPlaying) {
            foregroundNotificationHandler?.postDelayed({
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(Service.STOP_FOREGROUND_DETACH)
                } else {
                    stopForeground(true)
                    notificationManager.displayNotification()
                }
            }, 150)

            // Shutdown this service after 30 seconds
            delayedShutdownHandler?.postDelayed({
                if (!playbackManager.isPlaying()) {
                    Timber.v("Stopping service due to 30 second shutdown timer")
                    stopSelf()
                }
            }, 30 * 1000)
        }
    }

    override fun onPlaybackPrepared() {

    }

    override fun onPlaybackComplete(song: Song) {

    }


    // Static

    companion object {

        const val ACTION_TOGGLE_PLAYBACK: String = "com.simplecityapps.playback.toggle"
        const val ACTION_SKIP_PREV: String = "com.simplecityapps.playback.prev"
        const val ACTION_SKIP_NEXT: String = "com.simplecityapps.playback.next"
        const val ACTION_NOTIFICATION_DISMISS: String = "com.simplecityapps.playback.notification.dismiss"
    }

}