package com.simplecityapps.shuttle.shared.playback

import com.simplecityapps.playback.queue.QueueItem

/** A queue item as the player has it, reported under [id] (its uid), as part of the play [playId]. */
internal class IosFeed(
    val id: String,
    var item: QueueItem,
    val playId: String
) {
    /** Became ready to play (the player said it plays or is paused on it) since it became current. */
    var ready = false

    var failed = false

    /** Where the player was told to load it from, until it seeks: loading it there again before it's ready is a no-op. */
    var loadingFrom: Int? = null

    /**
     * Why the resolution the player opened it ahead with failed, before it became current and was asked again: the
     * failure the player reports for it after the transition.
     */
    var failedAhead: Throwable? = null

    /** Whether a failure is reported on the failure flow: not when the stream couldn't be resolved. */
    var reportFailure = true
}
