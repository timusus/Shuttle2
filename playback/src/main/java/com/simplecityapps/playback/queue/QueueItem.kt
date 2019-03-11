package com.simplecityapps.playback.queue

import com.simplecityapps.mediaprovider.model.Song
import java.util.*

class QueueItem(
    val uuid: UUID,
    val song: Song,
    val isCurrent: Boolean
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as QueueItem

        if (uuid != other.uuid) return false

        return true
    }

    override fun hashCode(): Int {
        return uuid.hashCode()
    }

    override fun toString(): String {
        return "QueueItem(song=${song.name}, isCurrent=$isCurrent)"
    }
}

fun Song.toQueueItem(isCurrent: Boolean): QueueItem {
    return QueueItem(UUID.randomUUID(), this, isCurrent)
}

fun QueueItem.clone(uid: UUID = this.uuid, song: Song = this.song, isCurrent: Boolean = this.isCurrent): QueueItem {
    return QueueItem(uid, song, isCurrent)
}