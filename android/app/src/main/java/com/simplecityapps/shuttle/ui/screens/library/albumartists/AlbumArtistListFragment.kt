package com.simplecityapps.shuttle.ui.screens.library.albumartists

import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
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
import com.simplecityapps.adapter.RecyclerListener
import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.ui.common.ContextualToolbarHelper
import com.simplecityapps.shuttle.ui.common.TagEditorMenuSanitiser
import com.simplecityapps.shuttle.ui.common.autoCleared
import com.simplecityapps.shuttle.ui.common.dialog.TagEditorAlertDialog
import com.simplecityapps.shuttle.ui.common.dialog.showExcludeDialog
import com.simplecityapps.shuttle.ui.common.error.userDescription
import com.simplecityapps.shuttle.ui.common.recyclerview.GlidePreloadModelProvider
import com.simplecityapps.shuttle.ui.common.recyclerview.GridSpacingItemDecoration
import com.simplecityapps.shuttle.ui.common.recyclerview.SectionedAdapter
import com.simplecityapps.shuttle.ui.common.view.CircularLoadingView
import com.simplecityapps.shuttle.ui.common.view.HorizontalLoadingView
import com.simplecityapps.shuttle.ui.common.view.findToolbarHost
import com.simplecityapps.shuttle.ui.screens.library.ViewMode
import com.simplecityapps.shuttle.ui.screens.library.albumartists.detail.AlbumArtistDetailFragmentArgs
import com.simplecityapps.shuttle.ui.screens.playlistmenu.CreatePlaylistDialogFragment
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistMenuPresenter
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistMenuView
import com.squareup.phrase.Phrase
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class AlbumArtistListFragment :
    Fragment(),
    AlbumArtistBinder.Listener,
    AlbumArtistListContract.View,
    CreatePlaylistDialogFragment.Listener {
    private var adapter: RecyclerAdapter by autoCleared()

    lateinit var imageLoader: GlideImageLoader

    private var recyclerView: RecyclerView by autoCleared()
    private var circularLoadingView: CircularLoadingView by autoCleared()
    private var horizontalLoadingView: HorizontalLoadingView by autoCleared()

    @Inject
    lateinit var presenter: AlbumArtistListPresenter

    @Inject
    lateinit var playlistMenuPresenter: PlaylistMenuPresenter

    private lateinit var playlistMenuView: PlaylistMenuView

    private var recyclerViewState: Parcelable? = null

    private var contextualToolbarHelper: ContextualToolbarHelper<AlbumArtist> by autoCleared()

    private val viewPreloadSizeProvider by lazy { ViewPreloadSizeProvider<AlbumArtist>() }
    private val preloadModelProvider by lazy {
        GlidePreloadModelProvider<AlbumArtist>(
            imageLoader,
            listOf(ArtworkImageLoader.Options.CacheDecodedResource)
        )
    }

    // Lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_album_artists, container, false)

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        imageLoader = GlideImageLoader(this)

        playlistMenuView = PlaylistMenuView(requireContext(), playlistMenuPresenter, childFragmentManager)

        adapter = SectionedAdapter(viewLifecycleOwner.lifecycleScope)
        recyclerView = view.findViewById(R.id.recyclerView)
        recyclerView.adapter = adapter
        recyclerView.setRecyclerListener(RecyclerListener())
        val preloader: RecyclerViewPreloader<AlbumArtist> =
            RecyclerViewPreloader(
                imageLoader.requestManager,
                preloadModelProvider,
                viewPreloadSizeProvider,
                12
            )
        recyclerView.addOnScrollListener(preloader)

        savedInstanceState?.getParcelable<Parcelable>(ARG_RECYCLER_STATE)?.let { recyclerViewState = it }

        circularLoadingView = view.findViewById(R.id.circularLoadingView)
        horizontalLoadingView = view.findViewById(R.id.horizontalLoadingView)

        contextualToolbarHelper = ContextualToolbarHelper()
        contextualToolbarHelper.callback = contextualToolbarCallback

        updateContextualToolbar()

        presenter.bindView(this)
        playlistMenuPresenter.bindView(playlistMenuView)
    }

    override fun onCreateOptionsMenu(
        menu: Menu,
        inflater: MenuInflater
    ) {
        super.onCreateOptionsMenu(menu, inflater)

        inflater.inflate(R.menu.menu_artist_list, menu)

        presenter.updateToolbarMenu()
    }

    override fun onResume() {
        super.onResume()

        presenter.loadAlbumArtists()

        updateContextualToolbar()
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

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.gridViewMode -> {
            adapter.clear()
            presenter.setViewMode(ViewMode.Grid)
            true
        }
        R.id.listViewMode -> {
            adapter.clear()
            presenter.setViewMode(ViewMode.List)
            true
        }
        else -> false
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

    override fun setAlbumArtists(
        albumArtists: List<AlbumArtist>,
        viewMode: ViewMode
    ) {
        preloadModelProvider.items = albumArtists

        adapter.update(
            albumArtists.map { albumArtist ->
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
            }
        ) {
            recyclerViewState?.let {
                recyclerView.layoutManager?.onRestoreInstanceState(recyclerViewState)
                recyclerViewState = null
            }
        }
    }

    override fun onAddedToQueue(albumArtists: List<AlbumArtist>) {
        Toast.makeText(
            context,
            Phrase.fromPlural(resources, R.plurals.queue_artists_added, albumArtists.size)
                .put("count", albumArtists.size)
                .format(),
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun setLoadingState(state: AlbumArtistListContract.LoadingState) {
        when (state) {
            is AlbumArtistListContract.LoadingState.Scanning -> {
                horizontalLoadingView.setState(HorizontalLoadingView.State.Loading(getString(R.string.library_scan_in_progress)))
                circularLoadingView.setState(CircularLoadingView.State.None)
            }
            is AlbumArtistListContract.LoadingState.Loading -> {
                horizontalLoadingView.setState(HorizontalLoadingView.State.None)
                circularLoadingView.setState(CircularLoadingView.State.Loading(getString(R.string.loading)))
            }
            is AlbumArtistListContract.LoadingState.Empty -> {
                horizontalLoadingView.setState(HorizontalLoadingView.State.None)
                circularLoadingView.setState(CircularLoadingView.State.Empty(getString(R.string.artist_list_empty)))
            }
            is AlbumArtistListContract.LoadingState.None -> {
                horizontalLoadingView.setState(HorizontalLoadingView.State.None)
                circularLoadingView.setState(CircularLoadingView.State.None)
            }
        }
    }

    override fun setLoadingProgress(progress: Progress?) {
        progress?.let {
            horizontalLoadingView.setProgress(progress.asFloat())
        }
    }

    override fun showLoadError(error: Error) {
        Toast.makeText(context, error.userDescription(resources), Toast.LENGTH_LONG).show()
    }

    override fun showTagEditor(songs: List<com.simplecityapps.shuttle.model.Song>) {
        TagEditorAlertDialog.newInstance(songs).show(childFragmentManager)
    }

    override fun setViewMode(viewMode: ViewMode) {
        when (viewMode) {
            ViewMode.List -> {
                findToolbarHost()?.toolbar?.menu?.findItem(R.id.listViewMode)?.isChecked = true
                (recyclerView.layoutManager as GridLayoutManager).spanCount = 1
                if (recyclerView.itemDecorationCount != 0) {
                    recyclerView.removeItemDecorationAt(0)
                }
            }
            ViewMode.Grid -> {
                findToolbarHost()?.toolbar?.menu?.findItem(R.id.gridViewMode)?.isChecked = true
                (recyclerView.layoutManager as GridLayoutManager).spanCount = 3
                if (recyclerView.itemDecorationCount == 0) {
                    recyclerView.addItemDecoration(GridSpacingItemDecoration(8, true))
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

    // AlbumArtistBinder.Listener Implementation

    override fun onAlbumArtistClicked(
        albumArtist: AlbumArtist,
        viewHolder: AlbumArtistBinder.ViewHolder
    ) {
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

    override fun onAlbumArtistLongClicked(
        view: View,
        albumArtist: AlbumArtist
    ) {
        contextualToolbarHelper.handleLongClick(albumArtist)
    }

    override fun onOverflowClicked(
        view: View,
        albumArtist: AlbumArtist
    ) {
        val popupMenu = PopupMenu(requireContext(), view)
        popupMenu.inflate(R.menu.menu_popup)
        TagEditorMenuSanitiser.sanitise(popupMenu.menu, albumArtist.mediaProviders)

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
                        showExcludeDialog(requireContext(), albumArtist.name ?: albumArtist.friendlyArtistName) {
                            presenter.exclude(albumArtist)
                        }
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

    override fun onSave(
        text: String,
        playlistData: PlaylistData
    ) {
        playlistMenuView.onSave(text, playlistData)
    }

    // ContextualToolbarHelper.Callback Implementation

    private val contextualToolbarCallback =
        object : ContextualToolbarHelper.Callback<AlbumArtist> {
            override fun onCountChanged(count: Int) {
                contextualToolbarHelper.contextualToolbar?.title =
                    Phrase.fromPlural(requireContext(), R.plurals.multi_select_items_selected, count)
                        .put("count", count)
                        .format()
                contextualToolbarHelper.contextualToolbar?.menu?.let { menu ->
                    TagEditorMenuSanitiser.sanitise(menu, contextualToolbarHelper.selectedItems.flatMap { albumArtist -> albumArtist.mediaProviders }.distinct())
                }
            }

            override fun onItemUpdated(
                item: AlbumArtist,
                isSelected: Boolean
            ) {
                adapter.let { adapter ->
                    adapter.items
                        .filterIsInstance<AlbumArtistBinder>()
                        .firstOrNull { it.albumArtist.groupKey == item.groupKey }
                        ?.let { viewBinder ->
                            viewBinder.selected = isSelected
                            adapter.notifyItemChanged(adapter.items.indexOf(viewBinder))
                        }
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
