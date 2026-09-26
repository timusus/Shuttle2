package com.simplecityapps.playback.queue

import androidx.media3.common.C
import androidx.media3.common.Player

/** The playlist's entries, in unshuffled order. Main thread only. */
internal fun Player.queueEntries(): List<QueueEntry> = List(mediaItemCount) { index -> getMediaItemAt(index).queueEntry }

/** The playlist indices in shuffled order. Main thread only. */
internal fun Player.shuffledIndices(): List<Int> {
    val timeline = currentTimeline
    return generateSequence(timeline.getFirstWindowIndex(true).takeIf { it != C.INDEX_UNSET }) { index ->
        timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, true).takeIf { it != C.INDEX_UNSET }
    }.toList()
}
