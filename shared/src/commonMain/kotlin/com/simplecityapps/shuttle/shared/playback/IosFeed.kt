package com.simplecityapps.shuttle.shared.playback

import com.simplecityapps.playback.queue.QueueItem

/** A queue item as handed to the engine, under an id unique to that handing, as part of the play [playId]. */
internal class IosFeed(
    val id: String,
    var item: QueueItem,
    val playId: String
) {
    /** Handed to the engine (its stream is resolved). */
    var sent = false

    /** Became ready to play (loaded, or moved on to by playing out the one before) since it became current. */
    var ready = false

    var failed = false

    /** Played out and paused at its end ([IosEngineFeeder.pausesAtEnd]): a play moves on to the next item. A seek undoes it. */
    var pausedAtEnd = false

    /** Whether a failure is reported on the failure flow: not when the stream couldn't be resolved. */
    var reportFailure = true

    /**
     * How far into the song the engine's track starts: 0, or where a stream re-opened for a seek starts. The engine
     * counts positions from its track's start, so this is added to every position it reports.
     */
    var offsetMs = 0

    /** Where in the song the engine starts it; a seek before it's handed over moves it (#763). */
    var startMs = 0

    /** Its stream can be resolved again to start at a position ([IosStream.opensAtPosition]). */
    var opensAtPosition = false

    /** The engine can't seek its stream (it said so once), so a seek re-opens the stream at the position. */
    var seeksByReopening = false

    /** What the engine was last handed for it. */
    var handedOver: IosAudioTrack? = null

    /** The stream [handedOver] plays. */
    var stream: IosStream? = null

    /** The engine track for [stream], remembered as what the engine was last handed. */
    fun track(stream: IosStream) = IosAudioTrack(
        id,
        stream.url,
        stream.headers,
        stream.gainDb,
        (item.song.duration - offsetMs).takeIf { it > 0 }?.toLong() ?: -1,
        item.song.bitRate?.takeIf { it > 0 } ?: -1,
        item.song.size.takeIf { it > 0 && offsetMs == 0 } ?: -1
    ).also {
        handedOver = it
        this.stream = stream
    }
}
