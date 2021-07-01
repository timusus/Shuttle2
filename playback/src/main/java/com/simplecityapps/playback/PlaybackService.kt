package com.simplecityapps.playback

import android.app.SearchManager
import android.app.Service
import android.content.Intent
import android.content.ServiceConnection
import android.os.*
import android.support.v4.media.MediaBrowserCompat
import androidx.media.MediaBrowserServiceCompat
import androidx.media.session.MediaButtonReceiver
import com.simplecityapps.playback.androidauto.MediaIdHelper
import com.simplecityapps.playback.androidauto.PackageValidator
import com.simplecityapps.playback.mediasession.MediaSessionManager
import com.simplecityapps.playback.queue.QueueChangeCallback
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.playback.queue.QueueWatcher
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class PlaybackService :
    MediaBrowserServiceCompat(),
    PlaybackWatcherCallback,
    QueueChangeCallback {

    @Inject
    lateinit var playbackManager: PlaybackManager

    @Inject
    lateinit var playbackWatcher: PlaybackWatcher

    @Inject
    lateinit var queueManager: QueueManager

    @Inject
    lateinit var queueWatcher: QueueWatcher

    @Inject
    lateinit var mediaSessionManager: MediaSessionManager

    @Inject
    lateinit var notificationManager: PlaybackNotificationManager

    @Inject
    lateinit var mediaIdHelper: MediaIdHelper

    private var foregroundNotificationHandler: Handler? = null

    private var delayedShutdownHandler: Handler? = null

    private val packageValidator: PackageValidator by lazy { PackageValidator(this, R.xml.allowed_media_browser_callers) }

    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    private var pendingStartCommands = mutableListOf<Intent>()

    override fun onCreate() {
        super.onCreate()

        Timber.v("onCreate()")

        playbackWatcher.addCallback(this)
        queueWatcher.addCallback(this)

        foregroundNotificationHandler = Handler(Looper.getMainLooper())
        delayedShutdownHandler = Handler(Looper.getMainLooper())

        notificationManager.registerCallbacks()

        sessionToken = mediaSessionManager.mediaSession.sessionToken
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        Timber.v("onStartCommand() action: ${intent?.action}")

        if (intent == null && (playbackManager.playbackState() != PlaybackState.Loading || playbackManager.playbackState() != PlaybackState.Playing)) {
            stopForeground(true)
            return START_NOT_STICKY
        }

        // Cancel any pending shutdown
        Timber.v("Cancelling delayed shutdown")
        delayedShutdownHandler?.removeCallbacksAndMessages(null)

        // Intent is only null if this service is being re-created due to process death
        intent?.let {
            when (intent.action) {
                ACTION_NOTIFICATION_DISMISS -> {
                    // The user has swiped away the notification. This is only possible when the service is no longer running in the foreground
                    Timber.v("Stopping due to notification dismiss")
                    stopSelf()
                    return START_NOT_STICKY
                }
            }

            if (queueManager.hasRestoredQueue) {
                // The queue is restored, so we know if it's empty or not. Proceed to handle the command
                if (queueManager.getQueue().isEmpty()) {
                    Timber.v("startForeground() called. Showing notification: Queue Empty")
                    /*
                        a) We can't just stopSelf() here. If we were called via startForegroundService(), we must show a foreground notification.
                        b) The user is now stuck with a non-dismissable 'empty queue' notification.

                       We'll allow 10 seconds, so we're not calling stopSelf() before Google ANR's us.
                       This also gives S2 time to respond to pending commands. For example, if the command is 'loadFromSearch', we don't want to stop the service
                       and allow the process to be killed while that we're in the middle of executing that command.
                    */
                    startForeground(PlaybackNotificationManager.NOTIFICATION_ID, notificationManager.displayQueueEmptyNotification())
                    postDelayedShutdown(10000)
                } else {
                    Timber.v("startForeground() called. Showing notification: Playback")
                    startForeground(PlaybackNotificationManager.NOTIFICATION_ID, notificationManager.displayPlaybackNotification())
                }
                processCommand(intent)
            } else {
                Timber.v("startForeground() called. Showing notification: Loading")
                startForeground(PlaybackNotificationManager.NOTIFICATION_ID, notificationManager.displayLoadingNotification())
                pendingStartCommands.add(intent)
            }
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
        queueWatcher.removeCallback(this)
        playbackManager.pause()

        notificationManager.removeCallbacks()

        foregroundNotificationHandler?.removeCallbacksAndMessages(null)
        delayedShutdownHandler?.removeCallbacksAndMessages(null)

        coroutineScope.cancel()

        super.onDestroy()
    }


    // Private

    private fun processCommand(intent: Intent) {
        Timber.v("processCommand()")
        MediaButtonReceiver.handleIntent(mediaSessionManager.mediaSession, intent)

        when (intent.action) {
            ACTION_TOGGLE_PLAYBACK -> playbackManager.togglePlayback()
            ACTION_SKIP_PREV -> playbackManager.skipToPrev()
            ACTION_SKIP_NEXT -> playbackManager.skipToNext(ignoreRepeat = true)
            ACTION_SEARCH -> mediaSessionManager.mediaSession.controller?.transportControls?.playFromSearch(intent.extras?.getString(SearchManager.QUERY), Bundle())
        }
    }

    private fun postDelayedShutdown(delay: Long = 15 * 1000L) {
        Timber.v("postDelayedShutdown(delay: $delay)")
        delayedShutdownHandler?.removeCallbacksAndMessages(null)
        delayedShutdownHandler?.postDelayed({
            if (playbackManager.playbackState() !is PlaybackState.Loading && playbackManager.playbackState() !is PlaybackState.Playing) {
                Timber.v("Stopping service due to ${delay}ms shutdown timer")
                if (queueManager.getQueue().isEmpty()) {
                    notificationManager.removeNotification()
                }
                stopSelf()
            }
        }, delay)
    }


    // PlaybackWatcherCallback Implementation

    override fun onPlaybackStateChanged(playbackState: PlaybackState) {
        // We use the foreground notification handler here to slightly delay the call to stopForeground().
        // This appears to be necessary in order to allow our notification to become dismissable if pause() is called via onStartCommand() to this service.
        // Presumably, there is an issue in calling stopForeground() too soon after startForeground() which causes the notification to be stuck in the 'ongoing' state and not able to be dismissed.

        foregroundNotificationHandler?.removeCallbacksAndMessages(null)

        Timber.v("Cancelling delayed shutdown")
        delayedShutdownHandler?.removeCallbacksAndMessages(null)

        if (playbackState is PlaybackState.Paused) {
            foregroundNotificationHandler?.postDelayed({
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    Timber.v("stopForeground()")
                    stopForeground(Service.STOP_FOREGROUND_DETACH)
                } else {
                    Timber.v("stopForeground()")
                    stopForeground(true)
                    notificationManager.displayPlaybackNotification()
                }
            }, 150)

            postDelayedShutdown()
        }
    }

    override fun onQueueRestored() {
        super.onQueueRestored()

        if (queueManager.getQueue().isEmpty()) {
            Timber.v("Queue empty")
            stopForeground(true)
            notificationManager.displayQueueEmptyNotification()
            postDelayedShutdown()
        }

        pendingStartCommands.forEach { pendingStartCommand ->
            processCommand(pendingStartCommand)
        }
        pendingStartCommands.clear()
    }

    override fun onQueueChanged() {
        if (queueManager.hasRestoredQueue && queueManager.getQueue().isEmpty()) {
            Timber.v("Queue cleared, stopForeground() called")
            // This should only occur if the user manually clears their queue, while playback is paused
            stopForeground(true)
            notificationManager.removeNotification()
            stopSelf()
        }
    }


    // MediaBrowserService Implementation

    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
        if ("EMPTY_ROOT" == parentId) {
            result.sendResult(mutableListOf())
        } else {
            result.detach()
            Timber.v("MediaId: $parentId")
            coroutineScope.launch {
                result.sendResult(mediaIdHelper.getChildren(parentId).toMutableList())
            }
        }
    }

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot? {
        return if (packageValidator.isKnownCaller(clientPackageName, clientUid)) {
            BrowserRoot("media:/root/", null)
        } else {
            Timber.v("OnGetRoot: Browsing NOT ALLOWED for unknown caller. Returning empty browser root so all apps can use MediaController. $clientPackageName")
            BrowserRoot("EMPTY_ROOT", null)
        }
    }


    // Static

    companion object {
        const val ACTION_TOGGLE_PLAYBACK: String = "com.simplecityapps.playback.toggle"
        const val ACTION_SKIP_PREV: String = "com.simplecityapps.playback.prev"
        const val ACTION_SKIP_NEXT: String = "com.simplecityapps.playback.next"
        const val ACTION_SEARCH: String = "com.simplecityapps.playback.search"
        const val ACTION_NOTIFICATION_DISMISS: String = "com.simplecityapps.playback.notification.dismiss"
    }
}