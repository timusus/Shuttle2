package com.simplecityapps.playback.queue

import com.simplecityapps.shuttle.model.Song
import kotlin.uuid.Uuid

class QueueItem(
    val uid: Long,
    val song: Song,
    val isCurrent: Boolean
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is QueueItem) return false

        if (uid != other.uid) return false

        return true
    }

    override fun hashCode(): Int = uid.hashCode()

    override fun toString(): String = "QueueItem(song=${song.name}, isCurrent=$isCurrent)"
}

fun Song.toQueueItem(isCurrent: Boolean): QueueItem = QueueItem(Uuid.random().toLongs { mostSignificantBits, _ -> mostSignificantBits } and Long.MAX_VALUE, this, isCurrent)

fun QueueItem.clone(
    uid: Long = this.uid,
    song: Song = this.song,
    isCurrent: Boolean = this.isCurrent
): QueueItem = QueueItem(uid, song, isCurrent)
