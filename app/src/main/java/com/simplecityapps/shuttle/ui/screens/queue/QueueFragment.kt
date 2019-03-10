package com.simplecityapps.shuttle.ui.screens.queue

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import com.simplecityapps.adapter.RecyclerAdapter
import com.simplecityapps.adapter.RecyclerListener
import com.simplecityapps.playback.queue.QueueItem
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.dagger.Injectable
import kotlinx.android.synthetic.main.fragment_queue.*
import javax.inject.Inject

class QueueFragment : Fragment(), Injectable, QueueContract.View {

    private val queueAdapter = RecyclerAdapter()
    private val shuffleQueueAdapter = RecyclerAdapter()
    private val historyAdapter = RecyclerAdapter()

    @Inject lateinit var imageLoader: ArtworkImageLoader

    @Inject lateinit var presenter: QueuePresenter


    // Lifecycle

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_queue, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView1.adapter = queueAdapter
        recyclerView1.setRecyclerListener(RecyclerListener())

        recyclerView2.adapter = shuffleQueueAdapter
        recyclerView2.setRecyclerListener(RecyclerListener())

        presenter.bindView(this)
    }

    override fun onDestroyView() {
        presenter.unbindView()
        super.onDestroyView()
    }


    // QueueContract.View Implementation

    override fun setData(queue: List<QueueItem>, otherQueue: List<QueueItem>) {
        queueAdapter.setData(queue.map { queueItem -> QueueBinder(queueItem, imageLoader, queueBinderListener) })
        shuffleQueueAdapter.setData(otherQueue.map { queueItem -> QueueBinder(queueItem, imageLoader, queueBinderListener) })
    }

    override fun toggleEmptyView(empty: Boolean) {
        emptyLabel.isVisible = empty
    }

    override fun toggleLoadingView(loading: Boolean) {
        progressBar.isVisible = loading
    }


    // QueueBinder.Listener Implementation

    private val queueBinderListener = object : QueueBinder.Listener {
        override fun onQueueItemClicked(queueItem: QueueItem) {
            presenter.onQueueItemClicked(queueItem)
        }
    }


    // Static

    companion object {

        const val TAG = "QueueFragment"

        fun newInstance() = QueueFragment()
    }
}