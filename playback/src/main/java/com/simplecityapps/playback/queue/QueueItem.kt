package com.simplecityapps.playback.queue

import com.simplecityapps.mediaprovider.model.Song
import java.util.*

class QueueItem(
    val uid: String,
    val song: Song,
    val isCurrent: Boolean
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as QueueItem

        if (uid != other.uid) return false

        return true
    }

    override fun hashCode(): Int {
        return uid.hashCode()
    }

    override fun toString(): String {
        return "QueueItem(song=${song.name}, isCurrent=$isCurrent)"
    }
}

fun Song.toQueueItem(isCurrent: Boolean): QueueItem {
    return QueueItem(UUID.randomUUID().toString(), this, isCurrent)
}

fun QueueItem.clone(uid: String = this.uid, song: Song = this.song, isCurrent: Boolean = this.isCurrent): QueueItem {
    return QueueItem(uid, song, isCurrent)
}