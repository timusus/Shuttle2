package com.simplecityapps.shuttle.ui.shell.player

import com.simplecityapps.playback.PlaybackOperations
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.shuttle.model.Song
import javax.inject.Inject

/**
 * A change to the queue from its rows, which name them by uid rather than index, so a queue that changed meanwhile
 * still changes the right row. An edit naming a row that has gone does nothing.
 */
sealed interface QueueEdit {
    /** Plays the row [uid] from its start. */
    data class SkipTo(val uid: Long) : QueueEdit

    /** Moves the row [uid] to just after the row [afterUid], or to the top when null. */
    data class Move(
        val uid: Long,
        val afterUid: Long?,
    ) : QueueEdit

    /** Moves the row [uid] to just after the current one. */
    data class PlayNext(val uid: Long) : QueueEdit

    data class Remove(val uid: Long) : QueueEdit

    /** Puts [song] back as a new row at [index], where a removed row was. */
    data class Reinsert(
        val song: Song,
        val index: Int,
    ) : QueueEdit
}

/** Carries out a [QueueEdit]. */
class EditQueue @Inject constructor(
    private val playbackOperations: PlaybackOperations,
    private val queueOperations: QueueOperations,
) {
    suspend operator fun invoke(edit: QueueEdit) {
        val queue = queueOperations.getQueue()
        when (edit) {
            is QueueEdit.SkipTo -> queue.indexOfFirst { it.uid == edit.uid }.takeIf { it >= 0 }?.let(playbackOperations::skipTo)

            is QueueEdit.Move -> queueMove(queue.map { it.uid }, edit.uid, edit.afterUid)?.let { (from, to) -> playbackOperations.moveQueueItem(from, to) }

            is QueueEdit.PlayNext -> {
                val from = queue.indexOfFirst { it.uid == edit.uid }
                val current = queueOperations.getCurrentPosition() ?: return
                if (from < 0 || from == current) return
                playbackOperations.moveQueueItem(from, if (from < current) current else current + 1)
            }

            is QueueEdit.Remove -> queue.firstOrNull { it.uid == edit.uid }?.let(playbackOperations::removeQueueItem)

            // Back in as a new row at the end, then moved to where the old row was.
            is QueueEdit.Reinsert -> {
                playbackOperations.addToQueue(listOf(edit.song))
                val last = queueOperations.getSize() - 1
                if (edit.index < last) playbackOperations.moveQueueItem(last, edit.index)
            }
        }
    }
}

/**
 * The from and to indices that put [uid] just after [afterUid] (at the top when null) in the live
 * queue [uids], or null when either row has gone or the row is already there.
 */
internal fun queueMove(
    uids: List<Long>,
    uid: Long,
    afterUid: Long?,
): Pair<Int, Int>? {
    val from = uids.indexOf(uid).takeIf { it >= 0 } ?: return null
    val to = if (afterUid == null) {
        0
    } else {
        val anchor = uids.indexOf(afterUid).takeIf { it >= 0 } ?: return null
        if (anchor < from) anchor + 1 else anchor
    }
    return (from to to).takeIf { from != to }
}
