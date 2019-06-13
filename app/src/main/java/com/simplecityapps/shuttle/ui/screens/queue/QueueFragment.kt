package com.simplecityapps.shuttle.ui.screens.queue

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import au.com.simplecityapps.shuttle.imageloading.glide.GlideImageLoader
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.simplecityapps.adapter.RecyclerAdapter
import com.simplecityapps.adapter.RecyclerListener
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.playback.PlaybackWatcher
import com.simplecityapps.playback.queue.QueueItem
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.dagger.Injectable
import com.simplecityapps.shuttle.ui.common.error.userDescription
import com.simplecityapps.shuttle.ui.common.recyclerview.clearAdapterOnDetach
import com.simplecityapps.shuttle.ui.common.view.multisheet.MultiSheetView
import com.simplecityapps.shuttle.ui.common.view.multisheet.findParentMultiSheetView
import kotlinx.android.synthetic.main.fragment_queue.*
import javax.inject.Inject

class QueueFragment :
    Fragment(),
    Injectable,
    QueueContract.View {

    private lateinit var queueAdapter: RecyclerAdapter

    private lateinit var imageLoader: ArtworkImageLoader

    @Inject lateinit var presenter: QueuePresenter

    @Inject lateinit var playbackWatcher: PlaybackWatcher

    @Inject lateinit var playbackManager: PlaybackManager


    // Lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        queueAdapter = RecyclerAdapter()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_queue, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        imageLoader = GlideImageLoader(this)

        recyclerView.adapter = queueAdapter
        recyclerView.setRecyclerListener(RecyclerListener())

        presenter.bindView(this)

        view.findParentMultiSheetView()?.addSheetStateChangeListener(sheetStateChangeListener)

        toolbar.inflateMenu(R.menu.menu_up_next)
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.scrollToCurrent -> {
                    presenter.scrollToCurrent()
                    return@setOnMenuItemClickListener true
                }
            }
            false
        }
    }

    override fun onDestroyView() {
        presenter.unbindView()
        view.findParentMultiSheetView()?.removeSheetStateChangeListener(sheetStateChangeListener)
        recyclerView.clearAdapterOnDetach()
        super.onDestroyView()
    }


    // QueueContract.View Implementation

    override fun setData(queue: List<QueueItem>, progress: Float, isPlaying: Boolean) {
        queueAdapter.setData(queue.map { queueItem -> QueueBinder(queueItem, imageLoader, playbackManager, playbackWatcher, queueBinderListener) })
    }

    override fun toggleEmptyView(empty: Boolean) {
        emptyLabel.isVisible = empty
    }

    override fun toggleLoadingView(loading: Boolean) {
        progressBar.isVisible = loading
    }

    override fun setQueuePosition(position: Int, total: Int) {
        toolbarSubtitleTextView.text = "${position + 1} of $total"
    }

    override fun showLoadError(error: Error) {
        Toast.makeText(context, error.userDescription(), Toast.LENGTH_LONG).show()
    }

    override fun scrollToPosition(position: Int, fromUser: Boolean) {
        if (fromUser) {
            recyclerView.scrollToPosition(position)
        } else {
            view?.findParentMultiSheetView()?.let { multiSheetView ->
                if (multiSheetView.currentSheet != MultiSheetView.Sheet.SECOND) {
                    recyclerView.scrollToPosition(position)
                }
            }
        }
    }

    // QueueBinder.Listener Implementation

    private val queueBinderListener = object : QueueBinder.Listener {

        override fun onQueueItemClicked(queueItem: QueueItem) {
            presenter.onQueueItemClicked(queueItem)
        }

        override fun onPlayPauseClicked() {
            presenter.togglePlayback()
        }
    }


    // MultiSheetView.SheetStateChangeListener Implementation

    private val sheetStateChangeListener = object : MultiSheetView.SheetStateChangeListener {

        override fun onSheetStateChanged(sheet: Int, state: Int) {
            toolbar.menu.findItem(R.id.scrollToCurrent)?.isVisible = sheet == MultiSheetView.Sheet.SECOND && state == BottomSheetBehavior.STATE_EXPANDED
        }

        override fun onSlide(sheet: Int, slideOffset: Float) {
            if (sheet == MultiSheetView.Sheet.SECOND) {
                toolbarTitleTextView.textSize = 15 + (5 * slideOffset)
            }
        }
    }


    // Static

    companion object {

        const val TAG = "QueueFragment"

        fun newInstance() = QueueFragment()
    }
}