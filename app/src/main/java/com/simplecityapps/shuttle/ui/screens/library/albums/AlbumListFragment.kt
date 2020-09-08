package com.simplecityapps.shuttle.ui.screens.library.albums

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
import com.simplecityapps.adapter.SpanSizeLookup
import com.simplecityapps.adapter.ViewBinder
import com.simplecityapps.mediaprovider.model.Album
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.R.id.viewMode
import com.simplecityapps.shuttle.dagger.Injectable
import com.simplecityapps.shuttle.ui.common.autoCleared
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
import com.simplecityapps.shuttle.ui.screens.library.albums.detail.AlbumDetailFragmentArgs
import com.simplecityapps.shuttle.ui.screens.playlistmenu.CreatePlaylistDialogFragment
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistMenuPresenter
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistMenuView
import javax.inject.Inject

class AlbumListFragment :
    Fragment(),
    Injectable,
    AlbumBinder.Listener,
    AlbumListContract.View,
    CreatePlaylistDialogFragment.Listener {

    private lateinit var adapter: RecyclerAdapter

    private var imageLoader: GlideImageLoader by autoCleared()

    private var recyclerView: RecyclerView by autoCleared()
    private var circularLoadingView: CircularLoadingView by autoCleared()
    private var horizontalLoadingView: HorizontalLoadingView by autoCleared()

    @Inject lateinit var presenter: AlbumListPresenter

    @Inject lateinit var playlistMenuPresenter: PlaylistMenuPresenter

    private lateinit var playlistMenuView: PlaylistMenuView

    private var recyclerViewState: Parcelable? = null

    private val viewPreloadSizeProvider by lazy { ViewPreloadSizeProvider<Album>() }
    private val preloadModelProvider by lazy { MyPreloadModelProvider<Album>(imageLoader, arrayOf(ArtworkImageLoader.Options.Placeholder(R.drawable.ic_placeholder_album))) }

    private lateinit var shuffleBinder: ShuffleBinder


    // Lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        adapter = SectionedAdapter(lifecycle.coroutineScope)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_albums, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        playlistMenuView = PlaylistMenuView(requireContext(), playlistMenuPresenter, childFragmentManager)

        imageLoader = GlideImageLoader(this)

        recyclerView = view.findViewById(R.id.recyclerView)
        recyclerView.adapter = adapter
        recyclerView.setRecyclerListener(RecyclerListener())
        recyclerView.clearAdapterOnDetach()
        val preloader: RecyclerViewPreloader<Album> = RecyclerViewPreloader(
            imageLoader.requestManager,
            preloadModelProvider,
            viewPreloadSizeProvider,
            12
        )
        recyclerView.addOnScrollListener(preloader)
        recyclerView.setItemViewCacheSize(0)

        circularLoadingView = view.findViewById(R.id.circularLoadingView)
        horizontalLoadingView = view.findViewById(R.id.horizontalLoadingView)

        presenter.bindView(this)
        playlistMenuPresenter.bindView(playlistMenuView)

        savedInstanceState?.getParcelable<Parcelable>(ARG_RECYCLER_STATE)?.let { recyclerViewState = it }

        shuffleBinder = ShuffleBinder(object : ShuffleBinder.Listener {
            override fun onClicked() {
                presenter.albumShuffle()
            }
        })
    }

    override fun onResume() {
        super.onResume()

        recyclerViewState?.let { recyclerView.layoutManager?.onRestoreInstanceState(recyclerViewState) }

        presenter.loadAlbums()

        findToolbarHost()?.getToolbar()?.let { toolbar ->
            toolbar.inflateMenu(R.menu.menu_album_list)
            toolbar.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    viewMode -> {
                        adapter.clear()
                        presenter.toggleViewMode()
                        true
                    }
                    else -> false
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()

        findToolbarHost()?.getToolbar()?.let { toolbar ->
            toolbar.menu.removeItem(viewMode)
            toolbar.setOnMenuItemClickListener(null)
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


    // AlbumListContract.View Implementation

    override fun setAlbums(albums: List<Album>, viewMode: ViewMode) {
        preloadModelProvider.items = albums

        val data = albums.map { album ->
            when (viewMode) {
                ViewMode.Grid -> GridAlbumBinder(album, imageLoader, this)
                ViewMode.List -> ListAlbumBinder(album, imageLoader, this)
            }
        }.toMutableList<ViewBinder>()

        data.add(0, shuffleBinder)

        adapter.update(data, completion = {
            recyclerViewState?.let {
                recyclerView.layoutManager?.onRestoreInstanceState(recyclerViewState)
                recyclerViewState = null
            }
        })
    }

    override fun onAddedToQueue(album: Album) {
        Toast.makeText(context, "${album.name} added to queue", Toast.LENGTH_SHORT).show()
    }

    override fun setLoadingState(state: AlbumListContract.LoadingState) {
        when (state) {
            is AlbumListContract.LoadingState.Scanning -> {
                horizontalLoadingView.setState(HorizontalLoadingView.State.Loading("Scanning your library"))
                circularLoadingView.setState(CircularLoadingView.State.None)
            }
            is AlbumListContract.LoadingState.Empty -> {
                horizontalLoadingView.setState(HorizontalLoadingView.State.None)
                circularLoadingView.setState(CircularLoadingView.State.Empty("No albums"))
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
        Toast.makeText(context, error.userDescription(), Toast.LENGTH_LONG).show()
    }

    override fun setViewMode(viewMode: ViewMode) {
        when (viewMode) {
            ViewMode.List -> {
                (recyclerView.layoutManager as GridLayoutManager).spanSizeLookup = SpanSizeLookup(adapter, 1)
                (recyclerView.layoutManager as GridLayoutManager).spanCount = 1
                if (recyclerView.itemDecorationCount != 0) {
                    recyclerView.removeItemDecorationAt(0)
                }
                findToolbarHost()?.getToolbar()?.menu?.findItem(R.id.viewMode)?.setIcon(R.drawable.ic_grid_outline_24)
            }
            ViewMode.Grid -> {
                (recyclerView.layoutManager as GridLayoutManager).spanCount = 3
                (recyclerView.layoutManager as GridLayoutManager).spanSizeLookup = SpanSizeLookup(adapter, 3)
                if (recyclerView.itemDecorationCount == 0) {
                    recyclerView.addItemDecoration(GridSpacingItemDecoration(8, true, 1))
                }
                findToolbarHost()?.getToolbar()?.menu?.findItem(R.id.viewMode)?.setIcon(R.drawable.ic_list_outline_24)
            }
        }
    }

    override fun showTagEditor(songs: List<Song>) {
        TagEditorAlertDialog.newInstance(songs).show(childFragmentManager)
    }


    // AlbumBinder.Listener Implementation

    override fun onAlbumClicked(album: Album, viewHolder: AlbumBinder.ViewHolder) {
        findNavController().navigate(
            R.id.action_libraryFragment_to_albumDetailFragment,
            AlbumDetailFragmentArgs(album).toBundle(),
            null,
            FragmentNavigatorExtras(viewHolder.imageView to viewHolder.imageView.transitionName)
        )
    }

    override fun onOverflowClicked(view: View, album: Album) {
        val popupMenu = PopupMenu(requireContext(), view)
        popupMenu.inflate(R.menu.menu_popup)

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
                        presenter.addToQueue(album)
                        return@setOnMenuItemClickListener true
                    }
                    R.id.playNext -> {
                        presenter.playNext(album)
                        return@setOnMenuItemClickListener true
                    }
                    R.id.exclude -> {
                        AlertDialog.Builder(requireContext())
                            .setTitle("Exclude Album")
                            .setMessage("\"${album.name}\" will be hidden from your library.\n\nYou can view excluded songs in settings.")
                            .setPositiveButton("Exclude") { _, _ ->
                                presenter.exclude(album)
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                        return@setOnMenuItemClickListener true
                    }
                    R.id.editTags -> {
                        presenter.editTags(album)
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


    // Static

    companion object {

        const val TAG = "AlbumListFragment"

        const val ARG_RECYCLER_STATE = "recycler_state"

        fun newInstance() = AlbumListFragment()
    }
}