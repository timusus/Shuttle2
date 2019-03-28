package com.simplecityapps.shuttle.ui.screens.queue

import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.playback.queue.QueueChangeCallback
import com.simplecityapps.playback.queue.QueueItem
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.playback.queue.QueueWatcher
import com.simplecityapps.shuttle.ui.common.mvp.BasePresenter
import javax.inject.Inject

class QueuePresenter @Inject constructor(
    private val queueManager: QueueManager,
    private val playbackManager: PlaybackManager,
    private val queueWatcher: QueueWatcher
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
        view?.setData(queueManager.getQueue())
    }


    // QueueBinder.Listener Implementation

    override fun onQueueItemClicked(queueItem: QueueItem) {
        queueManager.setCurrentItem(queueItem)
        playbackManager.loadCurrent()
    }


    // QueueChangeCallback Implementation

    override fun onQueueChanged() {
        loadQueue()
    }

    override fun onShuffleChanged() {
        loadQueue()
    }

    override fun onQueuePositionChanged() {
        loadQueue()

        view?.setQueuePosition(queueManager.getCurrentPosition() ?: 0, queueManager.getSize())
    }
}