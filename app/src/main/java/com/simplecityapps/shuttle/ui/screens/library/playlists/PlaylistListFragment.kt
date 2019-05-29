package com.simplecityapps.shuttle.ui.screens.library.playlists

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.simplecityapps.adapter.RecyclerAdapter
import com.simplecityapps.adapter.RecyclerListener
import com.simplecityapps.mediaprovider.model.Playlist
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.dagger.Injectable
import com.simplecityapps.shuttle.ui.common.dialog.EditTextAlertDialog
import com.simplecityapps.shuttle.ui.common.recyclerview.SectionedAdapter
import com.simplecityapps.shuttle.ui.common.recyclerview.clearAdapterOnDetach
import com.simplecityapps.shuttle.ui.screens.library.playlists.detail.PlaylistDetailFragmentArgs
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistMenuPresenter
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistMenuView
import kotlinx.android.synthetic.main.fragment_folder_detail.*
import timber.log.Timber
import javax.inject.Inject


class PlaylistListFragment :
    Fragment(),
    Injectable,
    PlaylistListContract.View,
    EditTextAlertDialog.Listener {

    private lateinit var adapter: RecyclerAdapter

    private lateinit var addPlaylistButton: Button

    @Inject lateinit var presenter: PlaylistListPresenter

    @Inject lateinit var playlistMenuPresenter: PlaylistMenuPresenter

    private lateinit var playlistMenuView: PlaylistMenuView


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

        playlistMenuView = PlaylistMenuView(context!!, playlistMenuPresenter, childFragmentManager)

        recyclerView.adapter = adapter
        recyclerView.setRecyclerListener(RecyclerListener())

        addPlaylistButton = view.findViewById(R.id.addPlaylistButton)
        addPlaylistButton.setOnClickListener { onShowCreatePlaylistDialog() }

        presenter.bindView(this)
        playlistMenuPresenter.bindView(playlistMenuView)
    }

    override fun onResume() {
        super.onResume()

        presenter.loadPlaylists()
        playlistMenuPresenter.unbindView()
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

        addPlaylistButton.isVisible = playlists.isEmpty()
    }

    override fun onPlaylistCreated(playlist: Playlist) {
        Toast.makeText(context!!, "'${playlist.name}' successfully created", Toast.LENGTH_LONG).show()
    }

    override fun onShowCreatePlaylistDialog() {
        EditTextAlertDialog.newInstance(hint = "Playlist Name").show(childFragmentManager)
    }

    // EditTextAlertDialog.Listener Implementation

    override fun onSave(text: String?) {
        text?.let {
            playlistMenuPresenter.createPlaylist(text, null)
        } ?: Timber.e("Failed to create playlist, playlist name is null")
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