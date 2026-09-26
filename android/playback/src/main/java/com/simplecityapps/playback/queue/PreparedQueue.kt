package com.simplecityapps.playback.queue

import androidx.media3.common.MediaItem
import com.simplecityapps.playback.engine.S2ShuffleOrder
import com.simplecityapps.shuttle.model.Song

/**
 * The [NewQueue] this module builds: the playlist items for [songs] and both the orders it could start in, so setting
 * it on the main thread only hands them to the player.
 */
internal class PreparedQueue private constructor(
    override val songs: List<Song>,
    override val shuffleSongs: List<Song>?,
    override val position: Int,
    val items: List<MediaItem>,
    val shuffleOrder: S2ShuffleOrder,
    /**
     * The playlist index [position] names in [shuffleSongs]: the first copy of a saved shuffled song the queue no
     * longer holds, if any, else the start of the shuffled order. Null without [shuffleSongs], or when [position] is
     * out of their range.
     */
    val shuffledIndex: Int?
) : NewQueue {
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
        ): PreparedQueue {
            val songIds = songs.map { it.id }
            val shuffleIds = shuffleSongs?.map { it.id }
            val shuffleOrder =
                if (shuffleIds != null) {
                    S2ShuffleOrder.matching(songIds, shuffleIds)
                } else {
                    S2ShuffleOrder.shuffled(songs.size, firstIndex = position)
                }
            val shuffledIndex = shuffleIds?.takeIf { position in it.indices }?.let {
                S2ShuffleOrder.matchedIndices(songIds, shuffleIds)[position]
                    ?: songIds.indexOf(shuffleIds[position]).takeIf { it != -1 }
                    ?: shuffleOrder.firstIndex
            }
            return PreparedQueue(songs, shuffleSongs, position, items, shuffleOrder, shuffledIndex)
        }
    }
}
