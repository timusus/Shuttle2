package com.simplecityapps.playback.fakes

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Timeline

/**
 * A playlist of [items] as ExoPlayer's timeline presents it for local files: one window per item,
 * each with a single period. [periodUid] gives the uid the audio sink reports for an item's stream.
 */
class FakePlaylistTimeline(private val items: List<MediaItem>) : Timeline() {
    private val periodUids = items.indices.map { index -> "period $index" }

    fun periodUid(index: Int): Any = periodUids[index]

    override fun getWindowCount(): Int = items.size

    override fun getWindow(
        windowIndex: Int,
        window: Window,
        defaultPositionProjectionUs: Long
    ): Window = window.set(
        "window $windowIndex",
        items[windowIndex],
        null,
        C.TIME_UNSET,
        C.TIME_UNSET,
        C.TIME_UNSET,
        true,
        false,
        null,
        0,
        C.TIME_UNSET,
        windowIndex,
        windowIndex,
        0
    )

    override fun getPeriodCount(): Int = items.size

    override fun getPeriod(
        periodIndex: Int,
        period: Period,
        setIds: Boolean
    ): Period = period.set(periodUids[periodIndex], periodUids[periodIndex], periodIndex, C.TIME_UNSET, 0)

    override fun getIndexOfPeriod(uid: Any): Int = periodUids.indexOf(uid)

    override fun getUidOfPeriod(periodIndex: Int): Any = periodUids[periodIndex]
}
