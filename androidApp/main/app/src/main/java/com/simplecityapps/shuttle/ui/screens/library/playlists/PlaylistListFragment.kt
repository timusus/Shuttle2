package com.simplecityapps.shuttle.ui.screens.library.playlists

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.simplecityapps.adapter.RecyclerAdapter
import com.simplecityapps.adapter.RecyclerListener
import com.simplecityapps.adapter.ViewBinder
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.SmartPlaylist
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.autoCleared
import com.simplecityapps.shuttle.ui.common.dialog.EditTextAlertDialog
import com.simplecityapps.shuttle.ui.common.error.userDescription
import com.simplecityapps.shuttle.ui.common.recyclerview.SectionedAdapter
import com.simplecityapps.shuttle.ui.common.view.CircularLoadingView
import com.simplecityapps.shuttle.ui.common.view.HorizontalLoadingView
import com.simplecityapps.shuttle.ui.screens.home.search.HeaderBinder
import com.simplecityapps.shuttle.ui.screens.library.playlists.detail.PlaylistDetailFragmentArgs
import com.simplecityapps.shuttle.ui.screens.library.playlists.smart.SmartPlaylistDetailFragmentArgs
import com.squareup.phrase.Phrase
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class PlaylistListFragment :
    Fragment(),
    PlaylistListContract.View,
    EditTextAlertDialog.Listener {

    @Inject
    lateinit var presenter: PlaylistListPresenter

    private var adapter: RecyclerAdapter by autoCleared()

    private var circularLoadingView: CircularLoadingView by autoCleared()
    private var horizontalLoadingView: HorizontalLoadingView by autoCleared()

    private var recyclerView: RecyclerView by autoCleared()

    private var recyclerViewState: Parcelable? = null


    // Lifecycle

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_playlists, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = SectionedAdapter(viewLifecycleOwner.lifecycleScope)
        recyclerView = view.findViewById(R.id.recyclerView)
        recyclerView.adapter = adapter
        recyclerView.setRecyclerListener(RecyclerListener())

        circularLoadingView = view.findViewById(R.id.circularLoadingView)
        horizontalLoadingView = view.findViewById(R.id.horizontalLoadingView)

        savedInstanceState?.getParcelable<Parcelable>(ARG_RECYCLER_STATE)?.let { recyclerViewState = it }

        presenter.bindView(this)
    }

    override fun onResume() {
        super.onResume()

        presenter.loadPlaylists()
    }

    override fun onPause() {
        super.onPause()

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

    override fun setPlaylists(playlists: List<com.simplecityapps.shuttle.model.Playlist>, smartPlaylists: List<com.simplecityapps.shuttle.model.SmartPlaylist>) {

        val viewBinders = mutableListOf<ViewBinder>()

        if (smartPlaylists.isNotEmpty()) {
            viewBinders.add(HeaderBinder(getString(R.string.playlists_title_smart_playlists)))
            viewBinders.addAll(smartPlaylists.map { smartPlaylist ->
                SmartPlaylistBinder(smartPlaylist, smartPlaylistBinderListener)
            })
        }

        if (playlists.isNotEmpty()) {
            viewBinders.add(HeaderBinder(getString(R.string.playlists_title_playlists)))
        }
        viewBinders.addAll(playlists.map { playlist ->
            PlaylistBinder(playlist, playlistBinderListener)
        })


        adapter.update(viewBinders) {
            recyclerViewState?.let {
                recyclerView.layoutManager?.onRestoreInstanceState(recyclerViewState)
                recyclerViewState = null
            }
        }
    }

    override fun onAddedToQueue(playlist: com.simplecityapps.shuttle.model.Playlist) {
        Toast.makeText(context, Phrase.from(requireContext(), R.string.queue_item_added).put("item_name", playlist.name).format(), Toast.LENGTH_SHORT).show()
    }

    override fun showLoadError(error: Error) {
        Toast.makeText(context, error.userDescription(resources), Toast.LENGTH_LONG).show()
    }

    override fun setLoadingState(state: PlaylistListContract.LoadingState) {
        when (state) {
            is PlaylistListContract.LoadingState.Scanning -> {
                horizontalLoadingView.setState(HorizontalLoadingView.State.Loading(getString(R.string.library_scan_in_progress)))
                circularLoadingView.setState(CircularLoadingView.State.None)
            }
            is PlaylistListContract.LoadingState.Loading -> {
                horizontalLoadingView.setState(HorizontalLoadingView.State.None)
                circularLoadingView.setState(CircularLoadingView.State.Loading(getString(R.string.loading)))
            }
            is PlaylistListContract.LoadingState.Empty -> {
                horizontalLoadingView.setState(HorizontalLoadingView.State.None)
                circularLoadingView.setState(CircularLoadingView.State.Empty(getString(R.string.playlist_list_empty)))
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


    // PlaylistBinder.Listener Implementation

    private val playlistBinderListener = object : PlaylistBinder.Listener {

        override fun onPlaylistSelected(playlist: com.simplecityapps.shuttle.model.Playlist, viewHolder: PlaylistBinder.ViewHolder) {
            if (playlist.songCount != 0) {
                if (findNavController().currentDestination?.id != R.id.playlistDetailFragment) {
                    findNavController().navigate(
                        R.id.action_libraryFragment_to_playlistDetailFragment,
                        PlaylistDetailFragmentArgs(playlist).toBundle(),
                        null,
                        null
                    )
                }
            }
        }

        @SuppressLint("RestrictedApi")
        override fun onOverflowClicked(view: View, playlist: com.simplecityapps.shuttle.model.Playlist) {
            val popupMenu = PopupMenu(requireContext(), view)
            popupMenu.inflate(R.menu.menu_playlist_overflow)

            popupMenu.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.play -> {
                        presenter.play(playlist)
                        true
                    }
                    R.id.queue -> {
                        presenter.addToQueue(playlist)
                        true
                    }
                    R.id.delete -> {
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle(getString(R.string.playlist_dialog_title_delete))
                            .setMessage(
                                Phrase.from(requireContext(), R.string.playlist_dialog_subtitle_delete)
                                    .put("playlist_name", playlist.name)
                                    .format()
                            )
                            .setPositiveButton(getString(R.string.playlist_dialog_button_delete)) { _, _ -> presenter.delete(playlist) }
                            .setNegativeButton(getString(R.string.dialog_button_cancel), null)
                            .show()
                        true
                    }
                    R.id.playNext -> {
                        presenter.playNext(playlist)
                        true
                    }
                    R.id.clear -> {
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle(getString(R.string.playlist_dialog_title_clear))
                            .setMessage(
                                Phrase.from(requireContext(), R.string.playlist_dialog_subtitle_clear)
                                    .put("playlist_name", playlist.name)
                                    .format()
                            )
                            .setPositiveButton(getString(R.string.playlist_dialog_button_clear)) { _, _ -> presenter.clear(playlist) }
                            .setNegativeButton(getString(R.string.dialog_button_cancel), null)
                            .show()
                        true
                    }
                    R.id.rename -> {
                        EditTextAlertDialog
                            .newInstance(
                                title = getString(R.string.playlist_dialog_title_rename),
                                hint = getString(R.string.playlist_dialog_hint_rename),
                                initialText = playlist.name,
                                extra = playlist
                            )
                            .show(childFragmentManager)
                        true
                    }
                    else -> false
                }
            }
            popupMenu.show()
        }
    }


    // SmartPlaylistBinder.Listener Implementation

    private val smartPlaylistBinderListener = object : SmartPlaylistBinder.Listener {

        override fun onSmartPlaylistSelected(smartPlaylist: com.simplecityapps.shuttle.model.SmartPlaylist, viewHolder: SmartPlaylistBinder.ViewHolder) {
            if (findNavController().currentDestination?.id != R.id.playlistDetailFragment) {
                findNavController().navigate(
                    R.id.action_libraryFragment_to_smartPlaylistDetailFragment,
                    SmartPlaylistDetailFragmentArgs(smartPlaylist).toBundle(),
                    null,
                    null
                )
            }
        }
    }


    // Static

    companion object {

        const val TAG = "PlaylistListFragment"

        const val ARG_RECYCLER_STATE = "recycler_state"

        fun newInstance() = PlaylistListFragment()
    }


    // EditTextAlertDialog.Listener

    override fun onSave(text: String?, extra: Parcelable?) {
        presenter.rename(extra as com.simplecityapps.shuttle.model.Playlist, text!!) // default validation ensures text is not null
    }
}