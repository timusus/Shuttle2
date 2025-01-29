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
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.simplecityapps.adapter.RecyclerAdapter
import com.simplecityapps.adapter.RecyclerListener
import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.PlaybackWatcher
import com.simplecityapps.playback.queue.QueueItem
import com.simplecityapps.recyclerview_fastscroll.views.FastScrollRecyclerView
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.TagEditorMenuSanitiser
import com.simplecityapps.shuttle.ui.common.autoCleared
import com.simplecityapps.shuttle.ui.common.autoClearedNullable
import com.simplecityapps.shuttle.ui.common.dialog.EditTextAlertDialog
import com.simplecityapps.shuttle.ui.common.dialog.TagEditorAlertDialog
import com.simplecityapps.shuttle.ui.common.dialog.showExcludeDialog
import com.simplecityapps.shuttle.ui.common.error.userDescription
import com.simplecityapps.shuttle.ui.common.recyclerview.ItemTouchHelperCallback
import com.simplecityapps.shuttle.ui.common.view.CircularProgressView
import com.simplecityapps.shuttle.ui.common.view.multisheet.MultiSheetView
import com.simplecityapps.shuttle.ui.common.view.multisheet.findParentMultiSheetView
import com.simplecityapps.shuttle.ui.screens.playlistmenu.CreatePlaylistDialogFragment
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistMenuPresenter
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistMenuView
import com.simplecityapps.shuttle.ui.screens.trial.PromoCodeDialogFragment
import com.simplecityapps.shuttle.ui.screens.trial.TrialDialogFragment
import com.simplecityapps.trial.PromoCodeService
import com.simplecityapps.trial.TrialManager
import com.simplecityapps.trial.TrialState
import com.squareup.phrase.Phrase
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class QueueFragment :
    Fragment(),
    QueueContract.View,
    CreatePlaylistDialogFragment.Listener,
    EditTextAlertDialog.Listener {
    @Inject
    lateinit var imageLoader: ArtworkImageLoader

    private var adapter: RecyclerAdapter? = null
    private var recyclerView: FastScrollRecyclerView? = null

    private var toolbar: Toolbar? by autoClearedNullable()
    private var toolbarTitleTextView: TextView by autoCleared()
    private var toolbarSubtitleTextView: TextView by autoCleared()
    private var progressBar: ProgressBar by autoCleared()
    private var emptyLabel: TextView by autoCleared()

    @Inject
    lateinit var presenter: QueuePresenter

    @Inject
    lateinit var playlistMenuPresenter: PlaylistMenuPresenter

    @Inject
    lateinit var playbackWatcher: PlaybackWatcher

    @Inject
    lateinit var playbackManager: PlaybackManager

    @Inject
    lateinit var trialManager: TrialManager

    @Inject
    lateinit var promoCodeService: PromoCodeService

    private var recyclerViewState: Parcelable? = null

    private lateinit var playlistMenuView: PlaylistMenuView

    private var pendingScrollPosition: Int? = null

    // Lifecycle

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_queue, container, false)

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        playlistMenuView = PlaylistMenuView(requireContext(), playlistMenuPresenter, childFragmentManager)

        adapter = RecyclerAdapter(viewLifecycleOwner.lifecycleScope)

        recyclerView = view.findViewById(R.id.recyclerView)
        recyclerView?.adapter = adapter
        recyclerView?.setRecyclerListener(RecyclerListener())
        itemTouchHelper.attachToRecyclerView(recyclerView)

        toolbarTitleTextView = view.findViewById(R.id.toolbarTitleTextView)
        toolbarSubtitleTextView = view.findViewById(R.id.toolbarSubtitleTextView)
        progressBar = view.findViewById(R.id.progressBar)
        emptyLabel = view.findViewById(R.id.emptyLabel)

        view.findParentMultiSheetView()?.addSheetStateChangeListener(sheetStateChangeListener)

        toolbar = view.findViewById(R.id.toolbar)
        toolbar!!.inflateMenu(R.menu.menu_up_next)
        toolbar!!.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.scrollToCurrent -> {
                    presenter.scrollToCurrent()
                    true
                }

                R.id.playlist -> {
                    playlistMenuView.createPlaylistMenu(toolbar!!.menu)
                    true
                }

                R.id.clearQueue -> {
                    presenter.clearQueue()
                    true
                }

                else -> {
                    playlistMenuView.handleMenuItem(menuItem, PlaylistData.Queue)
                }
            }
        }

        toolbar!!.setOnClickListener {
            view.findParentMultiSheetView()?.let { multiSheetView ->
                if (multiSheetView.currentSheet != MultiSheetView.Sheet.SECOND) {
                    multiSheetView.expandSheet(MultiSheetView.Sheet.SECOND)
                }
            }
        }

        savedInstanceState?.getParcelable<Parcelable>(ARG_RECYCLER_STATE)?.let { recyclerViewState = it }

        presenter.bindView(this)
        playlistMenuPresenter.bindView(playlistMenuView)

        playlistMenuView.createPlaylistMenu(toolbar!!.menu)

        val trialMenuItem = toolbar!!.menu.findItem(R.id.trial)
        trialMenuItem.actionView!!.setOnClickListener {
            TrialDialogFragment.newInstance().show(childFragmentManager)
        }
        viewLifecycleOwner.lifecycleScope.launch {
            trialManager.trialState.collect { trialState ->
                when (trialState) {
                    is TrialState.Pretrial, is TrialState.Paid, is TrialState.Unknown -> {
                        trialMenuItem.isVisible = false
                    }

                    is TrialState.Trial -> {
                        trialMenuItem.isVisible = true
                        val daysRemainingText: TextView = trialMenuItem.actionView!!.findViewById(R.id.daysRemaining)
                        daysRemainingText.text = TimeUnit.MILLISECONDS.toDays(trialState.timeRemaining).toString()
                        val progress: CircularProgressView = trialMenuItem.actionView!!.findViewById(R.id.progress)
                        progress.setProgress((trialState.timeRemaining / trialManager.trialLength.toDouble()).toFloat())
                    }

                    is TrialState.Expired -> {
                        trialMenuItem.isVisible = true
                        val daysRemainingText: TextView = trialMenuItem.actionView!!.findViewById(R.id.daysRemaining)
                        daysRemainingText.text = String.format("%.1fx", trialState.multiplier())
                        val progress: CircularProgressView = trialMenuItem.actionView!!.findViewById(R.id.progress)
                        progress.setProgress(0f)
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()

        recyclerViewState = recyclerView?.layoutManager?.onSaveInstanceState()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putParcelable(ARG_RECYCLER_STATE, recyclerViewState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        presenter.unbindView()
        playlistMenuPresenter.unbindView()

        itemTouchHelper.attachToRecyclerView(null)
        view.findParentMultiSheetView()?.removeSheetStateChangeListener(sheetStateChangeListener)

        recyclerView?.children
            ?.map { child -> recyclerView?.getChildViewHolder(child) }
            ?.filterIsInstance<QueueBinder.ViewHolder>()
            ?.forEach { viewHolder ->
                playbackWatcher.removeCallback(viewHolder)
            }

        adapter = null
        recyclerView = null

        super.onDestroyView()
    }

    private val itemTouchHelper =
        object : ItemTouchHelper(
            object : ItemTouchHelperCallback(
                object : OnItemMoveListener {
                    override fun onItemMoved(
                        from: Int,
                        to: Int
                    ) {
                        presenter.moveQueueItem(from, to)
                    }
                }
            ) {}
        ) {}

    // QueueContract.View Implementation

    override fun setData(
        queue: List<QueueItem>,
        progress: Float,
        playbackState: PlaybackState
    ) {
        val queueItems = queue.map { queueItem -> QueueBinder(queueItem, playbackState, progress, imageLoader, playbackManager, playbackWatcher, queueBinderListener) }
        adapter?.update(
            newList = queueItems
        ) {
            recyclerViewState?.let {
                recyclerView?.layoutManager?.onRestoreInstanceState(recyclerViewState)
                recyclerViewState = null
            }

            pendingScrollPosition?.let {
                recyclerView?.scrollToPosition(it)
                pendingScrollPosition = null
            }
        }
    }

    override fun clearData() {
        adapter?.clear()
    }

    override fun toggleEmptyView(empty: Boolean) {
        emptyLabel.isVisible = empty
    }

    override fun toggleLoadingView(loading: Boolean) {
        progressBar.isVisible = loading
    }

    override fun setQueuePosition(
        position: Int?,
        total: Int
    ) {
        position?.let {
            toolbarSubtitleTextView.text =
                Phrase.from(requireContext(), R.string.queue_position)
                    .put("position", (position + 1).toString())
                    .put("total", total.toString())
                    .format()
        }
    }

    override fun showLoadError(error: Error) {
        Toast.makeText(context, error.userDescription(resources), Toast.LENGTH_LONG).show()
    }

    override fun scrollToPosition(
        position: Int?,
        forceScrollUpdate: Boolean
    ) {
        position?.let {
            if (forceScrollUpdate) {
                recyclerView?.scrollToPosition(position)
                pendingScrollPosition = position
            } else {
                view?.findParentMultiSheetView()?.let { multiSheetView ->
                    if (multiSheetView.currentSheet != MultiSheetView.Sheet.SECOND) {
                        recyclerView?.scrollToPosition(position)
                        pendingScrollPosition = position
                    }
                }
            }
        }
    }

    // QueueBinder.Listener Implementation

    private val queueBinderListener =
        object : QueueBinder.Listener {
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
                viewHolder.viewBinder?.queueItem?.let { queueItem ->
                    val popupMenu = PopupMenu(requireContext(), viewHolder.itemView)
                    popupMenu.inflate(R.menu.menu_queue_item)
                    TagEditorMenuSanitiser.sanitise(popupMenu.menu, listOf(queueItem.song.mediaProvider))
                    popupMenu.menu.findItem(R.id.playNext).isVisible = queueItem.isCurrent == false
                    playlistMenuView.createPlaylistMenu(popupMenu.menu)
                    popupMenu.setOnMenuItemClickListener { menuItem ->
                        when (menuItem.itemId) {
                            R.id.removeFromQueue -> {
                                presenter.removeFromQueue(queueItem)
                                return@setOnMenuItemClickListener true
                            }

                            R.id.playNext -> {
                                presenter.playNext(queueItem)
                                return@setOnMenuItemClickListener true
                            }

                            R.id.exclude -> {
                                showExcludeDialog(requireContext(), queueItem.song.name) {
                                    presenter.exclude(queueItem)
                                }
                                return@setOnMenuItemClickListener true
                            }

                            R.id.editTags -> {
                                presenter.editTags(queueItem)
                                return@setOnMenuItemClickListener true
                            }

                            else -> {
                                playlistMenuView.handleMenuItem(menuItem, PlaylistData.Songs(queueItem.song))
                            }
                        }
                    }
                    popupMenu.show()
                } ?: Timber.e("Failed to show popup menu, queue item null")
            }
        }

    override fun showTagEditor(songs: List<com.simplecityapps.shuttle.model.Song>) {
        TagEditorAlertDialog.newInstance(songs).show(childFragmentManager)
    }

    // MultiSheetView.SheetStateChangeListener Implementation

    private val sheetStateChangeListener =
        object : MultiSheetView.SheetStateChangeListener {
            override fun onSheetStateChanged(
                sheet: Int,
                state: Int
            ) {
                toolbar?.let { toolbar ->
                    toolbar.menu.findItem(R.id.scrollToCurrent)?.isVisible = sheet == MultiSheetView.Sheet.SECOND && state == BottomSheetBehavior.STATE_EXPANDED
                    toolbar.menu.findItem(R.id.playlist)?.isVisible = sheet == MultiSheetView.Sheet.SECOND && state == BottomSheetBehavior.STATE_EXPANDED
                    toolbar.menu.findItem(R.id.clearQueue)?.isVisible = sheet == MultiSheetView.Sheet.SECOND && state == BottomSheetBehavior.STATE_EXPANDED

                    if (sheet == MultiSheetView.Sheet.SECOND && state == BottomSheetBehavior.STATE_EXPANDED) {
                        toolbar.menu.findItem(R.id.trial).isVisible = false
                    }
                    if (sheet == MultiSheetView.Sheet.SECOND && state == BottomSheetBehavior.STATE_COLLAPSED) {
                        if (trialManager.trialState.value is TrialState.Trial || trialManager.trialState.value is TrialState.Expired) {
                            toolbar.menu.findItem(R.id.trial).isVisible = true
                        }
                    }
                }
            }

            override fun onSlide(
                sheet: Int,
                slideOffset: Float
            ) {
                if (sheet == MultiSheetView.Sheet.SECOND) {
                    toolbarTitleTextView.textSize = 15 + (5 * slideOffset)
                }
            }
        }

    // CreatePlaylistDialogFragment.Listener Implementation

    override fun onSave(
        text: String,
        playlistData: PlaylistData
    ) {
        playlistMenuPresenter.createPlaylist(text, playlistData)
    }

    // EditTextAlertDialog.Listener Implementation

    override fun onSave(
        text: String?,
        extra: Parcelable?
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            when (val result = promoCodeService.getPromoCode(text!!)) {
                is NetworkResult.Success -> {
                    PromoCodeDialogFragment.newInstance(result.body.promoCode).show(childFragmentManager)
                }

                is NetworkResult.Failure -> {
                    Toast.makeText(requireContext(), "Failed to retrieve promo code", Toast.LENGTH_LONG).show()
                }
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
