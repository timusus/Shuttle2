package com.simplecityapps.playback.queue

import com.simplecityapps.shuttle.model.Song

/**
 * A queue built ready to set: [QueueOperations.buildQueue] builds one off the main thread, so setting it with
 * [QueueOperations.setQueueIfContentVersion] on the main thread only hands it to the player. Only the
 * [QueueOperations] that built a queue can set it.
 *
 * [position] is an index into [shuffleSongs] when they're given and shuffle is on when it's set, else into [songs].
 * Without [shuffleSongs], a new shuffled order starts at the item at [position].
 */
interface NewQueue {
    val songs: List<Song>
    val shuffleSongs: List<Song>?
    val position: Int
}
