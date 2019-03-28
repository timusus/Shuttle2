package com.simplecityapps.playback.mediasession

import android.content.Context
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.playback.PlaybackWatcher
import com.simplecityapps.playback.PlaybackWatcherCallback
import com.simplecityapps.playback.queue.QueueChangeCallback
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.playback.queue.QueueWatcher

class MediaSessionManager(
    private val context: Context,
    private val playbackManager: PlaybackManager,
    private val queueManager: QueueManager,
    playbackWatcher: PlaybackWatcher,
    queueWatcher: QueueWatcher
) : PlaybackWatcherCallback,
    QueueChangeCallback {

    val mediaSession: MediaSessionCompat by lazy {
        val mediaSession = MediaSessionCompat(context, "ShuttleMediaSession")
        mediaSession.setCallback(mediaSessionCallback)
        mediaSession
    }

    private var playbackStateBuilder = PlaybackStateCompat.Builder()
        .setActions(
            PlaybackStateCompat.ACTION_PLAY
                    or PlaybackStateCompat.ACTION_PAUSE
                    or PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                    or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                    or PlaybackStateCompat.ACTION_SEEK_TO
                    or PlaybackStateCompat.ACTION_SET_REPEAT_MODE
                    or PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE
        )

    init {
        playbackWatcher.addCallback(this)
        queueWatcher.addCallback(this)
    }


    // PlaybackWatcherCallback Implementation

    override fun onPlaystateChanged(isPlaying: Boolean) {
        mediaSession.isActive = isPlaying

        if (isPlaying) {
            playbackStateBuilder.setState(PlaybackStateCompat.STATE_PLAYING, playbackManager.getPosition()?.toLong() ?: 0, 1.0f)
        } else {
            playbackStateBuilder.setState(PlaybackStateCompat.STATE_PAUSED, playbackManager.getPosition()?.toLong() ?: 0, 1.0f)
        }

        mediaSession.setPlaybackState(playbackStateBuilder.build())
    }


    // QueueChangeCallback Implementation

    override fun onQueueChanged() {
        mediaSession.setQueue(queueManager.getQueue().map { queueItem -> queueItem.toQueueItem() })

    }

    override fun onQueuePositionChanged() {
        queueManager.getCurrentItem()?.let { currentItem ->
            playbackStateBuilder.setActiveQueueItemId(currentItem.toQueueItem().queueId)
            mediaSession.setPlaybackState(playbackStateBuilder.build())
        }
    }

    override fun onShuffleChanged() {
        mediaSession.setShuffleMode(queueManager.getShuffleMode().toShuffleMode())
    }

    override fun onRepeatChanged() {
        mediaSession.setRepeatMode(queueManager.getRepeatMode().toRepeatMode())
    }


    // MediaSessionCompat.Callback Implementation

    private val mediaSessionCallback = object : MediaSessionCompat.Callback() {

        override fun onPlay() {
            playbackManager.play()
        }

        override fun onPause() {
            playbackManager.pause()
        }

        override fun onSkipToPrevious() {
            playbackManager.skipToPrev()
        }

        override fun onSkipToNext() {
            playbackManager.skipToNext()
        }

        override fun onSeekTo(pos: Long) {
            playbackManager.seekTo(pos.toInt())
        }

        override fun onSetRepeatMode(repeatMode: Int) {
            queueManager.setRepeatMode(repeatMode.toRepeatMode())
        }

        override fun onSetShuffleMode(shuffleMode: Int) {
            queueManager.setShuffleMode(shuffleMode.toShuffleMode())
        }
    }
}