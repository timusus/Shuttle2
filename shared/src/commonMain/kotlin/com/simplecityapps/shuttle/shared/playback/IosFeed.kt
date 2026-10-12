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

    /** Whether a failure is reported on the failure flow: not when the stream couldn't be resolved. */
    var reportFailure = true
}
