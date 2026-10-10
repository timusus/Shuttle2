package com.simplecityapps.playback

import android.app.ForegroundServiceStartNotAllowedException
import android.app.PendingIntent
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.util.LruCache
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.simplecityapps.imageloading.ArtworkImageLoader
import com.simplecityapps.playback.androidauto.MediaIdHelper
import com.simplecityapps.playback.mediasession.ArtworkBitmapLoader
import com.simplecityapps.playback.mediasession.CarAccess
import com.simplecityapps.playback.mediasession.PlayRequests
import com.simplecityapps.playback.mediasession.SessionCallback
import com.simplecityapps.playback.mediasession.SessionPlayer
import com.simplecityapps.playback.mediasession.TrustedCallers
import com.simplecityapps.playback.mediasession.awaitRestored
import com.simplecityapps.playback.persistence.PlaybackPreferenceManager
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.playback.queue.queueEntryOrNull
import com.simplecityapps.shuttle.di.appGraph
import com.simplecityapps.shuttle.entitlement.ServerAccessGate
import com.simplecityapps.shuttle.pendingintent.PendingIntentCompat
import com.simplecityapps.shuttle.settings.ArtworkSettings
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import timber.log.Timber

/**
 * Publishes the app's player as a media library session: the notification, the lock screen, Android Auto's browse
 * tree, Bluetooth and headset buttons, Assistant, and playback resumption all go through it. Media3 runs the
 * notification and the foreground state; commands from controllers reach the queue and playback through
 * [SessionPlayer] and [SessionCallback].
 *
 * The app's widget and shortcuts, and voice searches ([VoiceSearchActivity]), start the service with one of the actions
 * below, which it handles itself.
 * [ForegroundStarts] keeps a start in the foreground until its command has run, as the app starts too.
 */
@UnstableApi
class PlaybackService : MediaLibraryService() {
    @ContributesTo(AppScope::class)
    interface Injector {
        fun inject(service: PlaybackService)
    }

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
    lateinit var playbackPreferenceManager: PlaybackPreferenceManager

    @Inject
    lateinit var artworkImageLoader: ArtworkImageLoader

    @Inject
    lateinit var artworkCache: LruCache<String, Bitmap>

    @Inject
    lateinit var artworkSettings: ArtworkSettings

    @Inject
    lateinit var castStarter: CastStarter

    @Inject
    lateinit var foregroundHold: ForegroundHold

    @Inject
    lateinit var serverAccessGate: ServerAccessGate

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var session: MediaLibrarySession

    private lateinit var callback: SessionCallback

    private lateinit var foregroundStarts: ForegroundStarts

