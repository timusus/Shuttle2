package com.simplecityapps.shuttle.ui.screens.library.albumartists

import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.lifecycle.coroutineScope
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import au.com.simplecityapps.shuttle.imageloading.glide.GlideImageLoader
import com.bumptech.glide.integration.recyclerview.RecyclerViewPreloader
import com.bumptech.glide.util.ViewPreloadSizeProvider
import com.simplecityapps.adapter.RecyclerAdapter
import com.simplecityapps.adapter.RecyclerListener
import com.simplecityapps.mediaprovider.model.AlbumArtist
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.dagger.Injectable
import com.simplecityapps.shuttle.ui.common.ContextualToolbarHelper
import com.simplecityapps.shuttle.ui.common.autoCleared
import com.simplecityapps.shuttle.ui.common.autoClearedNullable
import com.simplecityapps.shuttle.ui.common.dialog.TagEditorAlertDialog
import com.simplecityapps.shuttle.ui.common.error.userDescription
import com.simplecityapps.shuttle.ui.common.recyclerview.GridSpacingItemDecoration
import com.simplecityapps.shuttle.ui.common.recyclerview.MyPreloadModelProvider
import com.simplecityapps.shuttle.ui.common.recyclerview.SectionedAdapter
import com.simplecityapps.shuttle.ui.common.recyclerview.clearAdapterOnDetach
import com.simplecityapps.shuttle.ui.common.view.CircularLoadingView
import com.simplecityapps.shuttle.ui.common.view.HorizontalLoadingView
import com.simplecityapps.shuttle.ui.common.view.findToolbarHost
import com.simplecityapps.shuttle.ui.screens.library.ViewMode
import com.simplecityapps.shuttle.ui.screens.library.albumartists.detail.AlbumArtistDetailFragmentArgs
import com.simplecityapps.shuttle.ui.screens.playlistmenu.CreatePlaylistDialogFragment
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistMenuPresenter
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistMenuView
import javax.inject.Inject

