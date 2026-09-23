package com.simplecityapps.shuttle.ui.screens.queue

import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.playback.PlaybackOperations
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.queue.QueueItem
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.playback.queue.QueueState
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.ui.common.mvp.BaseContract
import com.simplecityapps.shuttle.ui.common.mvp.BasePresenter
import javax.inject.Inject
import kotlinx.coroutines.launch

interface QueueContract {
    interface Presenter : BaseContract.Presenter<View> {
        fun onQueueItemClicked(queueItem: QueueItem)

        fun togglePlayback()

        fun scrollToCurrent()

        fun moveQueueItem(
            from: Int,
            to: Int
        )

        fun removeFromQueue(queueItem: QueueItem)

        fun exclude(queueItem: QueueItem)

        fun editTags(queueItem: QueueItem)

        fun saveQueueToPlaylist()

        fun clearQueue()
    }

    interface View {
        fun setData(
            queue: List<QueueItem>,
            progress: Float,
            playbackState: PlaybackState
        )

        fun toggleEmptyView(empty: Boolean)

        fun toggleLoadingView(loading: Boolean)

        fun setQueuePosition(
            position: Int?,
            total: Int
        )

        fun showLoadError(error: Error)

        fun scrollToPosition(
            position: Int?,
            forceScrollUpdate: Boolean
        )

        fun showTagEditor(songs: List<com.simplecityapps.shuttle.model.Song>)

        fun clearData()
    }
}

class QueuePresenter
@Inject
constructor(
    private val queueManager: QueueOperations,
    private val playbackManager: PlaybackOperations,
    private val songRepository: SongRepository
) : BasePresenter<QueueContract.View>(),
    QueueContract.Presenter {
    override fun bindView(view: QueueContract.View) {
        super.bindView(view)

        val queueState = queueManager.queueStateFlow.value
        updateQueue(queueState, forceClear = true)
        updateQueuePosition(queueState, forceScrollUpdate = true)
        collectChanges(queueManager.queueStateFlow, queueState, ::onQueueStateChanged)
    }

    private fun updateQueue(
        queueState: QueueState,
        forceClear: Boolean
    ) {
        if (forceClear) {
            view?.clearData()
        }
        view?.setData(
            queue = queueState.items,
            progress = (playbackManager.getProgress() ?: 0) / (queueState.currentItem?.song?.duration?.toFloat() ?: Float.MAX_VALUE),
            playbackState = playbackManager.playbackState()
        )
    }

    private fun updateQueuePosition(
        queueState: QueueState,
        forceScrollUpdate: Boolean
    ) {
        view?.setQueuePosition(queueState.currentPosition, queueState.items.size)
        view?.scrollToPosition(queueState.currentPosition, forceScrollUpdate)
    }

    private fun updateMiniPlayerVisibility(visible: Boolean) {
        view?.toggleEmptyView(visible)
    }

    override fun togglePlayback() {
        playbackManager.togglePlayback()
    }

    override fun scrollToCurrent() {
        queueManager.getCurrentPosition()?.let { position ->
            view?.scrollToPosition(position, true)
        }
    }

    override fun moveQueueItem(
        from: Int,
        to: Int
    ) {
        playbackManager.moveQueueItem(from, to)
    }

    override fun removeFromQueue(queueItem: QueueItem) {
        playbackManager.removeQueueItem(queueItem)
    }

    fun playNext(queueItem: QueueItem) {
        queueManager.getCurrentPosition()?.let { currentPosition ->
            val from = queueManager.getQueue().indexOf(queueItem)
            playbackManager.moveQueueItem(from, currentPosition + if (from < currentPosition) 0 else 1)
        }
    }

    override fun exclude(queueItem: QueueItem) {
        removeFromQueue(queueItem)
        launch {
            songRepository.setExcluded(listOf(queueItem.song), true)
        }
    }

    override fun editTags(queueItem: QueueItem) {
        view?.showTagEditor(listOf(queueItem.song))
    }

    override fun saveQueueToPlaylist() {
        queueManager.getQueue()
    }

    override fun clearQueue() {
        playbackManager.clearQueue()
    }

    // QueueBinder.Listener Implementation

    override fun onQueueItemClicked(queueItem: QueueItem) {
        queueManager.setCurrentItem(queueItem)
        playbackManager.load(0) { result ->
            result.onSuccess { playbackManager.play() }
            result.onFailure { error -> view?.showLoadError(error as Error) }
        }
    }

    // Queue state

    /**
     * A restore or a queue change rebuilds the list and scrolls to the current item, unless the change
     * was a user's drag, which the list already shows. A position change only refreshes the current item.
     * Whether anything but a drag happened is read from [QueueState.nonMoveContentVersion], so a change
     * merged with a later drag into one emission still rebuilds the list.
     */
    private fun onQueueStateChanged(
        previous: QueueState,
        current: QueueState
    ) {
        val restored = current.isRestored && !previous.isRestored
        if (restored || current.contentVersion != previous.contentVersion) {
            val force = restored || current.nonMoveContentVersion != previous.nonMoveContentVersion
            updateQueue(current, force)
            updateQueuePosition(current, force)
            updateMiniPlayerVisibility(current.items.isEmpty())
        } else if (current.currentItem != previous.currentItem || current.currentPosition != previous.currentPosition) {
            updateQueue(current, forceClear = false) // Currently required in order to update current item
            updateQueuePosition(current, forceScrollUpdate = false)
        }
    }
}
