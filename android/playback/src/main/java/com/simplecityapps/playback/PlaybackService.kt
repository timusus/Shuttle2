package com.simplecityapps.playback

import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.os.Build
import android.util.LruCache
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import com.simplecityapps.playback.androidauto.MediaIdHelper
import com.simplecityapps.playback.androidauto.PackageValidator
import com.simplecityapps.playback.mediasession.ArtworkBitmapLoader
import com.simplecityapps.playback.mediasession.PlayRequests
import com.simplecityapps.playback.mediasession.SessionCallback
import com.simplecityapps.playback.mediasession.SessionPlayer
import com.simplecityapps.playback.mediasession.awaitRestored
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.playback.queue.queueEntryOrNull
import com.simplecityapps.shuttle.pendingintent.PendingIntentCompat
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Publishes the app's player as a media library session: the notification, the lock screen, Android Auto's browse
 * tree, Bluetooth and headset buttons, Assistant, and playback resumption all go through it. Media3 runs the
 * notification and the foreground state; commands from controllers reach the queue and playback through
 * [SessionPlayer] and [SessionCallback].
 *
 * The app's widget and shortcuts start the service with one of the actions below, which it handles itself.
 */
@UnstableApi
@AndroidEntryPoint
class PlaybackService : MediaLibraryService() {
    @Inject
    lateinit var player: Player

    @Inject
    lateinit var playbackOperations: PlaybackOperations

    @Inject
    lateinit var queueOperations: QueueOperations

    @Inject
    lateinit var playRequests: PlayRequests

    @Inject
    lateinit var mediaIdHelper: MediaIdHelper

    @Inject
    lateinit var artworkImageLoader: ArtworkImageLoader

    @Inject
    lateinit var artworkCache: LruCache<String, Bitmap?>

    @Inject
    lateinit var preferenceManager: GeneralPreferenceManager

    private val packageValidator: PackageValidator by lazy { PackageValidator(this, R.xml.allowed_media_browser_callers) }

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var session: MediaLibrarySession

    private lateinit var callback: SessionCallback

    override fun onCreate() {
        super.onCreate()
        Timber.v("onCreate()")

        NotificationManagerCompat.from(this).createNotificationChannel(
            NotificationChannelCompat.Builder(NOTIFICATION_CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(getString(R.string.playback_notification_channel_name))
                .setShowBadge(false)
                .build()
        )
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(NOTIFICATION_CHANNEL_ID)
                .setChannelName(R.string.playback_notification_channel_name)
                .setNotificationId(NOTIFICATION_ID)
                .build()
                .apply { setSmallIcon(R.drawable.ic_stat_name) }
        )
        setShowNotificationForIdlePlayer(SHOW_NOTIFICATION_FOR_IDLE_PLAYER_AFTER_STOP_OR_ERROR)

        callback = SessionCallback(this, playRequests, mediaIdHelper, queueOperations, coroutineScope) { controller ->
            runCatching { packageValidator.isKnownCaller(controller.packageName, controller.uid) }.getOrDefault(false)
        }
        val sessionPlayer = SessionPlayer(player, playbackOperations, queueOperations, coroutineScope)
        session = MediaLibrarySession.Builder(this, sessionPlayer, callback)
            .setBitmapLoader(ArtworkBitmapLoader(this, artworkImageLoader, artworkCache, preferenceManager) { player.currentMediaItem?.queueEntryOrNull?.song })
            .setMediaButtonPreferences(callback.mediaButtonPreferences(queueOperations.getShuffleMode(), queueOperations.getRepeatMode()))
            .setSessionActivity(PendingIntent.getActivity(this, 1, (applicationContext as ActivityIntentProvider).provideMainActivityIntent(), PendingIntentCompat.FLAG_IMMUTABLE))
            .build()
        addSession(session)

        callback.launchMediaButtonUpdates(session)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession = session

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        val result = super.onStartCommand(intent, flags, startId)
        val action = intent?.action ?: return result
        if (action !in actions) return result

        Timber.v("onStartCommand() action: $action")
        // The widget and shortcuts start the service in the foreground; Media3 only goes there once it has a
        // notification to show, which it may not have yet, or at all for a paused player.
        startForegroundIfNotAlready()

        coroutineScope.launch {
            // A command given as the app starts acts on the saved queue, not the empty one before it's restored.
            queueOperations.queueStateFlow.awaitRestored()
            when (action) {
                ACTION_START -> Unit
                ACTION_TOGGLE_PLAYBACK -> playbackOperations.togglePlayback()
                ACTION_SKIP_PREV -> playbackOperations.skipToPrev()
                ACTION_SKIP_NEXT -> playbackOperations.skipToNext(ignoreRepeat = true)
                ACTION_TOGGLE_SHUFFLE -> queueOperations.toggleShuffleMode()
                ACTION_TOGGLE_REPEAT -> queueOperations.toggleRepeatMode()
            }
        }
        return result
    }

    /**
     * Meets a foreground start's obligation to call startForeground, with a placeholder under the notification's id
     * that Media3's own notification then replaces (or removes, stopping the foreground state, if it shows none).
     */
    private fun startForegroundIfNotAlready() {
        if (isPlaybackOngoing) return
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle(getString(com.simplecityapps.core.R.string.loading))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException (API 31+): started with startService from the background.
            Timber.w(e, "Unable to start the service in the foreground")
        }
        triggerNotificationUpdate()
    }

    override fun onDestroy() {
        Timber.v("onDestroy()")
        playbackOperations.pause()
        coroutineScope.cancel()
        // The player is the app's, and outlives the service; only the session goes.
        session.release()
        super.onDestroy()
    }

    companion object {
        /** Starts the service in the foreground, for playback that has started in the app. */
        const val ACTION_START: String = "com.simplecityapps.playback.start"
        const val ACTION_TOGGLE_PLAYBACK: String = "com.simplecityapps.playback.toggle"
        const val ACTION_SKIP_PREV: String = "com.simplecityapps.playback.prev"
        const val ACTION_SKIP_NEXT: String = "com.simplecityapps.playback.next"
        const val ACTION_TOGGLE_SHUFFLE: String = "com.simplecityapps.playback.shuffle"
        const val ACTION_TOGGLE_REPEAT: String = "com.simplecityapps.playback.repeat"

        private val actions = setOf(ACTION_START, ACTION_TOGGLE_PLAYBACK, ACTION_SKIP_PREV, ACTION_SKIP_NEXT, ACTION_TOGGLE_SHUFFLE, ACTION_TOGGLE_REPEAT)

        // The channel and id the app's own notification used, so a user's settings for the channel carry over.
        const val NOTIFICATION_CHANNEL_ID = "2"
        const val NOTIFICATION_ID = 1
    }
}
