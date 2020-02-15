package com.simplecityapps.shuttle.ui.screens.library.albums

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import au.com.simplecityapps.shuttle.imageloading.glide.GlideImageLoader
import com.simplecityapps.adapter.RecyclerAdapter
import com.simplecityapps.adapter.RecyclerListener
import com.simplecityapps.mediaprovider.model.Album
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.dagger.Injectable
import com.simplecityapps.shuttle.ui.common.autoCleared
import com.simplecityapps.shuttle.ui.common.recyclerview.SectionedAdapter
import com.simplecityapps.shuttle.ui.common.recyclerview.clearAdapterOnDetach
import com.simplecityapps.shuttle.ui.common.view.CircularLoadingView
import com.simplecityapps.shuttle.ui.common.view.HorizontalLoadingView
import com.simplecityapps.shuttle.ui.common.view.findToolbarHost
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

    @Inject lateinit var presenter: AlbumListPresenter

    @Inject lateinit var playlistMenuPresenter: PlaylistMenuPresenter

    private var imageLoader: ArtworkImageLoader by autoCleared()

    private lateinit var playlistMenuView: PlaylistMenuView

    private var recyclerView: RecyclerView by autoCleared()

    private var circularLoadingView: CircularLoadingView by autoCleared()
    private var horizontalLoadingView: HorizontalLoadingView by autoCleared()

    // Lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        adapter = SectionedAdapter()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_albums, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        playlistMenuView = PlaylistMenuView(context!!, playlistMenuPresenter, childFragmentManager)

        imageLoader = GlideImageLoader(this)

        recyclerView = view.findViewById(R.id.recyclerView)
        recyclerView.adapter = adapter
        recyclerView.setRecyclerListener(RecyclerListener())
        recyclerView.clearAdapterOnDetach()

        circularLoadingView = view.findViewById(R.id.circularLoadingView)
        horizontalLoadingView = view.findViewById(R.id.horizontalLoadingView)

        presenter.bindView(this)
        playlistMenuPresenter.bindView(playlistMenuView)
    }

    override fun onResume() {
        super.onResume()

        presenter.loadAlbums()

        findToolbarHost()?.getToolbar()?.let { toolbar ->
            toolbar.inflateMenu(R.menu.menu_library)
            toolbar.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.rescan -> {
                        presenter.rescanLibrary()
                        Toast.makeText(context!!, "Library scan started", Toast.LENGTH_SHORT).show()
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
            toolbar.menu.removeItem(R.id.rescan)
            toolbar.setOnMenuItemClickListener(null)
        }
    }

    override fun onDestroyView() {
        adapter.dispose()

        presenter.unbindView()
        playlistMenuPresenter.unbindView()

        super.onDestroyView()
    }

    // AlbumListContract.View Implementation

    override fun setAlbums(albums: List<Album>) {
        adapter.setData(albums.map { album ->
            AlbumBinder(album, imageLoader, this)
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
        horizontalLoadingView?.setProgress(progress)
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
        val popupMenu = PopupMenu(context!!, view)
        popupMenu.inflate(R.menu.menu_popup_add)

        playlistMenuView.createPlaylistMenu(popupMenu.menu)

        popupMenu.setOnMenuItemClickListener { menuItem ->
            if (playlistMenuView.handleMenuItem(menuItem, PlaylistData.Albums(album))) {
                return@setOnMenuItemClickListener true
            } else {
                when (menuItem.itemId) {
                    R.id.queue -> {
                        presenter.addToQueue(album)
                        return@setOnMenuItemClickListener true
                    }
                    R.id.playNext -> {
                        presenter.playNext(album)
                        return@setOnMenuItemClickListener true
                    }
                }
            }
            false
        }
        popupMenu.show()
    }

    // CreatePlaylistDialogFragment.Listener Implementation

    override fun onSave(text: String, playlistData: PlaylistData) {
        playlistMenuPresenter.createPlaylist(text, playlistData)
    }


    // Static

    companion object {

        const val TAG = "AlbumListFragment"

        fun newInstance() = AlbumListFragment()
    }
}