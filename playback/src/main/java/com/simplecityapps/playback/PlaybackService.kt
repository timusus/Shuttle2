package com.simplecityapps.playback

import android.app.Service
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.support.v4.media.MediaBrowserCompat
import androidx.media.MediaBrowserServiceCompat
import androidx.media.session.MediaButtonReceiver
import com.simplecityapps.playback.androidauto.MediaIdHelper
import com.simplecityapps.playback.androidauto.PackageValidator
import com.simplecityapps.playback.mediasession.MediaSessionManager
import dagger.android.AndroidInjection
import io.reactivex.disposables.CompositeDisposable
import timber.log.Timber
import javax.inject.Inject

class PlaybackService :
    MediaBrowserServiceCompat(),
    PlaybackWatcherCallback {

    @Inject lateinit var playbackManager: PlaybackManager

    @Inject lateinit var playbackWatcher: PlaybackWatcher

    @Inject lateinit var mediaSessionManager: MediaSessionManager

    @Inject lateinit var notificationManager: PlaybackNotificationManager

    @Inject lateinit var mediaIdHelper: MediaIdHelper

    private var foregroundNotificationHandler: Handler? = null

    private var delayedShutdownHandler: Handler? = null

    private val packageValidator: PackageValidator by lazy { PackageValidator(this, R.xml.allowed_media_browser_callers) }

    private lateinit var compositeDisposable: CompositeDisposable

    override fun onCreate() {
        Timber.v("onCreate()")

        AndroidInjection.inject(this)
        super.onCreate()

        playbackWatcher.addCallback(this)

        foregroundNotificationHandler = Handler()
        delayedShutdownHandler = Handler()

        notificationManager.registerCallbacks()

        sessionToken = mediaSessionManager.mediaSession.sessionToken

        compositeDisposable = CompositeDisposable()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        Timber.v("onStartCommand() intent: ${intent?.toString()} action: ${intent?.action}")

        delayedShutdownHandler?.removeCallbacksAndMessages(null)

        MediaButtonReceiver.handleIntent(mediaSessionManager.mediaSession, intent)

        when (intent?.action) {
            ACTION_TOGGLE_PLAYBACK -> playbackManager.togglePlayback()
            ACTION_SKIP_PREV -> playbackManager.skipToPrev()
            ACTION_SKIP_NEXT -> playbackManager.skipToNext(true)
            ACTION_NOTIFICATION_DISMISS -> {
                Timber.v("Stopping due to notification dismiss")
                stopSelf()
                return START_STICKY
            }
        }

        if (intent != null) {
            startForeground(PlaybackNotificationManager.NOTIFICATION_ID, notificationManager.displayNotification())
            Timber.v("startForeground() called")
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        // For Android auto, need to call super, or onGetRoot won't be called.
        return if ("android.media.browse.MediaBrowserService" == intent?.action) {
            super.onBind(intent)
        } else null
    }

    override fun stopService(name: Intent?): Boolean {
        Timber.v("stopService() $name")
        return super.stopService(name)
    }

    override fun unbindService(conn: ServiceConnection) {
        Timber.v("unbindService()")
        super.unbindService(conn)
    }

    override fun onDestroy() {
        Timber.v("onDestroy()")

        playbackWatcher.removeCallback(this)
        playbackManager.pause()

        notificationManager.removeCallbacks()

        foregroundNotificationHandler?.removeCallbacksAndMessages(null)
        delayedShutdownHandler?.removeCallbacksAndMessages(null)

        compositeDisposable.clear()

        super.onDestroy()
    }


    // PlaybackWatcherCallback Implementation

    override fun onPlaystateChanged(isPlaying: Boolean) {
        // We use the foreground notification handler here to slightly delay the call to stopForeground().
        // This appears to be necessary in order to allow our notification to become dismissable if pause() is called via onStartCommand() to this service.
        // Presumably, there is an issue in calling stopForeground() too soon after startForeground() which causes the notification to be stuck in the 'ongoing' state and not able to be dismissed.

        foregroundNotificationHandler?.removeCallbacksAndMessages(null)

        delayedShutdownHandler?.removeCallbacksAndMessages(null)

        if (!isPlaying) {
            foregroundNotificationHandler?.postDelayed({
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    Timber.v("stopForeground()")
                    stopForeground(Service.STOP_FOREGROUND_DETACH)
                } else {
                    Timber.v("stopForeground()")
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


    // MediaBrowserService Implementation

    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
        if ("EMPTY_ROOT" == parentId) {
            result.sendResult(mutableListOf())
        } else {
            result.detach()
            Timber.i("MediaId: $parentId")
            compositeDisposable.add(mediaIdHelper.getChildren(parentId).subscribe(
                { mediaItems ->
                    result.sendResult(mediaItems.toMutableList())
                },
                { throwable ->
                    Timber.e(throwable, "Failed to retrieve children for media id: $parentId")
                    result.sendResult(mutableListOf())
                }
            ))
        }
    }

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot? {
        return if (packageValidator.isKnownCaller(clientPackageName, clientUid)) {
            BrowserRoot("media:/root/", null)
        } else {
            Timber.i("OnGetRoot: Browsing NOT ALLOWED for unknown caller. Returning empty browser root so all apps can use MediaController. $clientPackageName")
            BrowserRoot("EMPTY_ROOT", null)
        }
    }


    // Static

    companion object {

        const val ACTION_TOGGLE_PLAYBACK: String = "com.simplecityapps.playback.toggle"
        const val ACTION_SKIP_PREV: String = "com.simplecityapps.playback.prev"
        const val ACTION_SKIP_NEXT: String = "com.simplecityapps.playback.next"
        const val ACTION_NOTIFICATION_DISMISS: String = "com.simplecityapps.playback.notification.dismiss"
    }

}