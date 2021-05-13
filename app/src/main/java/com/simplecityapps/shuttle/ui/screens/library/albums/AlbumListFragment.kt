package com.simplecityapps.shuttle.ui.screens.library.albums

import android.os.Bundle
import android.os.Parcelable
import android.view.*
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import au.com.simplecityapps.shuttle.imageloading.glide.GlideImageLoader
import com.bumptech.glide.integration.recyclerview.RecyclerViewPreloader
import com.bumptech.glide.util.ViewPreloadSizeProvider
import com.simplecityapps.adapter.RecyclerAdapter
import com.simplecityapps.adapter.SpanSizeLookup
import com.simplecityapps.adapter.ViewBinder
import com.simplecityapps.mediaprovider.model.Album
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.mediaprovider.repository.AlbumSortOrder
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.ContextualToolbarHelper
import com.simplecityapps.shuttle.ui.common.TagEditorMenuSanitiser
import com.simplecityapps.shuttle.ui.common.autoCleared
import com.simplecityapps.shuttle.ui.common.dialog.ShowExcludeDialog
import com.simplecityapps.shuttle.ui.common.dialog.TagEditorAlertDialog
import com.simplecityapps.shuttle.ui.common.dialog.artwork.ArtworkEditorDialog
import com.simplecityapps.shuttle.ui.common.error.userDescription
import com.simplecityapps.shuttle.ui.common.recyclerview.GlidePreloadModelProvider
import com.simplecityapps.shuttle.ui.common.recyclerview.GridSpacingItemDecoration
import com.simplecityapps.shuttle.ui.common.recyclerview.SectionedAdapter
import com.simplecityapps.shuttle.ui.common.view.CircularLoadingView
import com.simplecityapps.shuttle.ui.common.view.HorizontalLoadingView
import com.simplecityapps.shuttle.ui.common.view.findToolbarHost
import com.simplecityapps.shuttle.ui.screens.library.ViewMode
import com.simplecityapps.shuttle.ui.screens.library.albums.detail.AlbumDetailFragmentArgs
import com.simplecityapps.shuttle.ui.screens.playlistmenu.CreatePlaylistDialogFragment
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistMenuPresenter
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistMenuView
import com.squareup.phrase.Phrase
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class AlbumListFragment :
    Fragment(),
    AlbumBinder.Listener,
    AlbumListContract.View,
    CreatePlaylistDialogFragment.Listener {

    private var adapter: RecyclerAdapter by autoCleared()

    lateinit var imageLoader: GlideImageLoader

    private var recyclerView: RecyclerView by autoCleared()
    private var circularLoadingView: CircularLoadingView by autoCleared()
    private var horizontalLoadingView: HorizontalLoadingView by autoCleared()

    @Inject
    lateinit var presenter: AlbumListPresenter

    @Inject
    lateinit var playlistMenuPresenter: PlaylistMenuPresenter

    private lateinit var playlistMenuView: PlaylistMenuView

    private var recyclerViewState: Parcelable? = null

    private lateinit var shuffleBinder: ShuffleBinder

    private var contextualToolbarHelper: ContextualToolbarHelper<Album> by autoCleared()

    private val viewPreloadSizeProvider by lazy { ViewPreloadSizeProvider<Album>() }
    private val preloadModelProvider by lazy {
        GlidePreloadModelProvider<Album>(
            imageLoader, listOf(ArtworkImageLoader.Options.CacheDecodedResource)
        )
    }

    // Lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setHasOptionsMenu(true)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_albums, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        imageLoader = GlideImageLoader(this)

        playlistMenuView = PlaylistMenuView(requireContext(), playlistMenuPresenter, childFragmentManager)

        adapter = object : SectionedAdapter(viewLifecycleOwner.lifecycleScope) {
            override fun getSectionName(viewBinder: ViewBinder?): String? {
                return (viewBinder as? AlbumBinder)?.album?.let { album ->
                    presenter.getFastscrollPrefix(album)
                }
            }
        }
        recyclerView = view.findViewById(R.id.recyclerView)
        recyclerView.adapter = adapter
        recyclerView.setItemViewCacheSize(0)
        recyclerView.setHasFixedSize(true)
        val preloader: RecyclerViewPreloader<Album> = RecyclerViewPreloader(
            imageLoader.requestManager,
            preloadModelProvider,
            viewPreloadSizeProvider,
            12
        )
        recyclerView.addOnScrollListener(preloader)

        circularLoadingView = view.findViewById(R.id.circularLoadingView)
        horizontalLoadingView = view.findViewById(R.id.horizontalLoadingView)

        shuffleBinder = ShuffleBinder(R.string.button_album_shuffle, object : ShuffleBinder.Listener {
            override fun onClicked() {
                presenter.albumShuffle()
            }
        })

        savedInstanceState?.getParcelable<Parcelable>(ARG_RECYCLER_STATE)?.let { recyclerViewState = it }

        contextualToolbarHelper = ContextualToolbarHelper()

        updateContextualToolbar()

        presenter.bindView(this)
        playlistMenuPresenter.bindView(playlistMenuView)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)

        inflater.inflate(R.menu.menu_album_list, menu)

        presenter.updateToolbarMenu()
    }

    override fun onResume() {
        super.onResume()

        presenter.loadAlbums(false)

        updateContextualToolbar()

        presenter.updateToolbarMenu()
    }

    override fun onPause() {
        super.onPause()

        findToolbarHost()?.apply {
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


    // Toolbar item selection

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.listViewMode -> {
                adapter.clear()
                presenter.setViewMode(ViewMode.List)
                true
            }
            R.id.gridViewMode -> {
                adapter.clear()
                presenter.setViewMode(ViewMode.Grid)
                true
            }
            R.id.sortAlbumName -> {
                presenter.setSortOrder(AlbumSortOrder.AlbumName)
                true
            }
            R.id.sortArtistName -> {
                presenter.setSortOrder(AlbumSortOrder.ArtistGroupKey)
                true
            }
            R.id.sortAlbumYear -> {
                presenter.setSortOrder(AlbumSortOrder.Year)
                true
            }
            else -> false
        }
    }


    // Private

    private fun updateContextualToolbar() {
        findToolbarHost()?.apply {
            contextualToolbar?.let { contextualToolbar ->
                contextualToolbar.menu.clear()
                contextualToolbar.inflateMenu(R.menu.menu_multi_select)
                TagEditorMenuSanitiser.sanitise(contextualToolbar.menu, contextualToolbarHelper.selectedItems.flatMap { it.mediaProviders }.distinct())
                contextualToolbar.setOnMenuItemClickListener { menuItem ->
                    playlistMenuView.createPlaylistMenu(contextualToolbar.menu)
                    if (playlistMenuView.handleMenuItem(menuItem, PlaylistData.Albums(contextualToolbarHelper.selectedItems.toList()))) {
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
                        R.id.editArtwork -> {
                            presenter.editArtwork(contextualToolbarHelper.selectedItems.toList().first())
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


    // AlbumListContract.View Implementation

    override fun setAlbums(albums: List<Album>, viewMode: ViewMode, resetPosition: Boolean) {
        preloadModelProvider.items = albums

        if (resetPosition) {
            adapter.clear()
        }

        val data = albums.map { album ->
            when (viewMode) {
                ViewMode.Grid -> {
                    GridAlbumBinder(album, imageLoader, this)
                        .apply { selected = contextualToolbarHelper.selectedItems.contains(album) }
                }
                ViewMode.List -> {
                    ListAlbumBinder(album, imageLoader, this)
                        .apply { selected = contextualToolbarHelper.selectedItems.contains(album) }
                }
            }
        }.toMutableList<ViewBinder>()

        if (albums.isNotEmpty()) {
            data.add(0, shuffleBinder)
        }

        adapter.update(data) {
            recyclerViewState?.let {
                recyclerView.layoutManager?.onRestoreInstanceState(recyclerViewState)
                recyclerViewState = null
            }
        }
    }

    override fun updateToolbarMenuSortOrder(sortOrder: AlbumSortOrder) {
        findToolbarHost()?.toolbar?.menu?.let { menu ->
            when (sortOrder) {
                AlbumSortOrder.AlbumName -> menu.findItem(R.id.sortAlbumName)?.isChecked = true
                AlbumSortOrder.ArtistGroupKey -> menu.findItem(R.id.sortArtistName)?.isChecked = true
                AlbumSortOrder.Year -> menu.findItem(R.id.sortAlbumYear)?.isChecked = true
                else -> {
                    // Nothing to do
                }
            }
        }
    }

    override fun updateToolbarMenuViewMode(viewMode: ViewMode) {
        when (viewMode) {
            ViewMode.List -> findToolbarHost()?.toolbar?.menu?.findItem(R.id.listViewMode)?.isChecked = true
            ViewMode.Grid -> findToolbarHost()?.toolbar?.menu?.findItem(R.id.gridViewMode)?.isChecked = true
        }
    }

    override fun onAddedToQueue(albums: List<Album>) {
        Toast.makeText(
            context,
            Phrase.fromPlural(resources, R.plurals.queue_albums_added, albums.size)
                .put("count", albums.size)
                .format(),
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun setLoadingState(state: AlbumListContract.LoadingState) {
        when (state) {
            is AlbumListContract.LoadingState.Scanning -> {
                horizontalLoadingView.setState(HorizontalLoadingView.State.Loading(getString(R.string.library_scan_in_progress)))
                circularLoadingView.setState(CircularLoadingView.State.None)
            }
            is AlbumListContract.LoadingState.Loading -> {
                horizontalLoadingView.setState(HorizontalLoadingView.State.None)
                circularLoadingView.setState(CircularLoadingView.State.Loading(getString(R.string.loading)))
            }
            is AlbumListContract.LoadingState.Empty -> {
                horizontalLoadingView.setState(HorizontalLoadingView.State.None)
                circularLoadingView.setState(CircularLoadingView.State.Empty(getString(R.string.album_list_empty)))
            }
            is AlbumListContract.LoadingState.None -> {
                horizontalLoadingView.setState(HorizontalLoadingView.State.None)
                circularLoadingView.setState(CircularLoadingView.State.None)
            }
        }
    }

    override fun setLoadingProgress(progress: Float) {
        horizontalLoadingView.setProgress(progress)
    }

    override fun showLoadError(error: Error) {
        Toast.makeText(context, error.userDescription(resources), Toast.LENGTH_LONG).show()
    }

    override fun setViewMode(viewMode: ViewMode) {
        when (viewMode) {
            ViewMode.List -> {
                (recyclerView.layoutManager as GridLayoutManager).spanSizeLookup = SpanSizeLookup(adapter, 1)
                (recyclerView.layoutManager as GridLayoutManager).spanCount = 1
                if (recyclerView.itemDecorationCount != 0) {
                    recyclerView.removeItemDecorationAt(0)
                }
            }
            ViewMode.Grid -> {
                (recyclerView.layoutManager as GridLayoutManager).spanCount = 3
                (recyclerView.layoutManager as GridLayoutManager).spanSizeLookup = SpanSizeLookup(adapter, 3)
                if (recyclerView.itemDecorationCount == 0) {
                    recyclerView.addItemDecoration(GridSpacingItemDecoration(8, true, 1))
                }
            }
        }
    }

    override fun showTagEditor(songs: List<Song>) {
        TagEditorAlertDialog.newInstance(songs).show(childFragmentManager)
    }

    override fun showArtworkEditor(album: Album) {
        ArtworkEditorDialog.newInstance(album).show(childFragmentManager)
    }


    // AlbumBinder.Listener Implementation

    override fun onAlbumClicked(album: Album, viewHolder: AlbumBinder.ViewHolder) {
        if (!contextualToolbarHelper.handleClick(album)) {
            if (findNavController().currentDestination?.id != R.id.albumDetailFragment) {
                findNavController().navigate(
                    R.id.action_libraryFragment_to_albumDetailFragment,
                    AlbumDetailFragmentArgs(album).toBundle(),
                    null,
                    FragmentNavigatorExtras(viewHolder.imageView to viewHolder.imageView.transitionName)
                )
            }
        }
    }

    override fun onAlbumLongClicked(album: Album, viewHolder: AlbumBinder.ViewHolder) {
        contextualToolbarHelper.handleLongClick(album)
    }

    override fun onOverflowClicked(view: View, album: Album) {
        val popupMenu = PopupMenu(requireContext(), view)
        popupMenu.inflate(R.menu.menu_popup)
        TagEditorMenuSanitiser.sanitise(popupMenu.menu, album.mediaProviders)

        playlistMenuView.createPlaylistMenu(popupMenu.menu)

        popupMenu.setOnMenuItemClickListener { menuItem ->
            if (playlistMenuView.handleMenuItem(menuItem, PlaylistData.Albums(album))) {
                return@setOnMenuItemClickListener true
            } else {
                when (menuItem.itemId) {
                    R.id.play -> {
                        presenter.play(album)
                        return@setOnMenuItemClickListener true
                    }
                    R.id.queue -> {
                        presenter.addToQueue(listOf(album))
                        return@setOnMenuItemClickListener true
                    }
                    R.id.playNext -> {
                        presenter.playNext(album)
                        return@setOnMenuItemClickListener true
                    }
                    R.id.exclude -> {
                        ShowExcludeDialog(requireContext(), album.name) {
                            presenter.exclude(album)
                        }
                        return@setOnMenuItemClickListener true
                    }
                    R.id.editTags -> {
                        presenter.editTags(listOf(album))
                        return@setOnMenuItemClickListener true
                    }
                }
            }
            false
        }
        popupMenu.show()
    }

    override fun onViewHolderCreated(holder: AlbumBinder.ViewHolder) {
        viewPreloadSizeProvider.setView(holder.imageView)
    }


    // CreatePlaylistDialogFragment.Listener Implementation

    override fun onSave(text: String, playlistData: PlaylistData) {
        playlistMenuView.onSave(text, playlistData)
    }


    // ContextualToolbarHelper.Callback Implementation

    private val contextualToolbarCallback = object : ContextualToolbarHelper.Callback<Album> {

        override fun onCountChanged(count: Int) {
            contextualToolbarHelper.contextualToolbar?.title = Phrase.fromPlural(requireContext(), R.plurals.multi_select_items_selected, count)
                .put("count", count).format()
            contextualToolbarHelper.contextualToolbar?.menu?.let { menu ->
                TagEditorMenuSanitiser.sanitise(menu, contextualToolbarHelper.selectedItems.flatMap { it.mediaProviders }.distinct())
            }
            contextualToolbarHelper.contextualToolbar?.menu?.findItem(R.id.editArtwork)?.isVisible = count == 1
        }

        override fun onItemUpdated(item: Album, isSelected: Boolean) {
            adapter.items
                .filterIsInstance<AlbumBinder>()
                .firstOrNull { it.album.groupKey == item.groupKey }
                ?.let { viewBinder ->
                    viewBinder.selected = isSelected
                    adapter.notifyItemChanged(adapter.items.indexOf(viewBinder))
                }
        }
    }


    // Static

    companion object {

        const val TAG = "AlbumListFragment"

        const val ARG_RECYCLER_STATE = "recycler_state"

        fun newInstance() = AlbumListFragment()
    }
}