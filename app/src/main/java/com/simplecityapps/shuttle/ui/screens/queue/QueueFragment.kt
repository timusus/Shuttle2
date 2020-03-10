package com.simplecityapps.shuttle.ui.screens.queue

import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.Toolbar
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
import com.simplecityapps.shuttle.ui.common.autoCleared
import com.simplecityapps.shuttle.ui.common.error.userDescription
import com.simplecityapps.shuttle.ui.common.recyclerview.clearAdapterOnDetach
import com.simplecityapps.shuttle.ui.common.view.multisheet.MultiSheetView
import com.simplecityapps.shuttle.ui.common.view.multisheet.findParentMultiSheetView
import javax.inject.Inject

class QueueFragment :
    Fragment(),
    Injectable,
    QueueContract.View {

    private lateinit var adapter: RecyclerAdapter

    private var imageLoader: ArtworkImageLoader by autoCleared()

    private var recyclerView: RecyclerView by autoCleared()

    private var toolbar: Toolbar by autoCleared()
    private var toolbarTitleTextView: TextView by autoCleared()
    private var toolbarSubtitleTextView: TextView by autoCleared()
    private var progressBar: ProgressBar by autoCleared()
    private var emptyLabel: TextView by autoCleared()

    @Inject lateinit var presenter: QueuePresenter

    @Inject lateinit var playbackWatcher: PlaybackWatcher

    @Inject lateinit var playbackManager: PlaybackManager

    private var recyclerViewState: Parcelable? = null


    // Lifecycle

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_queue, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = RecyclerAdapter()

        imageLoader = GlideImageLoader(this)

        recyclerView = view.findViewById(R.id.recyclerView)
        recyclerView.adapter = adapter
        recyclerView.setRecyclerListener(RecyclerListener())
        recyclerView.clearAdapterOnDetach()
        itemTouchHelper.attachToRecyclerView(recyclerView)

        toolbarTitleTextView = view.findViewById(R.id.toolbarTitleTextView)
        toolbarSubtitleTextView = view.findViewById(R.id.toolbarSubtitleTextView)
        progressBar = view.findViewById(R.id.progressBar)
        emptyLabel = view.findViewById(R.id.emptyLabel)

        view.findParentMultiSheetView()?.addSheetStateChangeListener(sheetStateChangeListener)

        toolbar = view.findViewById(R.id.toolbar)
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

        savedInstanceState?.getParcelable<Parcelable>(ARG_RECYCLER_STATE)?.let { recyclerViewState = it }

        presenter.bindView(this)
    }

    override fun onResume() {
        super.onResume()

        recyclerViewState?.let { recyclerView.layoutManager?.onRestoreInstanceState(recyclerViewState) }
    }

    override fun onPause() {
        super.onPause()

        recyclerViewState = recyclerView.layoutManager?.onSaveInstanceState()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putParcelable(ARG_RECYCLER_STATE, recyclerViewState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        presenter.unbindView()

        itemTouchHelper.attachToRecyclerView(null)
        view.findParentMultiSheetView()?.removeSheetStateChangeListener(sheetStateChangeListener)

        adapter.dispose()

        super.onDestroyView()
    }

    private val itemTouchHelper = object : ItemTouchHelper(object : ItemTouchHelperCallback(object : OnItemMoveListener {
        override fun onItemMoved(from: Int, to: Int) {
            presenter.moveQueueItem(from, to)
        }
    }) {}) {}


    // QueueContract.View Implementation

    override fun setData(queue: List<QueueItem>, progress: Float, isPlaying: Boolean) {
        adapter.setData(queue.map { queueItem -> QueueBinder(queueItem, imageLoader, playbackManager, playbackWatcher, queueBinderListener) },
            completion = {
                recyclerViewState?.let {
                    recyclerView.layoutManager?.onRestoreInstanceState(recyclerViewState)
                }
            })
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

        override fun onLongPress(viewHolder: QueueBinder.ViewHolder) {
            val popupMenu = PopupMenu(requireContext(), viewHolder.itemView)
            popupMenu.inflate(R.menu.menu_queue_item)
            popupMenu.menu.findItem(R.id.playNext).isVisible = viewHolder.viewBinder?.queueItem?.isCurrent == false
            popupMenu.setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.removeFromQueue -> {
                        presenter.removeFromQueue(viewHolder.viewBinder!!.queueItem)
                        return@setOnMenuItemClickListener true
                    }
                    R.id.playNext -> {
                        presenter.playNext(viewHolder.viewBinder!!.queueItem)
                        return@setOnMenuItemClickListener true
                    }
                }
                false
            }
            popupMenu.show()
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

        const val ARG_RECYCLER_STATE = "recycler_state"

        fun newInstance() = QueueFragment()
    }
}


open class ItemTouchHelperCallback(
    private val onItemMoveListener: OnItemMoveListener
) : ItemTouchHelper.Callback() {

    interface OnItemMoveListener {
        fun onItemMoved(from: Int, to: Int)
    }

    private var startPosition = -1
    private var endPosition = -1

    override fun isLongPressDragEnabled(): Boolean {
        // Long presses are handled separately
        return false
    }

    override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
        return makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)
    }

    override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
        if (startPosition == -1) {
            startPosition = viewHolder.adapterPosition
        }
        endPosition = target.adapterPosition

        (recyclerView.adapter as RecyclerAdapter).moveItem(viewHolder.adapterPosition, target.adapterPosition)
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