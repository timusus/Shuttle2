package com.simplecityapps.shuttle.ui.screens.library.playlists

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.simplecityapps.adapter.RecyclerAdapter
import com.simplecityapps.adapter.RecyclerListener
import com.simplecityapps.mediaprovider.model.Playlist
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.dagger.Injectable
import com.simplecityapps.shuttle.ui.common.recyclerview.SectionedAdapter
import com.simplecityapps.shuttle.ui.common.recyclerview.clearAdapterOnDetach
import com.simplecityapps.shuttle.ui.screens.library.playlists.detail.PlaylistDetailFragmentArgs
import kotlinx.android.synthetic.main.fragment_folder_detail.*
import javax.inject.Inject


class PlaylistListFragment :
    Fragment(),
    Injectable,
    PlaylistListContract.View {

    private lateinit var adapter: RecyclerAdapter

    @Inject lateinit var presenter: PlaylistListPresenter


    // Lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        adapter = SectionedAdapter()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_playlists, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        recyclerView.adapter = adapter
        recyclerView.setRecyclerListener(RecyclerListener())

        presenter.bindView(this)
    }

    override fun onResume() {
        super.onResume()

        presenter.loadPlaylists()
    }

    override fun onDestroyView() {
        presenter.unbindView()
        recyclerView.clearAdapterOnDetach()
        super.onDestroyView()
    }

    // PlaylistListContract.View Implementation

    override fun setPlaylists(playlists: List<Playlist>) {
        adapter.setData(playlists.map { playlist ->
            PlaylistBinder(playlist, playlistBinderListener)
        })
    }

    override fun onAddedToQueue(playlist: Playlist) {
        Toast.makeText(context, "${playlist.name} added to queue", Toast.LENGTH_SHORT).show()
    }

    // PlaylistBinder.Listener

    private val playlistBinderListener = object : PlaylistBinder.Listener {

        override fun onPlaylistSelected(playlist: Playlist, viewHolder: PlaylistBinder.ViewHolder) {
            findNavController().navigate(
                R.id.action_libraryFragment_to_playlistDetailFragment,
                PlaylistDetailFragmentArgs(playlist).toBundle(),
                null,
                null
            )
        }

        @SuppressLint("RestrictedApi")
        override fun onOverflowClicked(view: View, playlist: Playlist) {
            val popupMenu = PopupMenu(context!!, view)
            popupMenu.inflate(R.menu.menu_playlist_overflow)
            popupMenu.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.queue -> {
                        presenter.addToQueue(playlist)
                        true
                    }
                    R.id.delete -> {
                        presenter.deletePlaylist(playlist)
                        true
                    }
                    else -> false
                }
            }
            popupMenu.show()
        }
    }


    // Static

    companion object {

        const val TAG = "PlaylistListFragment"

        fun newInstance() = PlaylistListFragment()
    }
}