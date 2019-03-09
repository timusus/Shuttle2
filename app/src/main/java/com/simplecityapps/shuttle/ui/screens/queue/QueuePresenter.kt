package com.simplecityapps.shuttle.ui.screens.queue

import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.playback.queue.QueueChangeCallback
import com.simplecityapps.playback.queue.QueueItem
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.shuttle.ui.common.mvp.BasePresenter
import javax.inject.Inject

class QueuePresenter @Inject constructor(
    private val queueManager: QueueManager,
    private val playbackManager: PlaybackManager
) : BasePresenter<QueueContract.View>(),
    QueueContract.Presenter,
    QueueChangeCallback {

    override fun bindView(view: QueueContract.View) {
        super.bindView(view)

        queueManager.addCallback(this)
        loadQueue()
    }

    override fun unbindView() {
        queueManager.removeCallback(this)

        super.unbindView()
    }

    override fun loadQueue() {
        view?.toggleEmptyView(queueManager.getQueue().isEmpty())
        view?.setData(queueManager.getQueue())
    }

    override fun shuffleClicked(enabled: Boolean) {
        when (queueManager.getShuffleMode()) {
            QueueManager.ShuffleMode.On -> queueManager.setShuffleMode(QueueManager.ShuffleMode.Off)
            QueueManager.ShuffleMode.Off -> queueManager.setShuffleMode(QueueManager.ShuffleMode.On)
        }
    }

    override fun nextClicked() {
        playbackManager.skipToNext()
    }

    override fun prevClicked() {
        playbackManager.skipToPrev()
    }


    // QueueBinder.Listener Implementation

    override fun onQueueItemClicked(queueItem: QueueItem) {
        queueManager.setCurrentItem(queueItem)
        playbackManager.play()
    }


    // QueueChangeCallback Implementation

    override fun onQueueChanged() {
        loadQueue()
    }

    override fun onShuffleChanged() {
        loadQueue()
    }

    override fun onRepeatChanged() {

    }

    override fun onQueuePositionChanged() {
        loadQueue()
    }
}