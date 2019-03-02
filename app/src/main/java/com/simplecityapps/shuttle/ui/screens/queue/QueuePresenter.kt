package com.simplecityapps.shuttle.ui.screens.queue

import com.simplecityapps.playback.queue.QueueChangeCallback
import com.simplecityapps.playback.queue.QueueItem
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.shuttle.ui.common.mvp.BasePresenter
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.subscribeBy
import timber.log.Timber
import javax.inject.Inject

class QueuePresenter @Inject constructor(
    private val queueManager: QueueManager
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

        view?.toggleLoadingView(true)

        addDisposable(
            queueManager.getQueue()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeBy(
                    onSuccess = { queueResult ->
                        Timber.i("Queue loaded.. \nItems: ${queueResult.queue.joinToString("\n")}")
                        view?.toggleLoadingView(false)
                        view?.toggleEmptyView(queueResult.queue.isEmpty())
                        view?.setData(queueResult)
                    },
                    onError = { error ->
                        Timber.e(error, "Failed to load queueManager")
                    }
                )
        )
    }

    override fun shuffleClicked(enabled: Boolean) {
        when (queueManager.getShuffleMode()) {
            QueueManager.ShuffleMode.On -> queueManager.setShuffleMode(QueueManager.ShuffleMode.Off)
            QueueManager.ShuffleMode.Off -> queueManager.setShuffleMode(QueueManager.ShuffleMode.On)
        }
    }

    override fun nextClicked() {
        queueManager.getNext()?.let { nextItem ->
            queueManager.setCurrentItem(nextItem)
        } ?: run {
            Timber.i("Failed to retrieve next queue item")
        }
    }

    override fun prevClicked() {
        queueManager.getPrevious()?.let { prevItem ->
            queueManager.setCurrentItem(prevItem)
        } ?: run {
            Timber.i("Failed to retrieve previous queue item")
        }
    }


    // QueueBinder.Listener Implementation

    override fun onQueueItemClicked(queueItem: QueueItem) {
        queueManager.setCurrentItem(queueItem)
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
}