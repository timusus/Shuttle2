package com.simplecityapps.playback.queue

import com.simplecityapps.shuttle.model.Song
import java.util.*

class QueueItem(
    val uid: Long,
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
    return QueueItem(UUID.randomUUID().mostSignificantBits and Long.MAX_VALUE, this, isCurrent)
}

fun QueueItem.clone(
    uid: Long = this.uid,
    song: Song = this.song,
    isCurrent: Boolean = this.isCurrent
): QueueItem {
    return QueueItem(uid, song, isCurrent)
}
