package com.simplecityapps.shuttle.ui.screens.library.playlists

import android.annotation.SuppressLint
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
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.simplecityapps.adapter.RecyclerAdapter
import com.simplecityapps.adapter.RecyclerListener
import com.simplecityapps.mediaprovider.model.Playlist
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.dagger.Injectable
import com.simplecityapps.shuttle.ui.common.autoCleared
import com.simplecityapps.shuttle.ui.common.recyclerview.SectionedAdapter
import com.simplecityapps.shuttle.ui.common.recyclerview.clearAdapterOnDetach
import com.simplecityapps.shuttle.ui.common.view.CircularLoadingView
import com.simplecityapps.shuttle.ui.common.view.HorizontalLoadingView
import com.simplecityapps.shuttle.ui.common.view.findToolbarHost
import com.simplecityapps.shuttle.ui.screens.library.playlists.detail.PlaylistDetailFragmentArgs
import javax.inject.Inject


class PlaylistListFragment :
    Fragment(),
    Injectable,
    PlaylistListContract.View {

    private lateinit var adapter: RecyclerAdapter

    @Inject lateinit var presenter: PlaylistListPresenter

    private var circularLoadingView: CircularLoadingView by autoCleared()
    private var horizontalLoadingView: HorizontalLoadingView by autoCleared()

    private var recyclerView: RecyclerView by autoCleared()

    private var recyclerViewState: Parcelable? = null


    // Lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        adapter = SectionedAdapter(lifecycle.coroutineScope)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_playlists, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.recyclerView)
        recyclerView.adapter = adapter
        recyclerView.setRecyclerListener(RecyclerListener())
        recyclerView.clearAdapterOnDetach()

        circularLoadingView = view.findViewById(R.id.circularLoadingView)
        horizontalLoadingView = view.findViewById(R.id.horizontalLoadingView)

        savedInstanceState?.getParcelable<Parcelable>(ARG_RECYCLER_STATE)?.let { recyclerViewState = it }

        presenter.bindView(this)
    }

    override fun onResume() {
        super.onResume()

        presenter.loadPlaylists()

        findToolbarHost()?.getToolbar()?.let { toolbar ->
            toolbar.inflateMenu(R.menu.menu_playlists)
            toolbar.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.syncPlaylists -> {
                        AlertDialog.Builder(requireContext())
                            .setTitle("Sync Media Store Playlists")
                            .setMessage("Copies playlists from the Media Store. If the playlists already exists in Shuttle, the songs will be merged. \n\nNote: Songs are only added, and not removed.")
                            .setPositiveButton("Sync") { _, _ -> presenter.importMediaStorePlaylists() }
                            .setNegativeButton("Cancel", null)
                            .show()
                        true
                    }
                    else -> false
                }
            }
        }

        recyclerViewState?.let { recyclerView.layoutManager?.onRestoreInstanceState(recyclerViewState) }
    }

    override fun onPause() {
        super.onPause()

        findToolbarHost()?.getToolbar()?.let { toolbar ->
            toolbar.menu.removeItem(R.id.syncPlaylists)
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

        super.onDestroyView()
    }


    // PlaylistListContract.View Implementation

    override fun setPlaylists(playlists: List<Playlist>) {
        adapter.update(playlists.map { playlist ->
            PlaylistBinder(playlist, playlistBinderListener)
        }, completion = {
            recyclerViewState?.let {
                recyclerView.layoutManager?.onRestoreInstanceState(recyclerViewState)
            }
        })
    }

    override fun onAddedToQueue(playlist: Playlist) {
        Toast.makeText(context, "${playlist.name} added to queue", Toast.LENGTH_SHORT).show()
    }

    override fun setLoadingState(state: PlaylistListContract.LoadingState) {
        when (state) {
            is PlaylistListContract.LoadingState.Scanning -> {
                horizontalLoadingView.setState(HorizontalLoadingView.State.Loading("Scanning your library"))
                circularLoadingView.setState(CircularLoadingView.State.None)
            }
            is PlaylistListContract.LoadingState.Empty -> {
                horizontalLoadingView.setState(HorizontalLoadingView.State.None)
                circularLoadingView.setState(CircularLoadingView.State.Empty("No playlists"))
            }
            is PlaylistListContract.LoadingState.None -> {
                horizontalLoadingView.setState(HorizontalLoadingView.State.None)
                circularLoadingView.setState(CircularLoadingView.State.None)
            }
        }
    }

    override fun setLoadingProgress(progress: Float) {
        horizontalLoadingView.setProgress(progress)
    }

    override fun onPlaylistsImported() {
        Toast.makeText(requireContext(), "Playlists imported", Toast.LENGTH_SHORT).show()
    }


    // PlaylistBinder.Listener

    private val playlistBinderListener = object : PlaylistBinder.Listener {

        override fun onPlaylistSelected(playlist: Playlist, viewHolder: PlaylistBinder.ViewHolder) {
            if (playlist.songCount != 0) {
                findNavController().navigate(
                    R.id.action_libraryFragment_to_playlistDetailFragment,
                    PlaylistDetailFragmentArgs(playlist).toBundle(),
                    null,
                    null
                )
            }
        }

        @SuppressLint("RestrictedApi")
        override fun onOverflowClicked(view: View, playlist: Playlist) {
            val popupMenu = PopupMenu(requireContext(), view)
            popupMenu.inflate(R.menu.menu_playlist_overflow)
            popupMenu.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.queue -> {
                        presenter.addToQueue(playlist)
                        true
                    }
                    R.id.delete -> {
                        AlertDialog.Builder(requireContext())
                            .setTitle("Delete Playlist")
                            .setMessage("${playlist.name} will be permanently deleted")
                            .setPositiveButton("Delete") { _, _ -> presenter.deletePlaylist(playlist) }
                            .setNegativeButton("Cancel", null)
                            .show()
                        true
                    }
                    R.id.playNext -> {
                        presenter.playNext(playlist)
                        return@setOnMenuItemClickListener true
                    }
                    R.id.clear -> {
                        presenter.clearPlaylist(playlist)
                        return@setOnMenuItemClickListener true
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

        const val ARG_RECYCLER_STATE = "recycler_state"

        fun newInstance() = PlaylistListFragment()
    }
}