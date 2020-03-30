package com.simplecityapps.shuttle.ui.screens.library.folders.detail

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController
import androidx.recyclerview.widget.RecyclerView
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import au.com.simplecityapps.shuttle.imageloading.glide.GlideImageLoader
import com.simplecityappds.saf.SafDirectoryHelper
import com.simplecityapps.adapter.RecyclerAdapter
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.dagger.Injectable
import com.simplecityapps.shuttle.ui.common.autoCleared
import com.simplecityapps.shuttle.ui.common.error.userDescription
import com.simplecityapps.shuttle.ui.common.recyclerview.SectionedAdapter
import com.simplecityapps.shuttle.ui.common.recyclerview.clearAdapterOnDetach
import com.simplecityapps.shuttle.ui.common.view.findToolbarHost
import com.simplecityapps.shuttle.ui.screens.library.folders.FileNode
import com.simplecityapps.shuttle.ui.screens.library.folders.FileNodeTree
import com.simplecityapps.shuttle.ui.screens.library.folders.FolderBinder
import com.simplecityapps.shuttle.ui.screens.playlistmenu.CreatePlaylistDialogFragment
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistMenuPresenter
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistMenuView
import com.simplecityapps.shuttle.ui.screens.songinfo.SongInfoDialogFragment
import io.reactivex.disposables.CompositeDisposable
import javax.inject.Inject


class FolderDetailFragment :
    Fragment(),
    Injectable,
    FolderDetailContract.View,
    FolderBinder.Listener,
    CreatePlaylistDialogFragment.Listener {

    private val compositeDisposable = CompositeDisposable()

    private lateinit var adapter: RecyclerAdapter

    private lateinit var path: String

    private var imageLoader: ArtworkImageLoader by autoCleared()

    private lateinit var playlistMenuView: PlaylistMenuView

    private var recyclerView: RecyclerView by autoCleared()

    @Inject lateinit var playlistMenuPresenter: PlaylistMenuPresenter

    @Inject lateinit var presenter: FolderDetailPresenter


    // Lifecycle

    override fun onAttach(context: Context) {
        super.onAttach(context)

        path = FolderDetailFragmentArgs.fromBundle(requireArguments()).path
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_folder_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.recyclerView)

        playlistMenuView = PlaylistMenuView(requireContext(), playlistMenuPresenter, childFragmentManager)

        imageLoader = GlideImageLoader(this)

        findToolbarHost()?.getToolbar()?.let { toolbar ->
            toolbar.subtitle = path
        }

        adapter = SectionedAdapter()
        recyclerView.adapter = adapter
        recyclerView.clearAdapterOnDetach()

        playlistMenuPresenter.bindView(playlistMenuView)
        presenter.bindView(this)
        presenter.loadData(Uri.parse(path))
    }

    override fun onDestroyView() {
        compositeDisposable.clear()

        adapter.dispose()

        playlistMenuPresenter.unbindView()
        presenter.unbindView()

        super.onDestroyView()
    }


    // FolderDetailContract.View Implementation

    override fun setData(nodes: List<SafDirectoryHelper.FileNode>) {
        adapter.setData(nodes.map {
            FolderBinder(it, imageLoader, this)
        })
    }

    override fun showLoadError(error: Error) {
        Toast.makeText(context, error.userDescription(), Toast.LENGTH_LONG).show()
    }

    override fun onAddedToQueue(song: Song) {
        Toast.makeText(context, "${song.name} added to queue", Toast.LENGTH_SHORT).show()
    }

    // CreatePlaylistDialogFragment.Listener Implementation

    override fun onSave(text: String, playlistData: PlaylistData) {
        playlistMenuPresenter.createPlaylist(text, playlistData)
    }


    // FolderBinder.Listener Implementation

    override fun onNodeSelected(node: SafDirectoryHelper.FileNode) {
        when (node) {
            is FileNodeTree -> {
                view?.findNavController()?.navigate(R.id.action_folderDetailFragment_self, FolderDetailFragmentArgs(node.uri.toString()).toBundle())
            }
            is FileNode -> {
                presenter.onSongClicked(node.song, node.parent.leafNodes.map { leaf -> leaf.song })
            }
        }
    }

    override fun onOverflowClicked(view: View, song: Song) {
        val popupMenu = PopupMenu(requireContext(), view)
        popupMenu.inflate(R.menu.menu_popup_song)

        playlistMenuView.createPlaylistMenu(popupMenu.menu)

        popupMenu.setOnMenuItemClickListener { menuItem ->
            if (playlistMenuView.handleMenuItem(menuItem, PlaylistData.Songs(song))) {
                return@setOnMenuItemClickListener true
            } else {
                when (menuItem.itemId) {
                    R.id.queue -> {
                        presenter.addToQueue(song)
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
                    R.id.blacklist -> {
                        presenter.blacklist(song)
                        return@setOnMenuItemClickListener true
                    }
                }
            }
            false
        }
        popupMenu.show()
    }


    // Static

    companion object {
        const val TAG = "FolderDetailFragment"
    }
}