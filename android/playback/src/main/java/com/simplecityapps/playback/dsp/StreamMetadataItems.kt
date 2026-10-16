package com.simplecityapps.playback.dsp

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Timeline
import androidx.media3.common.audio.AudioProcessor

/** The playlist item this flush's stream plays, or null for a flush that doesn't identify one (a bare flush()). */
internal fun AudioProcessor.StreamMetadata.mediaItem(): MediaItem? = windowIndex()?.let { timeline.getWindow(it, Timeline.Window()).mediaItem }

/** The index in [AudioProcessor.StreamMetadata.timeline] of the item this flush's stream plays, or null as for [mediaItem]. */
internal fun AudioProcessor.StreamMetadata.windowIndex(): Int? {
    val periodUid = periodUid ?: return null
    val periodIndex = timeline.getIndexOfPeriod(periodUid)
    if (periodIndex == C.INDEX_UNSET) return null
    return timeline.getPeriod(periodIndex, Timeline.Period()).windowIndex
}
