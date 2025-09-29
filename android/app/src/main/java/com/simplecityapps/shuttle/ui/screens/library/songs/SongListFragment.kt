package com.simplecityapps.shuttle.ui.screens.library.songs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.sorting.SongSortOrder
import com.simplecityapps.shuttle.ui.common.TagEditorMenuSanitiser
import com.simplecityapps.shuttle.ui.common.autoCleared
import com.simplecityapps.shuttle.ui.common.dialog.TagEditorAlertDialog
import com.simplecityapps.shuttle.ui.common.dialog.showDeleteDialog
import com.simplecityapps.shuttle.ui.common.error.UserFriendlyError
import com.simplecityapps.shuttle.ui.common.error.userDescription
import com.simplecityapps.shuttle.ui.common.view.findToolbarHost
import com.simplecityapps.shuttle.ui.screens.playlistmenu.CreatePlaylistDialogFragment
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistMenuPresenter
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistMenuView
import com.simplecityapps.shuttle.ui.screens.songinfo.SongInfoDialogFragment
import com.squareup.phrase.Phrase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SongListFragment :
    Fragment(),
    CreatePlaylistDialogFragment.Listener {

    @Inject
    lateinit var playlistMenuPresenter: PlaylistMenuPresenter

    private var composeView: ComposeView by autoCleared()

    private val viewModel: SongListViewModel by viewModels()

    private lateinit var playlistMenuView: PlaylistMenuView

    // Lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_songs, container, false)

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        setHasOptionsMenu(true)

        playlistMenuView = PlaylistMenuView(requireContext(), playlistMenuPresenter, childFragmentManager)
        playlistMenuPresenter.bindView(playlistMenuView)

        composeView = view.findViewById(R.id.composeView)

        updateContextualToolbar()

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.contextualToolbarHelper.selectedSongCountState
                .collect { count ->
                    if (count == 0) {
                        viewModel.contextualToolbarHelper.hide()
                    } else {
                        viewModel.contextualToolbarHelper.show()

                        viewModel.contextualToolbarHelper.contextualToolbar?.title =
                            Phrase.fromPlural(requireContext(), R.plurals.multi_select_items_selected, count)
                                .put("count", count)
                                .format()
                        viewModel.contextualToolbarHelper.contextualToolbar?.menu?.let { menu ->
                            TagEditorMenuSanitiser.sanitise(
                                menu,
                                viewModel.contextualToolbarHelper
                                    .selectedSongsMediaProviders()
                            )
                        }
                    }
                }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.selectedSortOrder
                    .collect { sortOrder ->
                        updateToolbarMenuSortOrder(sortOrder)
                    }
            }
        }

        composeView.setContent {
            val viewState by viewModel.viewState.collectAsState()
            val playlists by playlistMenuPresenter.playlistsState.collectAsState()

            SongList(
                viewState = viewState,
                playlists = playlists.toImmutableList(),
                onSongClick = { song ->
                    viewModel.onSongClick(song) { result ->
                        result.onFailure { error ->
                            showLoadError(error as Error)
                        }
                    }
                },
                onSongLongClick = { song ->
                    viewModel.onSongLongClick(song)
                },
                onAddToQueue = { song ->
                    viewModel.addToQueue(song) { result ->
                        result.onSuccess { song ->
                            onAddedToQueue(listOf(song))
                        }
                    }
                },
                onAddToPlaylist = { playlist, playlistData ->
                    playlistMenuPresenter.addToPlaylist(playlist, playlistData)
                },
                onShowCreatePlaylistDialog = { song ->
                    CreatePlaylistDialogFragment.newInstance(
                        PlaylistData.Songs(song),
                        context?.getString(R.string.playlist_create_dialog_playlist_name_hint)
                    ).show(childFragmentManager)
                },
                onPlayNext = { song ->
                    viewModel.playNext(song) { result ->
                        result.onSuccess { song ->
                            onAddedToQueue(listOf(song))
                        }
                    }
                },
                onSongInfo = { song ->
                    SongInfoDialogFragment.newInstance(song).show(childFragmentManager)
                },
                onExclude = { song ->
                    viewModel.exclude(song)
                },
                onEditTags = { song ->
                    showTagEditor(song)
                },
                onDelete = { song ->
                    showDeleteDialog(requireContext(), song.name) {
                        try {
                            viewModel.delete(song)
                        } catch (e: UserFriendlyError) {
                            showDeleteError(e)
                        }
                    }
                },
                onShuffle = {
                    viewModel.shuffle { result ->
                        result.onFailure { error ->
                            showLoadError(error as Error)
                        }
                    }
                }
            )
        }
    }

    override fun onCreateOptionsMenu(
        menu: Menu,
        inflater: MenuInflater
    ) {
        super.onCreateOptionsMenu(menu, inflater)

        inflater.inflate(R.menu.menu_song_list, menu)
        updateToolbarMenuSortOrder(viewModel.selectedSortOrder.value)
    }

    override fun onResume() {
        super.onResume()

        updateContextualToolbar()
    }

    override fun onPause() {
        super.onPause()

        findToolbarHost()?.apply {
            contextualToolbar?.setOnMenuItemClickListener(null)
        }
    }

    override fun onDestroyView() {
        playlistMenuPresenter.unbindView()

        super.onDestroyView()
    }

    // Toolbar item selection

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.sortSongName -> {
            viewModel.setSortOrder(SongSortOrder.SongName)
            true
        }
        R.id.sortArtistName -> {
            viewModel.setSortOrder(SongSortOrder.ArtistGroupKey)
            true
        }
        R.id.sortAlbumName -> {
            viewModel.setSortOrder(SongSortOrder.AlbumGroupKey)
            true
        }
        R.id.sortSongYear -> {
            viewModel.setSortOrder(SongSortOrder.Year)
            true
        }
        R.id.sortSongDuration -> {
            viewModel.setSortOrder(SongSortOrder.Duration)
            true
        }
        R.id.sortSongDateModified -> {
            viewModel.setSortOrder(SongSortOrder.LastModified)
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
                TagEditorMenuSanitiser.sanitise(
                    contextualToolbar.menu,
                    viewModel.contextualToolbarHelper.selectedSongsMediaProviders(),
                )
                contextualToolbar.setOnMenuItemClickListener { menuItem ->
                    playlistMenuView.createPlaylistMenu(contextualToolbar.menu)
                    val selectedSongs = viewModel.contextualToolbarHelper.selectedSongsState.value.toList()
                    if (playlistMenuView.handleMenuItem(menuItem, PlaylistData.Songs(selectedSongs))) {
                        viewModel.contextualToolbarHelper.hide()
                        return@setOnMenuItemClickListener true
                    }
                    when (menuItem.itemId) {
                        R.id.queue -> {
                            viewModel.addSelectedToQueue()
                            true
                        }
                        R.id.editTags -> {
                            TagEditorAlertDialog.newInstance(selectedSongs)
                                .show(childFragmentManager)
                            viewModel.contextualToolbarHelper.hide()
                            true
                        }
                        else -> false
                    }
                }
            }
            viewModel.contextualToolbarHelper.contextualToolbar = contextualToolbar
            viewModel.contextualToolbarHelper.toolbar = toolbar

            if (viewModel.contextualToolbarHelper.isSelecting()) {
                viewModel.contextualToolbarHelper.show()
            }
        }
    }

    fun updateToolbarMenuSortOrder(sortOrder: SongSortOrder) {
        findToolbarHost()?.toolbar?.menu?.let { menu ->
            when (sortOrder) {
                SongSortOrder.SongName -> menu.findItem(R.id.sortSongName)?.isChecked = true
                SongSortOrder.ArtistGroupKey -> menu.findItem(R.id.sortArtistName)?.isChecked = true
                SongSortOrder.AlbumGroupKey -> menu.findItem(R.id.sortAlbumName)?.isChecked = true
                SongSortOrder.Year -> menu.findItem(R.id.sortSongYear)?.isChecked = true
                SongSortOrder.Duration -> menu.findItem(R.id.sortSongDuration)?.isChecked = true
                SongSortOrder.LastModified -> menu.findItem(R.id.sortSongDateModified)?.isChecked = true
                else -> {
                    // Nothing to do
                }
            }
        }
    }

    fun showLoadError(error: Error) {
        Toast.makeText(context, error.userDescription(resources), Toast.LENGTH_LONG).show()
    }

    fun showTagEditor(song: Song) {
        TagEditorAlertDialog.newInstance(listOf(song)).show(childFragmentManager)
    }

    fun onAddedToQueue(songs: List<com.simplecityapps.shuttle.model.Song>) {
        Toast.makeText(
            context,
            Phrase.fromPlural(resources, R.plurals.queue_songs_added, songs.size)
                .put("count", songs.size)
                .format(),
            Toast.LENGTH_SHORT
        ).show()
    }

    fun showDeleteError(error: Error) {
        Toast.makeText(requireContext(), error.userDescription(resources), Toast.LENGTH_LONG).show()
    }

    // CreatePlaylistDialogFragment.Listener Implementation

    override fun onSave(
        text: String,
        playlistData: PlaylistData
    ) {
        playlistMenuPresenter.createPlaylist(text, playlistData)
    }

    // Static

    companion object {
        const val TAG = "SongListFragment"
        const val ARG_RECYCLER_STATE = "recycler_state"

        fun newInstance() = SongListFragment()
    }
}
