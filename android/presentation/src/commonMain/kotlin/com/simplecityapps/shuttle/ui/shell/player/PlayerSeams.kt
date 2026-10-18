package com.simplecityapps.shuttle.ui.shell.player

import com.simplecityapps.playback.persistence.NowPlayingSnapshot
import com.simplecityapps.shuttle.model.Song
import kotlinx.coroutines.flow.Flow

/** Whether the Cast framework could start on this device. */
fun interface CastAvailability {
    fun isAvailable(): Boolean
}

/** The song the saved queue was left on (see [NowPlayingSnapshot]), shown until the queue is restored. */
fun interface SavedNowPlaying {
    fun snapshot(): NowPlayingSnapshot?
}

/** A queued song, each time it's skipped because streaming it needs S2 Pro. */
fun interface ObserveGatedServerSkip {
    operator fun invoke(): Flow<Song>
}
