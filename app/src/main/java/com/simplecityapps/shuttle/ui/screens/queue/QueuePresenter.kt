package com.simplecityapps.shuttle.ui.screens.queue

import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.mediaprovider.repository.SongRepository
import com.simplecityapps.playback.PlaybackManager
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
        fun loadQueue()
        fun onQueueItemClicked(queueItem: QueueItem)
        fun togglePlayback()
        fun scrollToCurrent()
        fun moveQueueItem(from: Int, to: Int)
        fun removeFromQueue(queueItem: QueueItem)
        fun exclude(queueItem: QueueItem)
        fun editTags(queueItem: QueueItem)
        fun saveQueueToPlaylist()
    }

    interface View {
        fun setData(queue: List<QueueItem>, progress: Float, isPlaying: Boolean)
        fun toggleEmptyView(empty: Boolean)
        fun toggleLoadingView(loading: Boolean)
        fun setQueuePosition(position: Int, total: Int)
        fun showLoadError(error: Error)
        fun scrollToPosition(position: Int, fromUser: Boolean)
        fun showTagEditor(songs: List<Song>)
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
        loadQueue()
    }

    override fun unbindView() {
        queueWatcher.removeCallback(this)

        super.unbindView()
    }

    override fun loadQueue() {
        view?.toggleEmptyView(queueManager.getQueue().isEmpty())
        view?.setData(
            queueManager.getQueue(),
            (playbackManager.getProgress() ?: 0) / (queueManager.getCurrentItem()?.song?.duration?.toFloat() ?: Float.MAX_VALUE),
            playbackManager.isPlaying()
        )
        view?.setQueuePosition(queueManager.getCurrentPosition() ?: 0, queueManager.getSize())
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


    // QueueBinder.Listener Implementation

    override fun onQueueItemClicked(queueItem: QueueItem) {
        queueManager.setCurrentItem(queueItem)
        playbackManager.loadCurrent(0) { result ->
            result.onSuccess { playbackManager.play() }
            result.onFailure { error -> view?.showLoadError(error as Error) }
        }
    }


    // QueueChangeCallback Implementation

    override fun onQueueChanged() {
        loadQueue()
    }

    override fun onShuffleChanged() {
        loadQueue()
    }

    override fun onQueuePositionChanged(oldPosition: Int?, newPosition: Int?) {
        loadQueue()
        newPosition?.let { position ->
            view?.scrollToPosition(position, false)
        }
    }
}