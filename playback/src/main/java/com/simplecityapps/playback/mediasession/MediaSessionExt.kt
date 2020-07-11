package com.simplecityapps.playback.mediasession

import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.simplecityapps.playback.queue.QueueItem
import com.simplecityapps.playback.queue.QueueManager

fun Int.toRepeatMode(): QueueManager.RepeatMode {
    return when (this) {
        PlaybackStateCompat.REPEAT_MODE_ALL -> QueueManager.RepeatMode.All
        PlaybackStateCompat.REPEAT_MODE_NONE -> QueueManager.RepeatMode.Off
        PlaybackStateCompat.REPEAT_MODE_ONE -> QueueManager.RepeatMode.One
        else -> QueueManager.RepeatMode.Off
    }
}

@PlaybackStateCompat.RepeatMode
fun QueueManager.RepeatMode.toRepeatMode(): Int {
    return when (this) {
        QueueManager.RepeatMode.All -> PlaybackStateCompat.REPEAT_MODE_ALL
        QueueManager.RepeatMode.Off -> PlaybackStateCompat.REPEAT_MODE_NONE
        QueueManager.RepeatMode.One -> PlaybackStateCompat.REPEAT_MODE_ONE
    }
}

fun Int.toShuffleMode(): QueueManager.ShuffleMode {
    return when (this) {
        PlaybackStateCompat.SHUFFLE_MODE_ALL -> QueueManager.ShuffleMode.On
        PlaybackStateCompat.SHUFFLE_MODE_NONE -> QueueManager.ShuffleMode.Off
        else -> QueueManager.ShuffleMode.Off
    }
}

@PlaybackStateCompat.ShuffleMode
fun QueueManager.ShuffleMode.toShuffleMode(): Int {
    return when (this) {
        QueueManager.ShuffleMode.On -> PlaybackStateCompat.SHUFFLE_MODE_ALL
        QueueManager.ShuffleMode.Off -> PlaybackStateCompat.SHUFFLE_MODE_NONE
    }
}

fun QueueItem.toQueueItem(): MediaSessionCompat.QueueItem {
    val mediaDescription = MediaDescriptionCompat.Builder()
        .setMediaId(song.id.toString())
        .setTitle(song.name)
        .setSubtitle(song.albumArtist)
        .build()
    return MediaSessionCompat.QueueItem(mediaDescription, uid)
}