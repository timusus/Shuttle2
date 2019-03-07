package com.simplecityapps.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.playback.queue.QueueChangeCallback
import com.simplecityapps.playback.queue.QueueManager

class PlaybackNotificationManager(
    private val context: Context,
    private val notificationManager: NotificationManager,
    private val playbackManager: PlaybackManager,
    private val queueManager: QueueManager
) : Playback.Callback, QueueChangeCallback {

    init {
        playbackManager.addCallback(this)
        queueManager.addCallback(this)
    }

    fun displayNotification(): Notification {

        createNotificationChannel()

        val song = queueManager.getCurrentItem()?.song

        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .apply {
                song?.let {
                    setContentText(song.albumArtistName)
                        .setContentTitle(song.name)
                }
            }
            .setShowWhen(false)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(playbackManager.mediaSession.sessionToken)
                    .setShowCancelButton(true)
            )
            .addAction(prevAction)
            .addAction(playbackAction)
            .addAction(nextAction)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)

        return notification!!
    }

    private val playbackAction: NotificationCompat.Action
        get() {
            val intent = Intent(context, PlaybackService::class.java).apply {
                action = PlaybackService.ACTION_TOGGLE_PLAYBACK
            }
            val pendingIntent = PendingIntent.getService(context, 1, intent, 0)

            return if (playbackManager.isPlaying()) {
                NotificationCompat.Action(R.drawable.ic_pause_black_24dp, "Pause", pendingIntent)
            } else {
                NotificationCompat.Action(R.drawable.ic_play_arrow_black_24dp, "Play", pendingIntent)
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
                val notificationChannel = NotificationChannel(NOTIFICATION_CHANNEL_ID, "Shuttle", NotificationManager.IMPORTANCE_LOW)
                notificationChannel.enableLights(false)
                notificationChannel.enableVibration(false)
                notificationChannel.setShowBadge(false)
                notificationChannel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                notificationManager.createNotificationChannel(notificationChannel)
            }
        }
    }


    // Playback.Callback Implementation

    override fun onPlaystateChanged(isPlaying: Boolean) {
        displayNotification()
    }

    override fun onPlaybackPrepared() {

    }

    override fun onPlaybackComplete(song: Song?) {

    }


    // QueueChangeCallback Implementation

    override fun onQueueChanged() {
        displayNotification()
    }

    override fun onShuffleChanged() {
        displayNotification()
    }

    override fun onRepeatChanged() {
        displayNotification()
    }

    override fun onQueuePositionChanged() {
        displayNotification()
    }


    companion object {
        const val NOTIFICATION_CHANNEL_ID = "1"
        const val NOTIFICATION_ID = 1
    }
}