    override fun onCreate() {
        appGraph<Injector>().inject(this)
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

        callback = SessionCallback(this, playRequests, mediaIdHelper, queueOperations, playbackPreferenceManager::nowPlaying, coroutineScope, CarAccess(serverAccessGate), TrustedCallers::isTrusted)
        val sessionPlayer = SessionPlayer(player, playbackOperations, queueOperations, coroutineScope)
        session = MediaLibrarySession.Builder(this, sessionPlayer, callback)
            .setBitmapLoader(ArtworkBitmapLoader(this, artworkImageLoader, artworkCache, artworkSettings) { player.currentMediaItem?.queueEntryOrNull?.song })
            .setMediaButtonPreferences(callback.mediaButtonPreferences(queueOperations.getShuffleMode(), queueOperations.getRepeatMode()))
            .setSessionActivity(PendingIntent.getActivity(this, 1, (applicationContext as ActivityIntentProvider).provideMainActivityIntent(), PendingIntentCompat.FLAG_IMMUTABLE))
            .build()
        foregroundStarts = ForegroundStarts(this, sessionPlayer, coroutineScope, NOTIFICATION_ID) {
            // Under the notification's id, so Media3's own notification replaces it.
            NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_name)
                .setContentTitle(getString(com.simplecityapps.core.R.string.loading))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build()
        }
        addSession(session)
        callback.launchMediaButtonUpdates(session)
        callback.launchRootRefreshes(session)
        // A session started with no activity (Android Auto, a media button) casts too.
        castStarter.startForSession()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession = session

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        val result = super.onStartCommand(intent, flags, startId)
        if (intent != null) handleStart(intent, foregroundStarts, foregroundHold, playbackOperations, queueOperations, playRequests::playSearch, playRequests::shuffleAll)
        return result
    }

    override fun onUpdateNotificationAsync(
        session: MediaSession,
        startInForegroundRequired: Boolean
    ): ListenableFuture<Void?> = if (foregroundStarts.mayUpdateNotification(startInForegroundRequired)) {
        super.onUpdateNotificationAsync(session, startInForegroundRequired)
    } else {
        Futures.immediateVoidFuture()
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
        /**
         * Starts the service in the foreground ([ACTION_START]), for playback that has started in the app, or a play
         * held for a call ([ForegroundHold]). From the background Android may refuse (API 31+), which is logged.
         */
        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, PlaybackService::class.java).setAction(ACTION_START))
            } catch (e: IllegalStateException) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is ForegroundServiceStartNotAllowedException) {
                    Timber.w(e, "Cannot start foreground service from background - likely audio focus regained while app in background")
                } else {
                    throw e
                }
            }
        }

        /**
         * Starts the service in the foreground, for playback that has started in the app, or to stay there while a play
         * is held for a call, until [ForegroundHold] is released.
         */
        const val ACTION_START: String = "com.simplecityapps.playback.start"
        const val ACTION_TOGGLE_PLAYBACK: String = "com.simplecityapps.playback.toggle"
        const val ACTION_SKIP_PREV: String = "com.simplecityapps.playback.prev"
        const val ACTION_SKIP_NEXT: String = "com.simplecityapps.playback.next"
        const val ACTION_TOGGLE_SHUFFLE: String = "com.simplecityapps.playback.shuffle"
        const val ACTION_TOGGLE_REPEAT: String = "com.simplecityapps.playback.repeat"
        const val ACTION_SHUFFLE_ALL: String = "com.simplecityapps.playback.shuffle_all"

        /** Plays a voice search: the intent's [SearchManager.QUERY] and its extras, as `VoiceSearch.from` reads them. */
        const val ACTION_PLAY_FROM_SEARCH: String = "com.simplecityapps.playback.search"

        /** Starts the service in the foreground with [action]; a start the system refuses is logged, not thrown. */
        fun startAction(
            context: Context,
            action: String
        ) {
            val intent = Intent(context, PlaybackService::class.java).setAction(action)
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

        private val actions = setOf(ACTION_START, ACTION_TOGGLE_PLAYBACK, ACTION_SKIP_PREV, ACTION_SKIP_NEXT, ACTION_TOGGLE_SHUFFLE, ACTION_TOGGLE_REPEAT, ACTION_SHUFFLE_ALL, ACTION_PLAY_FROM_SEARCH)

        /**
         * Runs one of the actions above, or keeps the service in the foreground for a play button's start, whose play
         * Media3 runs. A command given as the app starts acts on the saved queue, not the empty one before it's
         * restored.
         */
        internal fun handleStart(
            intent: Intent,
            foregroundStarts: ForegroundStarts,
            foregroundHold: ForegroundHold,
            playbackOperations: PlaybackOperations,
            queueOperations: QueueOperations,
            playSearch: suspend (query: String?, extras: Bundle?) -> Unit,
            shuffleAll: suspend () -> Unit
        ) {
            val action = intent.action
            when {
                ForegroundStarts.isPlayButton(intent) -> foregroundStarts.start {
                    queueOperations.queueStateFlow.awaitRestored()
                    delay(ForegroundStarts.MEDIA_BUTTON_SETTLE_MS)
                }

                action in actions -> {
                    Timber.v("onStartCommand() action: $action")
                    foregroundStarts.start {
                        queueOperations.queueStateFlow.awaitRestored()
                        when (action) {
                            ACTION_START -> foregroundHold.awaitRelease()
                            ACTION_TOGGLE_PLAYBACK -> playbackOperations.togglePlayback()
                            ACTION_SKIP_PREV -> playbackOperations.skipToPrev()
                            ACTION_SKIP_NEXT -> playbackOperations.skipToNext(ignoreRepeat = true)
                            ACTION_TOGGLE_SHUFFLE -> queueOperations.toggleShuffleMode()
                            ACTION_TOGGLE_REPEAT -> queueOperations.toggleRepeatMode()
                            ACTION_SHUFFLE_ALL -> shuffleAll()
                            ACTION_PLAY_FROM_SEARCH -> playSearch(intent.getStringExtra(SearchManager.QUERY), intent.extras)
                        }
                    }
                }
            }
        }

        // The channel and id the app's own notification used, so a user's settings for the channel carry over.
        const val NOTIFICATION_CHANNEL_ID = "2"
        const val NOTIFICATION_ID = 1
    }
}
