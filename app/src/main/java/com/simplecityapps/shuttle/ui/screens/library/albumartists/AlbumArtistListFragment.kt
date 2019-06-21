package com.simplecityapps.shuttle.ui.screens.library.albumartists

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.findNavController
import au.com.simplecityapps.shuttle.imageloading.glide.GlideImageLoader
import com.simplecityapps.adapter.RecyclerAdapter
import com.simplecityapps.adapter.RecyclerListener
import com.simplecityapps.mediaprovider.model.AlbumArtist
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.dagger.Injectable
import com.simplecityapps.shuttle.ui.common.recyclerview.SectionedAdapter
import com.simplecityapps.shuttle.ui.common.recyclerview.clearAdapterOnDetach
import com.simplecityapps.shuttle.ui.screens.library.albumartists.detail.AlbumArtistDetailFragmentArgs
import com.simplecityapps.shuttle.ui.screens.playlistmenu.CreatePlaylistDialogFragment
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistMenuPresenter
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistMenuView
import kotlinx.android.synthetic.main.fragment_folder_detail.*
import javax.inject.Inject

class AlbumArtistListFragment :
    Fragment(),
    Injectable,
    AlbumArtistBinder.Listener,
    AlbumArtistListContract.View,
    CreatePlaylistDialogFragment.Listener {

    private lateinit var adapter: RecyclerAdapter

    private lateinit var imageLoader: GlideImageLoader

    @Inject lateinit var presenter: AlbumArtistListPresenter

    @Inject lateinit var playlistMenuPresenter: PlaylistMenuPresenter

    private lateinit var playlistMenuView: PlaylistMenuView

    // Lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        adapter = SectionedAdapter()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_album_artists, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        playlistMenuView = PlaylistMenuView(context!!, playlistMenuPresenter, childFragmentManager)

        imageLoader = GlideImageLoader(this)

        recyclerView.adapter = adapter
        recyclerView.setRecyclerListener(RecyclerListener())

        presenter.bindView(this)
        playlistMenuPresenter.bindView(playlistMenuView)
    }

    override fun onResume() {
        super.onResume()

        presenter.loadAlbumArtists()
    }

    override fun onDestroyView() {
        presenter.unbindView()
        playlistMenuPresenter.unbindView()
        recyclerView.clearAdapterOnDetach()
        super.onDestroyView()
    }

    // AlbumArtistListContact.View Implementation

    override fun setAlbumArtists(albumArtists: List<AlbumArtist>) {
        adapter.setData(albumArtists.map { albumArtist ->
            AlbumArtistBinder(albumArtist, imageLoader, this)
        })
    }

    override fun onAddedToQueue(albumArtist: AlbumArtist) {
        Toast.makeText(context, "${albumArtist.name} added to queue", Toast.LENGTH_SHORT).show()
    }

    // AlbumArtistBinder.Listener Implementation

    override fun onAlbumArtistClicked(albumArtist: AlbumArtist, viewHolder: AlbumArtistBinder.ViewHolder) {
        findNavController().navigate(
            R.id.action_libraryFragment_to_albumArtistDetailFragment,
            AlbumArtistDetailFragmentArgs(albumArtist).toBundle(),
            null,
            FragmentNavigatorExtras(viewHolder.imageView to viewHolder.imageView.transitionName)
        )
    }

    override fun onOverflowClicked(view: View, albumArtist: AlbumArtist) {
        val popupMenu = PopupMenu(context!!, view)
        popupMenu.inflate(R.menu.menu_popup_add)

        playlistMenuView.createPlaylistMenu(popupMenu.menu)

        popupMenu.setOnMenuItemClickListener { menuItem ->
            if (playlistMenuView.handleMenuItem(menuItem, PlaylistData.AlbumArtists(albumArtist))) {
                return@setOnMenuItemClickListener true
            } else {
                when (menuItem.itemId) {
                    R.id.queue -> {
                        presenter.addToQueue(albumArtist)
                        return@setOnMenuItemClickListener true
                    }
                }
            }
            false
        }
        popupMenu.show()
    }

    // CreatePlaylistDialogFragment.Listener

    override fun onSave(text: String, playlistData: PlaylistData) {
        playlistMenuView.onSave(text, playlistData)
    }


    // Static

    companion object {

        const val TAG = "AlbumArtistListFragment"

        fun newInstance() = AlbumArtistListFragment()
    }
}