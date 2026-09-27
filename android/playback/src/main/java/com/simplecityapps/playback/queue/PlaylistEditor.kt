package com.simplecityapps.playback.queue

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.simplecityapps.playback.chromecast.isRemote
import com.simplecityapps.playback.engine.S2ShuffleOrder
import com.simplecityapps.playback.engine.SongUriResolver
import com.simplecityapps.playback.settings.PlaybackSettings
import com.simplecityapps.shuttle.model.Song
import timber.log.Timber

/**
 * Makes each change to the queue on [player]: its playlist, the playlist's [S2ShuffleOrder], the shuffle and repeat
 * modes and the current item. A change made of several player calls runs in a [QueueStatePublisher.batch], so it
 * publishes once. Every new item is handed to [songUriResolver] before the player gets it. Main thread only.
 */
internal class PlaylistEditor(
    private val player: ExoPlayer,
    /** The player the app plays through: [player] itself, or a Cast player around it. */
    private val activePlayer: Player,
    private val playbackSettings: PlaybackSettings,
    private val songUriResolver: SongUriResolver,
    private val publisher: QueueStatePublisher
) {
    /**
     * Where a change to the queue goes. While playing locally, through [activePlayer], so what it reports is current as
     * soon as the change returns (a Cast player learns of changes made to [player] directly only later). While
     * casting, to [player], whose changes are sent on to the receiver. A shuffle order only [player] takes.
     */
    private val writer: Player
        get() = if (activePlayer.isRemote) player else activePlayer

    /**
     * Replaces the playlist with [queue]'s items, unless it already holds those songs, and moves to its position. When
     * it already holds them, it keeps its items and takes the queue's songs' data. Sets the shuffle mode to
     * [shuffleMode] if given, else keeps the player's, which says which order the position is in.
     */
    fun setQueue(
        queue: PreparedQueue,
        shuffleMode: ShuffleMode? = null
    ): Boolean {
        val songs = queue.songs
        val currentMode = player.shuffleModeEnabled.toShuffleMode()
        val index = queue.order.startIndex(shuffleEnabled = (shuffleMode ?: currentMode) == ShuffleMode.On)
        if (index == null) {
            Timber.e("Invalid queue position: ${queue.order.position} (songs.size: ${songs.size}, shuffleSongs.size: ${queue.shuffleSongs?.size})")
            return false
        }

        publisher.batch {
            val newMode = NewQueueOrder.shuffleModeAfter(
                requested = shuffleMode,
                current = currentMode,
                hasSavedShuffle = queue.shuffleSongs != null,
                retainShuffle = playbackSettings.retainShuffleOnNewQueue.value
            )
            if (newMode != currentMode) {
                writer.shuffleModeEnabled = newMode == ShuffleMode.On
            }

            val sameSongs = player.mediaItemCount == songs.size &&
                songs.indices.all { index -> songs[index].id == player.getMediaItemAt(index).queueEntry.song.id }

            if (sameSongs) {
                replaceChanged(songs)
                if (index != player.currentMediaItemIndex) {
                    writer.seekTo(index, 0)
                }
            } else {
                songUriResolver.queued(queue.items)
                writer.setMediaItems(queue.items, index, 0)
            }
            player.setShuffleOrder(S2ShuffleOrder(queue.order.shuffleOrder))
        }

        return player.mediaItemCount != 0
    }

    /** Moves to the item with [uid], if the queue holds it. */
    fun setCurrent(uid: Long) {
        val index = player.queueEntries().indexOfFirst { it.uid == uid }
        if (index != -1 && index != player.currentMediaItemIndex) {
            writer.seekTo(index, 0)
        }
    }

    /** Moves to the start of the item at playlist [index]. */
    fun seekTo(index: Int) {
        writer.seekTo(index, 0)
    }

    /**
     * Adds [items], built for [songs], to the end of the queue: the end of the unshuffled order, and the end of the
     * shuffled order. Added to an empty queue, they're set as a new queue, as [setQueue] sets it, and this returns true.
     */
    fun add(
        songs: List<Song>,
        items: List<MediaItem>
    ): Boolean {
        if (player.mediaItemCount == 0) return setQueue(PreparedQueue.of(songs, items, null, 0))
        songUriResolver.queued(items)
        writer.addMediaItems(items)
        return false
    }

    /**
     * Adds [items], built for [songs], after the current item, in both the unshuffled and the shuffled order. Added to
     * an empty queue, they're set as a new queue, as [setQueue] sets it, and this returns true.
     */
    fun addNext(
        songs: List<Song>,
        items: List<MediaItem>
    ): Boolean {
        if (player.mediaItemCount == 0) return setQueue(PreparedQueue.of(songs, items, null, 0))
        publisher.batch {
            songUriResolver.queued(items)
            val current = player.currentMediaItemIndex
            val shuffled = ShuffleOrder(player.shuffledIndices())
            writer.addMediaItems(current + 1, items)
            // The shuffled order places the new items right after the current one too.
            player.setShuffleOrder(S2ShuffleOrder(shuffled.insertedNext(current, items.size)))
        }
        return false
    }

    /** Gives each item whose song id matches one of [songsById] that song's data, keeping its uid and place. */
    fun updateSongs(songsById: Map<Long, Song>) {
        publisher.batch {
            replaceChanged(player.queueEntries().map { entry -> songsById[entry.song.id] ?: entry.song })
        }
    }

    /**
     * Gives each playlist item the song at its index in [songs], where that differs, keeping its uid and its place in
     * both orders.
     */
    private fun replaceChanged(songs: List<Song>) {
        val shuffled = player.shuffledIndices()
        player.queueEntries().zip(songs).forEachIndexed { index, (entry, song) ->
            if (song != entry.song) {
                val item = QueueEntry(entry.uid, song).toMediaItem()
                songUriResolver.queued(listOf(item))
                writer.replaceMediaItem(index, item)
            }
        }
        // An item whose file changed is replaced by removing and re-adding it, which moves it to the end of the
        // shuffled order, so the order is put back.
        if (player.shuffledIndices() != shuffled) {
            player.setShuffleOrder(S2ShuffleOrder(ShuffleOrder(shuffled)))
        }
    }

    /** Moves an item within the queue as the shuffle mode presents it, leaving the other order as it is. */
    fun move(
        from: Int,
        to: Int
    ) {
        if (player.shuffleModeEnabled) {
            val order = ShuffleOrder(player.shuffledIndices()).movedInOrder(from, to) ?: return
            player.setShuffleOrder(S2ShuffleOrder(order))
        } else {
            writer.moveMediaItem(from, to)
        }
    }

    /** Removes the items with [uids]. */
    fun remove(uids: Set<Long>) {
        publisher.batch {
            // Each player change rebuilds the timeline, so a run of items goes in one call, the last run first.
            val indices = player.queueEntries().withIndex().filter { it.value.uid in uids }.map { it.index }
            val runs = mutableListOf<IntRange>()
            indices.forEach { index ->
                val last = runs.lastOrNull()
                if (last != null && last.last + 1 == index) runs[runs.lastIndex] = last.first..index else runs += index..index
            }
            runs.asReversed().forEach { range -> writer.removeMediaItems(range.first, range.last + 1) }
        }
    }

    fun clear() {
        writer.clearMediaItems()
    }

    /**
     * Sets the shuffle mode. [reshuffle] generates a new shuffled order, starting at the current item, when turning
     * shuffle on.
     */
    fun setShuffleMode(
        shuffleMode: ShuffleMode,
        reshuffle: Boolean
    ) {
        if (player.shuffleModeEnabled.toShuffleMode() == shuffleMode) return
        publisher.batch {
            if (shuffleMode == ShuffleMode.On && reshuffle) {
                val current = player.currentMediaItemIndex.takeIf { player.mediaItemCount > 0 }
                player.setShuffleOrder(S2ShuffleOrder(ShuffleOrder.shuffled(player.mediaItemCount, firstIndex = current)))
            }
            writer.shuffleModeEnabled = shuffleMode == ShuffleMode.On
        }
    }

    fun setRepeatMode(repeatMode: RepeatMode) {
        writer.repeatMode = repeatMode.toPlayerRepeatMode()
    }
}
