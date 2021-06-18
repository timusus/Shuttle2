package com.simplecityapps.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.LruCache
import androidx.core.app.NotificationCompat
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import com.simplecityapps.playback.mediasession.MediaSessionManager
import com.simplecityapps.playback.queue.QueueChangeCallback
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.playback.queue.QueueWatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject

class PlaybackNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notificationManager: NotificationManager,
    private val playbackManager: PlaybackManager,
    private val queueManager: QueueManager,
    private val mediaSessionManager: MediaSessionManager,
    private val playbackWatcher: PlaybackWatcher,
    private val queueWatcher: QueueWatcher,
    private val artworkCache: LruCache<String, Bitmap>,
    private val artworkImageLoader: ArtworkImageLoader
) : PlaybackWatcherCallback,
    QueueChangeCallback {

    private val placeholder: Bitmap? by lazy {
        drawableToBitmap(context.resources.getDrawable(R.drawable.ic_music_note_black_24dp, context.theme))
    }

    private var hasDisplayedNotification = false

    fun registerCallbacks() {
        playbackWatcher.addCallback(this)
        queueWatcher.addCallback(this)
    }

    fun removeCallbacks() {
        playbackWatcher.removeCallback(this)
        queueWatcher.removeCallback(this)
    }

    fun displayNotification(): Notification {
        Timber.i("Display Notification")

        createNotificationChannel()

        val song = queueManager.getCurrentItem()?.song

        val notificationBuilder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .apply {
                song?.let { song ->
                    setContentTitle(song.name ?: context.getString(R.string.unknown))
                    setContentText(song.friendlyArtistName ?: song.albumArtist ?: context.getString(R.string.unknown))
                }
            }
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSessionManager.mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .setContentIntent(PendingIntent.getActivity(context, 1, (context.applicationContext as ActivityIntentProvider).provideMainActivityIntent(), 0))
            .setDeleteIntent(PendingIntent.getService(context, 1, Intent(context, PlaybackService::class.java).apply { action = PlaybackService.ACTION_NOTIFICATION_DISMISS }, 0))
            .addAction(prevAction)
            .addAction(playbackAction)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setNotificationSilent()
            .addAction(nextAction)
            .setLargeIcon(placeholder)

        val artworkSize = 512

        song?.let { song ->
            synchronized(artworkCache) {
                artworkCache[song.getArtworkCacheKey(artworkSize, artworkSize)]?.let { image ->
                    notificationBuilder.setLargeIcon(image)
                }
            } ?: run {
                artworkImageLoader.loadBitmap(
                    data = song,
                    width = artworkSize,
                    height = artworkSize,
                ) { image ->
                    if (image != null) {
                        if (song.id == queueManager.getCurrentItem()?.song?.id) {
                            notificationBuilder
                                .setContentTitle(song.name ?: context.getString(R.string.unknown))
                                .setContentText(song.friendlyArtistName ?: song.albumArtist ?: context.getString(R.string.unknown))
                                .setLargeIcon(image)
                            val notification = notificationBuilder.build()
                            notificationManager.notify(NOTIFICATION_ID, notification)
                        }
                        synchronized(artworkCache) {
                            artworkCache.put(song.getArtworkCacheKey(artworkSize, artworkSize), image)
                        }
                    }
                }
            }
        }

        // Load the next song's artwork as well
        queueManager.getNext(true)?.song?.let { song ->
            artworkCache[song.getArtworkCacheKey(artworkSize, artworkSize)] ?: artworkImageLoader.loadBitmap(song, 512, 512) { image ->
                if (image != null) {
                    synchronized(artworkCache) {
                        artworkCache.put(song.getArtworkCacheKey(artworkSize, artworkSize), image)
                    }
                }
            }
        }

        val notification = notificationBuilder.build()
        notificationManager.notify(NOTIFICATION_ID, notification)

        hasDisplayedNotification = true

        return notification
    }

    private val playbackAction: NotificationCompat.Action
        get() {
            val intent = Intent(context, PlaybackService::class.java).apply {
                action = PlaybackService.ACTION_TOGGLE_PLAYBACK
            }
            val pendingIntent = PendingIntent.getService(context, 1, intent, 0)

            return when (playbackManager.getPlayback().playBackState()) {
                is PlaybackState.Loading, PlaybackState.Playing -> {
                    NotificationCompat.Action(R.drawable.ic_pause_black_24dp, "Pause", pendingIntent)
                }
                else -> {
                    NotificationCompat.Action(R.drawable.ic_play_arrow_black_24dp, "Play", pendingIntent)
                }
            }
        }

    private val prevAction: NotificationCompat.Action
        get() {
            val intent = Intent(context, PlaybackService::class.java).apply {
                action = PlaybackService.ACTION_SKIP_PREV
            }
            val pendingIntent = PendingIntent.getService(context, 1, intent, 0)
            return NotificationCompat.Action(R.drawable.ic_skip_previous_black_24dp, "Prev", pendingIntent)
        }

    private val nextAction: NotificationCompat.Action
        get() {
            val intent = Intent(context, PlaybackService::class.java).apply {
                action = PlaybackService.ACTION_SKIP_NEXT
            }
            val pendingIntent = PendingIntent.getService(context, 1, intent, 0)
            return NotificationCompat.Action(R.drawable.ic_skip_next_black_24dp, "Prev", pendingIntent)
        }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.getNotificationChannel(NOTIFICATION_CHANNEL_ID) ?: run {
                val notificationChannel = NotificationChannel(NOTIFICATION_CHANNEL_ID, "S2", NotificationManager.IMPORTANCE_DEFAULT)
                notificationChannel.enableLights(false)
                notificationChannel.enableVibration(false)
                notificationChannel.setShowBadge(false)
                notificationChannel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                notificationManager.createNotificationChannel(notificationChannel)
            }

            // Attempt to delete old notification channel.
            // Todo: This can be removed at any point in the future, it's barely necessary as is.
            try {
                notificationManager.deleteNotificationChannel("1")
            } catch (e: Exception) {

            }
        }
    }


    // PlaybackWatcherCallback Implementation

    override fun onPlaybackStateChanged(playbackState: PlaybackState) {
        displayNotification()
    }


    // QueueChangeCallback Implementation

    override fun onQueueChanged() {
        if (queueWatcher.hasRestoredQueue) {
            displayNotification()
        }
    }

    override fun onShuffleChanged(shuffleMode: QueueManager.ShuffleMode) {
        if (queueWatcher.hasRestoredQueue) {
            displayNotification()
        }
    }

    override fun onRepeatChanged(repeatMode: QueueManager.RepeatMode) {
        if (queueWatcher.hasRestoredQueue) {
            displayNotification()
        }
    }

    override fun onQueuePositionChanged(oldPosition: Int?, newPosition: Int?) {
        if (queueWatcher.hasRestoredQueue) {
            displayNotification()
        }
    }


    companion object {
        const val NOTIFICATION_CHANNEL_ID = "2"
        const val NOTIFICATION_ID = 1

        fun drawableToBitmap(drawable: Drawable): Bitmap {

            if (drawable is BitmapDrawable) {
                if (drawable.bitmap != null) {
                    return drawable.bitmap
                }
            }

            val bitmap: Bitmap = if (drawable.intrinsicWidth <= 0 || drawable.intrinsicHeight <= 0) {
                Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
            } else {
                Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
            }

            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            return bitmap
        }
    }
}