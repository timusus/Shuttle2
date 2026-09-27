package com.simplecityapps.playback.queue

import androidx.media3.common.MediaItem
import com.simplecityapps.shuttle.model.Song

/**
 * A queue built ready to set: the playlist items for [songs] and both the orders it could start in ([order]), so
 * setting it on the main thread only hands them to the player.
 *
 * [NewQueueOrder.position] is an index into [shuffleSongs] when they're given and shuffle is on when it's set, else
 * into [songs]. Without [shuffleSongs], a new shuffled order starts at the item at that position.
 */
internal class PreparedQueue private constructor(
    val songs: List<Song>,
    val shuffleSongs: List<Song>?,
    val items: List<MediaItem>,
    val order: NewQueueOrder
) {
    companion object {
        /** Builds new entries for [songs]. It takes a while for a long queue, so it's best done off the main thread. */
        fun build(
            songs: List<Song>,
            shuffleSongs: List<Song>?,
            position: Int
        ): PreparedQueue = of(songs, songs.map { song -> song.toQueueEntry().toMediaItem() }, shuffleSongs, position)

        /** A queue of [items], built for [songs]. */
        fun of(
            songs: List<Song>,
            items: List<MediaItem>,
            shuffleSongs: List<Song>?,
            position: Int
        ): PreparedQueue = PreparedQueue(songs, shuffleSongs, items, NewQueueOrder.of(songs.map { it.id }, shuffleSongs?.map { it.id }, position))
    }
}
