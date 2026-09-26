package com.simplecityapps.playback.dsp

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Timeline
import androidx.media3.common.audio.AudioProcessor

/** The playlist item this flush's stream plays, or null for a flush that doesn't identify one (a bare flush()). */
internal fun AudioProcessor.StreamMetadata.mediaItem(): MediaItem? {
    val periodUid = periodUid ?: return null
    val periodIndex = timeline.getIndexOfPeriod(periodUid)
    if (periodIndex == C.INDEX_UNSET) return null
    val windowIndex = timeline.getPeriod(periodIndex, Timeline.Period()).windowIndex
    return timeline.getWindow(windowIndex, Timeline.Window()).mediaItem
}
