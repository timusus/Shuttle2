package com.simplecityapps.shuttle.ui.screens.queue

import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.queue.QueueChangeCallback
import com.simplecityapps.playback.queue.QueueItem
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.playback.queue.QueueWatcher
import com.simplecityapps.shuttle.ui.common.mvp.BaseContract
import com.simplecityapps.shuttle.ui.common.mvp.BasePresenter
import kotlinx.coroutines.launch
import javax.inject.Inject

interface QueueContract {

    interface Presenter : BaseContract.Presenter<View> {
        fun onQueueItemClicked(queueItem: QueueItem)
        fun togglePlayback()
        fun scrollToCurrent()
        fun moveQueueItem(from: Int, to: Int)
        fun removeFromQueue(queueItem: QueueItem)
        fun exclude(queueItem: QueueItem)
        fun editTags(queueItem: QueueItem)
        fun saveQueueToPlaylist()
        fun clearQueue()
    }

    interface View {
        fun setData(queue: List<QueueItem>, progress: Float, playbackState: PlaybackState)
        fun toggleEmptyView(empty: Boolean)
        fun toggleLoadingView(loading: Boolean)
        fun setQueuePosition(position: Int?, total: Int)
        fun showLoadError(error: Error)
        fun scrollToPosition(position: Int?, forceScrollUpdate: Boolean)
        fun showTagEditor(songs: List<com.simplecityapps.shuttle.model.Song>)
        fun clearData()
    }
}

class QueuePresenter @Inject constructor(
    private val queueManager: QueueManager,
    private val playbackManager: PlaybackManager,
    private val queueWatcher: QueueWatcher,
    private val songRepository: SongRepository,
) : BasePresenter<QueueContract.View>(),
    QueueContract.Presenter,
    QueueChangeCallback {

    override fun bindView(view: QueueContract.View) {
        super.bindView(view)

        queueWatcher.addCallback(this)
        updateQueue(true)
        updateQueuePosition(true)
    }

    override fun unbindView() {
        queueWatcher.removeCallback(this)

        super.unbindView()
    }

    private fun updateQueue(forceClear: Boolean) {
        if (forceClear) {
            view?.clearData()
        }
        view?.setData(
            queue = queueManager.getQueue(),
            progress = (playbackManager.getProgress() ?: 0) / (queueManager.getCurrentItem()?.song?.duration?.toFloat() ?: Float.MAX_VALUE),
            playbackState = playbackManager.playbackState()
        )
    }

    private fun updateQueuePosition(forceScrollUpdate: Boolean) {
        view?.setQueuePosition(queueManager.getCurrentPosition(), queueManager.getSize())
        view?.scrollToPosition(queueManager.getCurrentPosition(), forceScrollUpdate)
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

    override fun moveQueueItem(from: Int, to: Int) {
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


    // QueueChangeCallback Implementation

    override fun onQueueRestored() {
        updateQueue(true)
        updateQueuePosition(true)
        updateMiniPlayerVisibility(queueManager.getQueue().isEmpty())
    }

    override fun onQueueChanged(reason: QueueChangeCallback.QueueChangeReason) {
        updateQueue(reason != QueueChangeCallback.QueueChangeReason.Move)
        updateQueuePosition(reason != QueueChangeCallback.QueueChangeReason.Move)
        updateMiniPlayerVisibility(queueManager.getQueue().isEmpty())
    }

    override fun onQueuePositionChanged(oldPosition: Int?, newPosition: Int?) {
        updateQueue(false) // Currently required in order to update current item
        updateQueuePosition(false)
    }
}