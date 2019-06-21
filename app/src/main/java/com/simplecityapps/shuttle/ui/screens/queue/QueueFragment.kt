package com.simplecityapps.shuttle.ui.screens.queue

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
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

    private var queueAdapter = RecyclerAdapter()

    private lateinit var imageLoader: ArtworkImageLoader

    @Inject lateinit var presenter: QueuePresenter

    @Inject lateinit var playbackWatcher: PlaybackWatcher

    @Inject lateinit var playbackManager: PlaybackManager


    // Lifecycle

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_queue, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        imageLoader = GlideImageLoader(this)

        recyclerView.adapter = queueAdapter
        recyclerView.setRecyclerListener(RecyclerListener())
        itemTouchHelper.attachToRecyclerView(recyclerView)

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

    private val itemTouchHelper = object : ItemTouchHelper(object : ItemTouchHelperCallback(queueAdapter, object : OnItemMoveListener {
        override fun onItemMoved(from: Int, to: Int) {
            presenter.moveQueueItem(from, to)
        }
    }) {}) {}


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

        override fun onStartDrag(viewHolder: QueueBinder.ViewHolder) {
            itemTouchHelper.startDrag(viewHolder)
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


open class ItemTouchHelperCallback(
    private val adapter: RecyclerAdapter,
    private val onItemMoveListener: OnItemMoveListener
) : ItemTouchHelper.Callback() {

    interface OnItemMoveListener {
        fun onItemMoved(from: Int, to: Int)
    }

    private var startPosition = -1
    private var endPosition = -1

    override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
        return makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)
    }

    override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
        if (startPosition == -1) {
            startPosition = viewHolder.adapterPosition
        }
        endPosition = target.adapterPosition

        adapter.moveItem(viewHolder.adapterPosition, target.adapterPosition)
        return true
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        if (startPosition != -1 && endPosition != -1) {
            onItemMoveListener.onItemMoved(startPosition, endPosition)
        }

        startPosition = -1
        endPosition = -1
    }
}