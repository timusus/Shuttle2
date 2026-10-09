package com.simplecityapps.shuttle.shared.playback

import com.simplecityapps.playback.queue.QueueItem
import com.simplecityapps.playback.queue.QueueModel
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.playback.queue.QueueState
import com.simplecityapps.playback.queue.RepeatMode
import com.simplecityapps.playback.queue.ShuffleMode
import com.simplecityapps.shuttle.model.PlayContext
import com.simplecityapps.shuttle.model.Song
import kotlinx.coroutines.flow.StateFlow

/**
 * [QueueOperations] over [queue], changed on the main thread ([main]); every change is handed on to the engine through
 * [sync], and a removal that plays the queue out through [syncPlayedOut].
 */
internal class IosQueueOperations(
    private val queue: QueueModel,
    private val main: IosMainThread,
    private val retainShuffleOnNewQueue: () -> Boolean,
    private val sync: () -> Unit,
    private val syncPlayedOut: () -> Unit
) : QueueOperations {
    override val queueStateFlow: StateFlow<QueueState> = queue.queueStateFlow

    override val shuffleModeFlow: StateFlow<ShuffleMode> = queue.shuffleModeFlow

    override val repeatModeFlow: StateFlow<RepeatMode> = queue.repeatModeFlow

    override val playContext: PlayContext get() = queue.playContext

    override var hasRestoredQueue: Boolean
        get() = queue.queueStateFlow.value.isRestored
        set(value) = main.run { queue.isRestored = value }

    override suspend fun setQueue(
        songs: List<Song>,
        shuffleSongs: List<Song>?,
        position: Int,
        context: PlayContext
    ): Boolean = main.call {
        queue.playContext = context
        queue.setQueue(songs, shuffleSongs, position, retainShuffle = retainShuffleOnNewQueue()).also { sync() }
    }

    override fun getQueue(): List<QueueItem> = queueStateFlow.value.items

    override fun getQueue(shuffleMode: ShuffleMode): List<QueueItem> = queue.lists.get(shuffleMode)

    override fun getCurrentItem(): QueueItem? = queueStateFlow.value.currentItem

    override fun getCurrentPosition(): Int? = queueStateFlow.value.currentPosition

    override fun getSize(): Int = queue.lists.base.size

    override fun setCurrentItem(currentItem: QueueItem) = main.run {
        queue.setCurrent(currentItem.uid)
        sync()
    }

    /** The item after the current one, as playback would play it. [ignoreRepeat] treats the repeat mode as [RepeatMode.All]. */
    override fun getNext(ignoreRepeat: Boolean): QueueItem? = queue.next(if (ignoreRepeat) RepeatMode.All else queue.repeatMode)

    override fun getPrevious(): QueueItem? = queue.previous()

    override fun skipToNext(ignoreRepeat: Boolean): Boolean {
        val next = getNext(ignoreRepeat) ?: return false
        setCurrentItem(next)
        return true
    }

    override fun skipToPrevious() {
        getPrevious()?.let(::setCurrentItem)
    }

    override fun skipTo(position: Int) {
        getQueue().getOrNull(position)?.let(::setCurrentItem)
    }

    override suspend fun addToQueue(songs: List<Song>): Boolean = main.call {
        queue.add(songs, retainShuffleOnNewQueue()).also { sync() }
    }

    override suspend fun addToNext(songs: List<Song>): Boolean = main.call {
        queue.addNext(songs, retainShuffleOnNewQueue()).also { sync() }
    }

    override fun updateSongs(songs: List<Song>) {
        val songsById = songs.associateBy { it.id }
        if (songsById.isEmpty()) return
        main.run {
            queue.updateSongs(songsById)
            sync()
        }
    }

    override fun move(
        from: Int,
        to: Int
    ) = main.run {
        queue.move(from, to)
        sync()
    }

    /** Removes [items]. Removing the current item with every item after it in queue order ends playback, paused. */
    override fun remove(items: List<QueueItem>) {
        val uids = items.map { it.uid }.toSet()
        main.run {
            if (queue.remove(uids)) syncPlayedOut() else sync()
        }
    }

    override fun remove(song: Song) {
        remove(getQueue().filter { it.song.id == song.id })
    }

    override fun clear() = main.run {
        queue.playContext = PlayContext.None
        queue.clear()
        sync()
    }

    override fun getShuffleMode(): ShuffleMode = shuffleModeFlow.value

    override suspend fun setShuffleMode(
        shuffleMode: ShuffleMode,
        reshuffle: Boolean
    ) = main.call {
        queue.setShuffleMode(shuffleMode, reshuffle)
        sync()
    }

    override suspend fun toggleShuffleMode() = when (getShuffleMode()) {
        ShuffleMode.Off -> setShuffleMode(ShuffleMode.On, reshuffle = true)
        ShuffleMode.On -> setShuffleMode(ShuffleMode.Off, reshuffle = false)
    }

    override fun getRepeatMode(): RepeatMode = repeatModeFlow.value

    override fun setRepeatMode(repeatMode: RepeatMode) = main.run {
        queue.setRepeatMode(repeatMode)
        sync()
    }

    override fun toggleRepeatMode() {
        when (getRepeatMode()) {
            RepeatMode.Off -> setRepeatMode(RepeatMode.All)
            RepeatMode.All -> setRepeatMode(RepeatMode.One)
            RepeatMode.One -> setRepeatMode(RepeatMode.Off)
        }
    }
}