class AlbumArtistListFragment :
    Fragment(),
    Injectable,
    AlbumArtistBinder.Listener,
    AlbumArtistListContract.View,
    CreatePlaylistDialogFragment.Listener {

    private var adapter: RecyclerAdapter by autoCleared()

    private var imageLoader: GlideImageLoader by autoCleared()

    private var recyclerView: RecyclerView? by autoClearedNullable()
    private var circularLoadingView: CircularLoadingView by autoCleared()
    private var horizontalLoadingView: HorizontalLoadingView by autoCleared()

    @Inject lateinit var presenter: AlbumArtistListPresenter

    @Inject lateinit var playlistMenuPresenter: PlaylistMenuPresenter

    private lateinit var playlistMenuView: PlaylistMenuView

    private var recyclerViewState: Parcelable? = null

    private val viewPreloadSizeProvider by lazy { ViewPreloadSizeProvider<AlbumArtist>() }
    private val preloadModelProvider by lazy { MyPreloadModelProvider<AlbumArtist>(imageLoader, arrayOf(ArtworkImageLoader.Options.Placeholder(R.drawable.ic_placeholder_artist))) }

    private var contextualToolbarHelper: ContextualToolbarHelper<AlbumArtist> by autoCleared()


    // Lifecycle

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_album_artists, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        playlistMenuView = PlaylistMenuView(requireContext(), playlistMenuPresenter, childFragmentManager)

        imageLoader = GlideImageLoader(this)

        adapter = SectionedAdapter(lifecycle.coroutineScope)
        recyclerView = view.findViewById(R.id.recyclerView)
        recyclerView?.adapter = adapter
        recyclerView?.clearAdapterOnDetach()
        recyclerView?.setRecyclerListener(RecyclerListener())
        val preloader: RecyclerViewPreloader<AlbumArtist> = RecyclerViewPreloader(
            imageLoader.requestManager,
            preloadModelProvider,
            viewPreloadSizeProvider,
            12
        )
        recyclerView?.addOnScrollListener(preloader)
        savedInstanceState?.getParcelable<Parcelable>(ARG_RECYCLER_STATE)?.let { recyclerViewState = it }

        circularLoadingView = view.findViewById(R.id.circularLoadingView)
        horizontalLoadingView = view.findViewById(R.id.horizontalLoadingView)

        contextualToolbarHelper = ContextualToolbarHelper()
        contextualToolbarHelper.callback = contextualToolbarCallback

        updateToolbar()

        presenter.bindView(this)
        playlistMenuPresenter.bindView(playlistMenuView)

        presenter.loadAlbumArtists()
    }

    override fun onResume() {
        super.onResume()

        updateToolbar()
    }

    override fun onPause() {
        super.onPause()

        findToolbarHost()?.apply {
            toolbar?.let { toolbar ->
                toolbar.menu.removeItem(R.id.viewMode)
                toolbar.setOnMenuItemClickListener(null)
            }

            contextualToolbar?.setOnMenuItemClickListener(null)
        }

        recyclerViewState = recyclerView?.layoutManager?.onSaveInstanceState()
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
                toolbar.inflateMenu(R.menu.menu_artist_list)
                toolbar.setOnMenuItemClickListener { menuItem ->
                    when (menuItem.itemId) {
                        R.id.viewMode -> {
                            adapter.clear()
                            presenter.toggleViewMode()
                            true
                        }
                        else -> false
                    }
                }
            }

            contextualToolbar?.let { contextualToolbar ->
                contextualToolbar.menu.clear()
                contextualToolbar.inflateMenu(R.menu.menu_multi_select)
                contextualToolbar.setOnMenuItemClickListener { menuItem ->
                    playlistMenuView.createPlaylistMenu(contextualToolbar.menu)
                    if (playlistMenuView.handleMenuItem(menuItem, PlaylistData.AlbumArtists(contextualToolbarHelper.selectedItems.toList()))) {
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
                            presenter.editTags(contextualToolbarHelper.selectedItems.toList())
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


    // AlbumArtistListContact.View Implementation

    override fun setAlbumArtists(albumArtists: List<AlbumArtist>, viewMode: ViewMode) {

        preloadModelProvider.items = albumArtists

        adapter.update(albumArtists.map { albumArtist ->
            when (viewMode) {
                ViewMode.Grid -> {
                    GridAlbumArtistBinder(albumArtist, imageLoader, this)
                        .apply { selected = contextualToolbarHelper.selectedItems.contains(albumArtist) }
                }
                ViewMode.List -> {
                    ListAlbumArtistBinder(albumArtist, imageLoader, this)
                        .apply { selected = contextualToolbarHelper.selectedItems.contains(albumArtist) }
                }
            }
        }, completion = {
            recyclerViewState?.let {
                recyclerView?.layoutManager?.onRestoreInstanceState(recyclerViewState)
                recyclerViewState = null
            }
        })
    }

    override fun onAddedToQueue(albumArtists: List<AlbumArtist>) {
        Toast.makeText(context, "${albumArtists.size} artist(s) added to queue", Toast.LENGTH_SHORT).show()
    }

    override fun setLoadingState(state: AlbumArtistListContract.LoadingState) {
        when (state) {
            is AlbumArtistListContract.LoadingState.Scanning -> {
                horizontalLoadingView.setState(HorizontalLoadingView.State.Loading("Scanning your library"))
                circularLoadingView.setState(CircularLoadingView.State.None)
            }
            is AlbumArtistListContract.LoadingState.Empty -> {
                horizontalLoadingView.setState(HorizontalLoadingView.State.None)
                circularLoadingView.setState(CircularLoadingView.State.Empty("No album artists"))
            }
            is AlbumArtistListContract.LoadingState.None -> {
                horizontalLoadingView.setState(HorizontalLoadingView.State.None)
                circularLoadingView.setState(CircularLoadingView.State.None)
            }
        }
    }

    override fun setLoadingProgress(progress: Float) {
        horizontalLoadingView.setProgress(progress)
    }

    override fun showLoadError(error: Error) {
        Toast.makeText(context, error.userDescription(), Toast.LENGTH_LONG).show()
    }

    override fun showTagEditor(songs: List<Song>) {
        TagEditorAlertDialog.newInstance(songs).show(childFragmentManager)
    }

    override fun setViewMode(viewMode: ViewMode) {
        when (viewMode) {
            ViewMode.List -> {
                (recyclerView?.layoutManager as GridLayoutManager).spanCount = 1
                if (recyclerView?.itemDecorationCount != 0) {
                    recyclerView?.removeItemDecorationAt(0)
                }
                findToolbarHost()?.toolbar?.menu?.findItem(R.id.viewMode)?.setIcon(R.drawable.ic_grid_outline_24)
            }
            ViewMode.Grid -> {
                (recyclerView?.layoutManager as GridLayoutManager).spanCount = 3
                if (recyclerView?.itemDecorationCount == 0) {
                    recyclerView?.addItemDecoration(GridSpacingItemDecoration(8, true))
                }
                findToolbarHost()?.toolbar?.menu?.findItem(R.id.viewMode)?.setIcon(R.drawable.ic_list_outline_24)
            }
        }
    }


    // AlbumArtistBinder.Listener Implementation

    override fun onAlbumArtistClicked(albumArtist: AlbumArtist, viewHolder: AlbumArtistBinder.ViewHolder) {
        if (!contextualToolbarHelper.handleClick(albumArtist)) {
            if (findNavController().currentDestination?.id != R.id.albumArtistDetailFragment) {
                findNavController().navigate(
                    R.id.action_libraryFragment_to_albumArtistDetailFragment,
                    AlbumArtistDetailFragmentArgs(albumArtist).toBundle(),
                    null,
                    FragmentNavigatorExtras(viewHolder.imageView to viewHolder.imageView.transitionName)
                )
            }
        }
    }

    override fun onAlbumArtistLongClicked(view: View, albumArtist: AlbumArtist) {
        contextualToolbarHelper.handleLongClick(albumArtist)
    }

    override fun onOverflowClicked(view: View, albumArtist: AlbumArtist) {
        val popupMenu = PopupMenu(requireContext(), view)
        popupMenu.inflate(R.menu.menu_popup)

        playlistMenuView.createPlaylistMenu(popupMenu.menu)

        popupMenu.setOnMenuItemClickListener { menuItem ->
            if (playlistMenuView.handleMenuItem(menuItem, PlaylistData.AlbumArtists(albumArtist))) {
                return@setOnMenuItemClickListener true
            } else {
                when (menuItem.itemId) {
                    R.id.play -> {
                        presenter.play(albumArtist)
                        return@setOnMenuItemClickListener true
                    }
                    R.id.queue -> {
                        presenter.addToQueue(listOf(albumArtist))
                        return@setOnMenuItemClickListener true
                    }
                    R.id.playNext -> {
                        presenter.playNext(albumArtist)
                        return@setOnMenuItemClickListener true
                    }
                    R.id.exclude -> {
                        AlertDialog.Builder(requireContext())
                            .setTitle("Exclude Artist")
                            .setMessage("\"${albumArtist.name}\" will be hidden from your library.\n\nYou can view excluded songs in settings.")
                            .setPositiveButton("Exclude") { _, _ ->
                                presenter.exclude(albumArtist)
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                        return@setOnMenuItemClickListener true
                    }
                    R.id.editTags -> {
                        presenter.editTags(listOf(albumArtist))
                        return@setOnMenuItemClickListener true
                    }
                }
            }
            false
        }
        popupMenu.show()
    }

    override fun onViewHolderCreated(holder: AlbumArtistBinder.ViewHolder) {
        viewPreloadSizeProvider.setView(holder.imageView)
    }


    // CreatePlaylistDialogFragment.Listener Implementation

    override fun onSave(text: String, playlistData: PlaylistData) {
        playlistMenuView.onSave(text, playlistData)
    }


    // ContextualToolbarHelper.Callback Implementation

    private val contextualToolbarCallback = object : ContextualToolbarHelper.Callback<AlbumArtist> {

        override fun onCountChanged(count: Int) {
            contextualToolbarHelper.contextualToolbar?.title = "$count selected"
        }

        override fun onItemUpdated(item: AlbumArtist, isSelected: Boolean) {
            adapter.items
                .filterIsInstance<AlbumArtistBinder>()
                .firstOrNull { it.albumArtist == item }
                ?.let { viewBinder ->
                    viewBinder.selected = isSelected
                    adapter.notifyItemChanged(adapter.items.indexOf(viewBinder))
                }
        }
    }


    // Static

    companion object {

        const val TAG = "AlbumArtistListFragment"

        const val ARG_RECYCLER_STATE = "recycler_state"

        fun newInstance() = AlbumArtistListFragment()
    }
}