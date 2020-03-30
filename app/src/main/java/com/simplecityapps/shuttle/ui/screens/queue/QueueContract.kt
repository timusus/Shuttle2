package com.simplecityapps.shuttle.ui.screens.queue

import com.simplecityapps.playback.queue.QueueItem
import com.simplecityapps.shuttle.ui.common.mvp.BaseContract

interface QueueContract {

    interface Presenter : BaseContract.Presenter<View> {
        fun loadQueue()
        fun onQueueItemClicked(queueItem: QueueItem)
        fun togglePlayback()
        fun scrollToCurrent()
        fun moveQueueItem(from: Int, to: Int)
        fun removeFromQueue(queueItem: QueueItem)
        fun blacklist(queueItem: QueueItem)
    }

    interface View {
        fun setData(queue: List<QueueItem>, progress: Float, isPlaying: Boolean)
        fun toggleEmptyView(empty: Boolean)
        fun toggleLoadingView(loading: Boolean)
        fun setQueuePosition(position: Int, total: Int)
        fun showLoadError(error: Error)
        fun scrollToPosition(position: Int, fromUser: Boolean)
    }
}