package com.simplecityapps.shuttle.ui.screens.queue

import com.simplecityapps.playback.queue.QueueItem
import com.simplecityapps.playback.queue.QueueResult
import com.simplecityapps.shuttle.ui.common.mvp.BaseContract

interface QueueContract {

    interface Presenter : BaseContract.Presenter<View> {

        fun loadQueue()

        fun onQueueItemClicked(queueItem: QueueItem)

        fun shuffleClicked(enabled: Boolean)

        fun nextClicked()

        fun prevClicked()
    }

    interface View {

        fun setData(queueResult: QueueResult)

        fun toggleEmptyView(empty: Boolean)

        fun toggleLoadingView(loading: Boolean)
    }
}