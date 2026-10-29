package com.simplecityapps.shuttle.shared.playback

import com.simplecityapps.playback.dsp.replaygain.ReplayGainMode
import com.simplecityapps.playback.dsp.replaygain.replayGainModeAt
import com.simplecityapps.playback.queue.QueueItem

/**
 * The entries of [order], the play order (shuffle included, so work neighbours are play-order neighbours as on
 * Android's `isWorkMovementAt`), that Track mode plays at album gain. Empty unless [classicalDisplay] is on.
 */
internal fun workAlbumGainEntries(
    order: List<QueueItem>,
    classicalDisplay: Boolean
): List<IosQueueEntry> {
    if (!classicalDisplay) return emptyList()
    val songs = order.map { it.song }
    return order.filterIndexed { index, _ -> replayGainModeAt(ReplayGainMode.Track, songs, index) == ReplayGainMode.Album }
        .map { IosQueueEntry(it.uid, it.song.id) }
}
