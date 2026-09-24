package com.simplecityapps.playback.mediasession

import android.support.v4.media.session.PlaybackStateCompat
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.simplecityapps.playback.PositionAnchor
import com.simplecityapps.playback.R
import com.simplecityapps.playback.queue.QueueManager

/**
 * Everything the media session's [PlaybackStateCompat] is derived from.
 *
 * @param activeQueueItemId the queue id of the current item, or [android.support.v4.media.session.MediaSessionCompat.QueueItem.UNKNOWN_ID]
 */
data class MediaSessionPlaybackState(
    val anchor: PositionAnchor,
    val shuffleMode: QueueManager.ShuffleMode,
    val activeQueueItemId: Long
)

data class SessionCustomAction(
    val action: String,
    @StringRes val nameRes: Int,
    @DrawableRes val iconRes: Int
)

/**
 * The [PlaybackStateCompat] the session publishes, and whether the session is active, as plain values so the
 * mapping from [MediaSessionPlaybackState] is testable on the JVM.
 */
data class PublishedPlaybackState(
    val playback: SessionPlaybackState,
    val actions: Long,
    val customActions: List<SessionCustomAction>,
    val activeQueueItemId: Long
)

const val SESSION_PLAYBACK_ACTIONS: Long = (
    PlaybackStateCompat.ACTION_PLAY
        or PlaybackStateCompat.ACTION_PAUSE
        or PlaybackStateCompat.ACTION_SKIP_TO_NEXT
        or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
        or PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM
        or PlaybackStateCompat.ACTION_SEEK_TO
        or PlaybackStateCompat.ACTION_SET_REPEAT_MODE
        or PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE
        or PlaybackStateCompat.ACTION_PREPARE_FROM_SEARCH
        or PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH
        or PlaybackStateCompat.ACTION_PREPARE_FROM_MEDIA_ID
        or PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID
    )

fun MediaSessionPlaybackState.toPublishedPlaybackState() = PublishedPlaybackState(
    playback = anchor.toSessionPlaybackState(),
    actions = SESSION_PLAYBACK_ACTIONS,
    customActions = listOf(shuffleMode.toShuffleCustomAction()),
    activeQueueItemId = activeQueueItemId
)

/** The shuffle toggle, labelled and drawn for what tapping it does. */
private fun QueueManager.ShuffleMode.toShuffleCustomAction() = when (this) {
    QueueManager.ShuffleMode.Off -> SessionCustomAction(MediaSessionManager.ACTION_SHUFFLE, com.simplecityapps.core.R.string.shuffle_on, R.drawable.ic_shuffle_off_black_24dp)
    QueueManager.ShuffleMode.On -> SessionCustomAction(MediaSessionManager.ACTION_SHUFFLE, com.simplecityapps.core.R.string.shuffle_off, R.drawable.ic_shuffle_black_24dp)
}

fun PublishedPlaybackState.toPlaybackStateCompat(getString: (Int) -> String): PlaybackStateCompat = PlaybackStateCompat.Builder()
    .setState(playback.state, playback.positionMs, playback.speed, playback.updateTimeMs)
    .setActions(actions)
    .setActiveQueueItemId(activeQueueItemId)
    .apply {
        customActions.forEach { customAction ->
            addCustomAction(PlaybackStateCompat.CustomAction.Builder(customAction.action, getString(customAction.nameRes), customAction.iconRes).build())
        }
    }
    .build()
