package com.simplecityapps.shuttle.ui.screens.library.songs

import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.simplecityapps.adapter.RecyclerAdapter
import com.simplecityapps.adapter.RecyclerListener
import com.simplecityapps.adapter.ViewBinder
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.mediaprovider.repository.SongSortOrder
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.dagger.Injectable
import com.simplecityapps.shuttle.ui.common.ContextualToolbarHelper
import com.simplecityapps.shuttle.ui.common.autoCleared
import com.simplecityapps.shuttle.ui.common.dialog.TagEditorAlertDialog
import com.simplecityapps.shuttle.ui.common.error.userDescription
import com.simplecityapps.shuttle.ui.common.recyclerview.SectionedAdapter
import com.simplecityapps.shuttle.ui.common.view.CircularLoadingView
import com.simplecityapps.shuttle.ui.common.view.HorizontalLoadingView
import com.simplecityapps.shuttle.ui.common.view.findToolbarHost
import com.simplecityapps.shuttle.ui.screens.playlistmenu.CreatePlaylistDialogFragment
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistMenuPresenter
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistMenuView
import com.simplecityapps.shuttle.ui.screens.songinfo.SongInfoDialogFragment
import javax.inject.Inject

class SongListFragment :
    Fragment(),
    Injectable,
    SongListContract.View,
    CreatePlaylistDialogFragment.Listener {

    @Inject lateinit var presenter: SongListPresenter

    @Inject lateinit var playlistMenuPresenter: PlaylistMenuPresenter

    private var adapter: RecyclerAdapter by autoCleared()

    @Inject lateinit var imageLoader: ArtworkImageLoader

    private lateinit var playlistMenuView: PlaylistMenuView

    private var circularLoadingView: CircularLoadingView by autoCleared()
    private var horizontalLoadingView: HorizontalLoadingView by autoCleared()

    private var recyclerView: RecyclerView by autoCleared()

    private var recyclerViewState: Parcelable? = null

    private var contextualToolbarHelper: ContextualToolbarHelper<Song> by autoCleared()


    // Lifecycle

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_songs, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setHasOptionsMenu(true)

        playlistMenuView = PlaylistMenuView(requireContext(), playlistMenuPresenter, childFragmentManager)

        adapter = object : SectionedAdapter(viewLifecycleOwner.lifecycleScope) {
            override fun getSectionName(viewBinder: ViewBinder?): String {
                return (viewBinder as? SongBinder)?.song?.let { song ->
                    presenter.getFastscrollPrefix(song)
                } ?: ""
            }
        }
        recyclerView = view.findViewById(R.id.recyclerView)
        recyclerView.adapter = adapter
        recyclerView.setRecyclerListener(RecyclerListener())

        circularLoadingView = view.findViewById(R.id.circularLoadingView)
        horizontalLoadingView = view.findViewById(R.id.horizontalLoadingView)

        savedInstanceState?.getParcelable<Parcelable>(ARG_RECYCLER_STATE)?.let { recyclerViewState = it }

        contextualToolbarHelper = ContextualToolbarHelper()

        updateToolbar()

        presenter.bindView(this)
        playlistMenuPresenter.bindView(playlistMenuView)
    }

    override fun onResume() {
        super.onResume()

        presenter.loadSongs(false)

        updateToolbar()

        presenter.updateSortOrder()
    }

    override fun onPause() {
        super.onPause()

        findToolbarHost()?.apply {
            toolbar?.let { toolbar ->
                toolbar.menu?.removeItem(R.id.songSortOrder)
                toolbar.setOnMenuItemClickListener(null)
            }

            contextualToolbar?.setOnMenuItemClickListener(null)
        }

        recyclerViewState = recyclerView.layoutManager?.onSaveInstanceState()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putParcelable(ARG_RECYCLER_STATE, recyclerViewState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        presenter.unbindView()
        playlistMenuPresenter.unbindView()

        super.onDestroyView()
    }


    // Private

    private fun updateToolbar() {
        findToolbarHost()?.apply {
            toolbar?.let { toolbar ->
                toolbar.menu.clear()
                toolbar.inflateMenu(R.menu.menu_song_list)
                toolbar.setOnMenuItemClickListener { menuItem ->
                    when (menuItem.itemId) {
                        R.id.sortSongName -> presenter.setSortOrder(SongSortOrder.SongName)
                        R.id.sortArtistName -> presenter.setSortOrder(SongSortOrder.AlbumArtistName)
                        R.id.sortAlbumName -> presenter.setSortOrder(SongSortOrder.AlbumName)
                        R.id.sortSongYear -> presenter.setSortOrder(SongSortOrder.Year)
                        R.id.sortSongDuration -> presenter.setSortOrder(SongSortOrder.Duration)
                    }
                    false
                }
            }

            contextualToolbar?.let { contextualToolbar ->
                contextualToolbar.menu.clear()
                contextualToolbar.inflateMenu(R.menu.menu_multi_select)
                contextualToolbar.setOnMenuItemClickListener { menuItem ->
                    playlistMenuView.createPlaylistMenu(contextualToolbar.menu)
                    if (playlistMenuView.handleMenuItem(menuItem, PlaylistData.Songs(contextualToolbarHelper.selectedItems.toList()))) {
                        contextualToolbarHelper.hide()
                        return@setOnMenuItemClickListener true
                    }
                    when (menuItem.itemId) {
                        R.id.queue -> {
                            presenter.addToQueue(contextualToolbarHelper.selectedItems.toList())
                            contextualToolbarHelper.hide()
                            true
                        }
                        R.id.editTags -> {
                            TagEditorAlertDialog.newInstance(contextualToolbarHelper.selectedItems.toList()).show(childFragmentManager)
                            contextualToolbarHelper.hide()
                            true
                        }
                        else -> false
                    }
                }
            }
            contextualToolbarHelper.contextualToolbar = contextualToolbar
            contextualToolbarHelper.toolbar = toolbar
            contextualToolbarHelper.callback = contextualToolbarCallback

            if (contextualToolbarHelper.selectedItems.isNotEmpty()) {
                contextualToolbarHelper.show()
            }
        }
    }


    // SongListContract.View Implementation

    override fun setData(songs: List<Song>, resetPosition: Boolean) {
        if (resetPosition) {
            adapter.clear()
        }

        adapter.update(songs.map { song ->
            SongBinder(song, imageLoader, songBinderListener).apply {
                selected = contextualToolbarHelper.selectedItems.contains(song)
            }
        }, completion = {
            recyclerViewState?.let {
                recyclerView.layoutManager?.onRestoreInstanceState(recyclerViewState)
                recyclerViewState = null
            }
        })
    }

    override fun updateSortOrder(sortOrder: SongSortOrder) {
        findToolbarHost()?.toolbar?.menu?.let { menu ->
            when (sortOrder) {
                SongSortOrder.SongName -> menu.findItem(R.id.sortSongName)?.isChecked = true
                SongSortOrder.AlbumArtistName -> menu.findItem(R.id.sortArtistName)?.isChecked = true
                SongSortOrder.AlbumName -> menu.findItem(R.id.sortAlbumName)?.isChecked = true
                SongSortOrder.Year -> menu.findItem(R.id.sortSongYear)?.isChecked = true
                SongSortOrder.Duration -> menu.findItem(R.id.sortSongDuration)?.isChecked = true
                else -> {
                    // Nothing to do
                }
            }
        }
    }

    override fun showLoadError(error: Error) {
        Toast.makeText(context, error.userDescription(), Toast.LENGTH_LONG).show()
    }

    override fun onAddedToQueue(songs: List<Song>) {
        Toast.makeText(context, "${songs.size} song(s) added to queue", Toast.LENGTH_SHORT).show()
    }

    override fun setLoadingState(state: SongListContract.LoadingState) {
        when (state) {
            is SongListContract.LoadingState.Scanning -> {
                horizontalLoadingView.setState(HorizontalLoadingView.State.Loading("Scanning your library"))
                circularLoadingView.setState(CircularLoadingView.State.None)
            }
            is SongListContract.LoadingState.Loading -> {
                horizontalLoadingView.setState(HorizontalLoadingView.State.None)
                circularLoadingView.setState(CircularLoadingView.State.Loading())
            }
            is SongListContract.LoadingState.Empty -> {
                horizontalLoadingView.setState(HorizontalLoadingView.State.None)
                circularLoadingView.setState(CircularLoadingView.State.Empty("No songs"))
            }
            is SongListContract.LoadingState.None -> {
                horizontalLoadingView.setState(HorizontalLoadingView.State.None)
                circularLoadingView.setState(CircularLoadingView.State.None)
            }
        }
    }

    override fun setLoadingProgress(progress: Float) {
        horizontalLoadingView.setProgress(progress)
    }

    override fun showDeleteError(error: Error) {
        Toast.makeText(requireContext(), error.userDescription(), Toast.LENGTH_LONG).show()
    }

    // Private

    private val songBinderListener = object : SongBinder.Listener {

        override fun onSongClicked(song: Song) {
            if (!contextualToolbarHelper.handleClick(song)) {
                presenter.onSongClicked(song)
            }
        }

        override fun onSongLongClicked(song: Song) {
            contextualToolbarHelper.handleLongClick(song)
        }

        override fun onOverflowClicked(view: View, song: Song) {
            val popupMenu = PopupMenu(requireContext(), view)
            popupMenu.inflate(R.menu.menu_popup_song)

            playlistMenuView.createPlaylistMenu(popupMenu.menu)

            if (song.mediaStoreId != null) {
                popupMenu.menu.findItem(R.id.delete)?.isVisible = false
            }

            popupMenu.setOnMenuItemClickListener { menuItem ->
                if (playlistMenuView.handleMenuItem(menuItem, PlaylistData.Songs(song))) {
                    return@setOnMenuItemClickListener true
                } else {
                    when (menuItem.itemId) {
                        R.id.queue -> {
                            presenter.addToQueue(listOf(song))
                            return@setOnMenuItemClickListener true
                        }
                        R.id.playNext -> {
                            presenter.playNext(song)
                            return@setOnMenuItemClickListener true
                        }
                        R.id.songInfo -> {
                            SongInfoDialogFragment.newInstance(song).show(childFragmentManager)
                            return@setOnMenuItemClickListener true
                        }
                        R.id.exclude -> {
                            MaterialAlertDialogBuilder(requireContext())
                                .setTitle("Exclude Song")
                                .setMessage("\"${song.name}\" will be hidden from your library.\n\nYou can view excluded songs in settings.")
                                .setPositiveButton("Exclude") { _, _ ->
                                    presenter.exclude(song)
                                }
                                .setNegativeButton("Cancel", null)
                                .show()
                            return@setOnMenuItemClickListener true
                        }
                        R.id.delete -> {
                            MaterialAlertDialogBuilder(requireContext())
                                .setTitle("Delete Song")
                                .setMessage("\"${song.name}\" will be permanently deleted")
                                .setPositiveButton("Delete") { _, _ ->
                                    presenter.delete(song)
                                }
                                .setNegativeButton("Cancel", null)
                                .show()
                            return@setOnMenuItemClickListener true
                        }
                        R.id.editTags -> {
                            TagEditorAlertDialog.newInstance(listOf(song)).show(childFragmentManager)
                        }
                    }
                }
                false
            }
            popupMenu.show()
        }
    }


    // CreatePlaylistDialogFragment.Listener Implementation

    override fun onSave(text: String, playlistData: PlaylistData) {
        playlistMenuPresenter.createPlaylist(text, playlistData)
    }


    // ContextualToolbarHelper.Callback Implementation

    private val contextualToolbarCallback = object : ContextualToolbarHelper.Callback<Song> {

        override fun onCountChanged(count: Int) {
            contextualToolbarHelper.contextualToolbar?.title = "$count selected"
        }

        override fun onItemUpdated(item: Song, isSelected: Boolean) {
            adapter.items
                .filterIsInstance<SongBinder>()
                .firstOrNull { it.song == item }
                ?.let { viewBinder ->
                    viewBinder.selected = isSelected
                    adapter.notifyItemChanged(adapter.items.indexOf(viewBinder))
                }
        }
    }


    // Static

    companion object {
        const val TAG = "SongListFragment"
        const val ARG_RECYCLER_STATE = "recycler_state"

        fun newInstance() = SongListFragment()
    }
}