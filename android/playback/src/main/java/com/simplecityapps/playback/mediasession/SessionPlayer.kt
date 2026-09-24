package com.simplecityapps.playback.mediasession

import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.simplecityapps.playback.PlaybackOperations
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.playback.queue.queueEntryOrNull
import com.simplecityapps.playback.queue.toRepeatMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * The player the media session publishes: the app's own [Player], with every command a controller (the notification,
 * Android Auto, a Bluetooth headset, Assistant) sends routed through [PlaybackOperations] and [QueueOperations], the
 * way the app's own screens send them. State passes straight through; nothing is copied.
 *
 * The commands can't go to the player directly: playing takes audio focus and restores the saved position, skipping
 * honours S2's own repeat and restart-the-song rules, shuffling reorders the queue through S2's shuffle order, and a
 * new queue is built from songs so each item carries its [com.simplecityapps.playback.queue.QueueEntry] (the tag
 * replay gain and the queue read) and is written to the player that owns the queue while casting.
 *
 * Runs on the main thread, where the session calls it.
 */
class SessionPlayer(
    player: Player,
    private val playbackOperations: PlaybackOperations,
    private val queueOperations: QueueOperations,
    private val scope: CoroutineScope
) : ForwardingPlayer(player) {
    override fun play() {
        playbackOperations.play()
    }

    override fun pause() {
        playbackOperations.pause()
    }

    override fun setPlayWhenReady(playWhenReady: Boolean) {
        if (playWhenReady) playbackOperations.play() else playbackOperations.pause()
    }

    override fun stop() {
        playbackOperations.pause()
    }

    // Playing prepares the player, at the saved position (see PlaybackOperations.play); preparing it first would start
    // it from wherever the player happens to be.
    override fun prepare() {}

    override fun seekToNext() {
        playbackOperations.skipToNext(ignoreRepeat = true)
    }

    override fun seekToNextMediaItem() {
        playbackOperations.skipToNext(ignoreRepeat = true)
    }

    override fun seekToPrevious() {
        playbackOperations.skipToPrev()
    }

    override fun seekToPreviousMediaItem() {
        playbackOperations.skipToPrev(force = true)
    }

    override fun seekTo(positionMs: Long) {
        playbackOperations.seekTo(positionMs.toInt())
    }

    override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
        if (mediaItemIndex == currentMediaItemIndex) {
            if (positionMs != C.TIME_UNSET) playbackOperations.seekTo(positionMs.toInt())
            return
        }
        // The session counts in playlist order; the queue counts in the order it's presented, which shuffle changes.
        val uid = getMediaItemAt(mediaItemIndex).queueEntryOrNull?.uid ?: return
        val position = queueOperations.getQueue().indexOfFirst { item -> item.uid == uid }
        if (position != -1) playbackOperations.skipTo(position)
    }

    override fun seekToDefaultPosition() {
        playbackOperations.seekTo(0)
    }

    override fun seekToDefaultPosition(mediaItemIndex: Int) {
        seekTo(mediaItemIndex, 0)
    }

    override fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) {
        scope.launch {
            queueOperations.setShuffleMode(if (shuffleModeEnabled) QueueManager.ShuffleMode.On else QueueManager.ShuffleMode.Off, reshuffle = true)
        }
    }

    override fun setRepeatMode(repeatMode: Int) {
        queueOperations.setRepeatMode(repeatMode.toRepeatMode())
    }

    override fun setMediaItems(mediaItems: List<MediaItem>) {
        setMediaItems(mediaItems, 0, C.TIME_UNSET)
    }

    override fun setMediaItems(mediaItems: List<MediaItem>, resetPosition: Boolean) {
        setMediaItems(mediaItems, if (resetPosition) 0 else currentMediaItemIndex, C.TIME_UNSET)
    }

    override fun setMediaItem(mediaItem: MediaItem) {
        setMediaItems(listOf(mediaItem), 0, C.TIME_UNSET)
    }

    override fun setMediaItem(mediaItem: MediaItem, startPositionMs: Long) {
        setMediaItems(listOf(mediaItem), 0, startPositionMs)
    }

    override fun setMediaItem(mediaItem: MediaItem, resetPosition: Boolean) {
        setMediaItems(listOf(mediaItem), resetPosition)
    }

    /**
     * The session's callback has already set the queue for a request to play something (see [SessionCallback]), and
     * hands back the player's own items, so those are left alone. Any other items are songs to set as a new queue.
     */
    override fun setMediaItems(mediaItems: List<MediaItem>, startIndex: Int, startPositionMs: Long) {
        if (mediaItems.uids() == currentMediaItems().uids()) {
            if (startIndex != C.INDEX_UNSET && startIndex != currentMediaItemIndex) seekTo(startIndex, startPositionMs)
            return
        }
        val songs = mediaItems.mapNotNull { item -> item.queueEntryOrNull?.song }
        if (songs.size != mediaItems.size) {
            Timber.w("Ignoring ${mediaItems.size} media items: not all of them are songs")
            return
        }
        scope.launch {
            if (queueOperations.setQueue(songs, position = startIndex.coerceIn(0, songs.lastIndex))) {
                playbackOperations.load(seekPosition = startPositionMs.takeIf { it != C.TIME_UNSET }?.toInt()) { result ->
                    result.onFailure { error -> Timber.e(error, "Failed to load the queue a controller set") }
                }
            }
        }
    }

    override fun addMediaItem(mediaItem: MediaItem) {
        addMediaItems(mediaItemCount, listOf(mediaItem))
    }

    override fun addMediaItem(index: Int, mediaItem: MediaItem) {
        addMediaItems(index, listOf(mediaItem))
    }

    override fun addMediaItems(mediaItems: List<MediaItem>) {
        addMediaItems(mediaItemCount, mediaItems)
    }

    /** Adds songs after the current item when asked to put them there, otherwise at the end of the queue. */
    override fun addMediaItems(index: Int, mediaItems: List<MediaItem>) {
        val songs = mediaItems.mapNotNull { item -> item.queueEntryOrNull?.song }
        if (songs.isEmpty()) return
        scope.launch {
            if (mediaItemCount > 0 && index == currentMediaItemIndex + 1) {
                queueOperations.addToNext(songs)
            } else {
                queueOperations.addToQueue(songs)
            }
        }
    }

    override fun removeMediaItem(index: Int) {
        removeMediaItems(index, index + 1)
    }

    override fun removeMediaItems(fromIndex: Int, toIndex: Int) {
        val uids = (fromIndex until toIndex.coerceAtMost(mediaItemCount)).mapNotNull { index -> getMediaItemAt(index).queueEntryOrNull?.uid }.toSet()
        val items = queueOperations.getQueue().filter { item -> item.uid in uids }
        items.forEach(playbackOperations::removeQueueItem)
    }

    /** Moves an item, where the queue's presented order matches the playlist's: with shuffle off. */
    override fun moveMediaItem(currentIndex: Int, newIndex: Int) {
        if (shuffleModeEnabled) {
            Timber.w("Ignoring a controller's move while shuffle is on")
            return
        }
        playbackOperations.moveQueueItem(currentIndex, newIndex)
    }

    override fun moveMediaItems(fromIndex: Int, toIndex: Int, newIndex: Int) {
        if (toIndex - fromIndex == 1) moveMediaItem(fromIndex, newIndex) else Timber.w("Ignoring a controller's move of ${toIndex - fromIndex} items")
    }

    override fun replaceMediaItem(index: Int, mediaItem: MediaItem) {
        Timber.w("Ignoring a controller's replace of item $index")
    }

    override fun replaceMediaItems(fromIndex: Int, toIndex: Int, mediaItems: List<MediaItem>) {
        Timber.w("Ignoring a controller's replace of items $fromIndex until $toIndex")
    }

    override fun clearMediaItems() {
        playbackOperations.clearQueue()
    }

    private fun currentMediaItems(): List<MediaItem> = List(mediaItemCount, ::getMediaItemAt)

    private fun List<MediaItem>.uids(): List<Long?> = map { item -> item.queueEntryOrNull?.uid }
}
