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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.sorting.SongSortOrder
import com.simplecityapps.shuttle.ui.common.ComposeContextualToolbarHelper
import com.simplecityapps.shuttle.ui.common.TagEditorMenuSanitiser
import com.simplecityapps.shuttle.ui.common.autoCleared
import com.simplecityapps.shuttle.ui.common.dialog.TagEditorAlertDialog
import com.simplecityapps.shuttle.ui.common.dialog.showDeleteDialog
import com.simplecityapps.shuttle.ui.common.view.findToolbarHost
import com.simplecityapps.shuttle.ui.screens.playlistmenu.CreatePlaylistDialogFragment
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistMenuPresenter
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistMenuView
import com.simplecityapps.shuttle.ui.screens.songinfo.SongInfoDialogFragment
import com.simplecityapps.shuttle.ui.theme.AppTheme
import com.squareup.phrase.Phrase
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SongListFragment :
    Fragment(),
    CreatePlaylistDialogFragment.Listener {

    @Inject
    lateinit var playlistMenuPresenter: PlaylistMenuPresenter

    @Inject
    lateinit var preferenceManager: GeneralPreferenceManager

    private var composeView: ComposeView by autoCleared()

    private val viewModel: SongListViewModel by viewModels()

    private var contextualToolbarHelper: ComposeContextualToolbarHelper by autoCleared()

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

        contextualToolbarHelper = ComposeContextualToolbarHelper(viewModel::clearSelection)

        updateContextualToolbar()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState
                    .collect { state ->
                        val count = state.selectedSongs.size
                        if (count == 0) {
                            contextualToolbarHelper.hide()
                        } else {
                            contextualToolbarHelper.show()

                            contextualToolbarHelper.contextualToolbar?.title =
                                Phrase.fromPlural(requireContext(), R.plurals.multi_select_items_selected, count)
                                    .put("count", count)
                                    .format()
                            contextualToolbarHelper.contextualToolbar?.menu?.let { menu ->
                                TagEditorMenuSanitiser.sanitise(
                                    menu,
                                    state.selectedSongs
                                        .map { it.mediaProvider }
                                        .distinct(),
                                )
                            }
                        }

                        updateToolbarMenuSortOrder(state.sortOrder)
                    }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is SongListUiEvent.AddedToQueue -> {
                            Toast.makeText(
                                context,
                                Phrase.fromPlural(resources, R.plurals.queue_songs_added, event.songCount)
                                    .put("count", event.songCount)
                                    .format(),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        is SongListUiEvent.Error -> {
                            Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }

        composeView.setContent {
            val uiState by viewModel.uiState.collectAsState()
            val playlists by playlistMenuPresenter.playlistsState.collectAsState()

            val theme by preferenceManager.theme(viewLifecycleOwner.lifecycleScope).collectAsStateWithLifecycle()
            val accent by preferenceManager.accent(viewLifecycleOwner.lifecycleScope).collectAsStateWithLifecycle()

            AppTheme(
                theme = theme,
                accent = accent,
            ) {
                SongList(
                    uiState = uiState,
                    playlists = playlists.toImmutableList(),
                    onSongClick = { song -> viewModel.onSongClick(song) },
                    onSongLongClick = { song -> viewModel.onSongLongClick(song) },
                    onAddToQueue = { song -> viewModel.onAddToQueue(song) },
                    onAddToPlaylist = { playlist, playlistData ->
                        playlistMenuPresenter.addToPlaylist(playlist, playlistData)
                    },
                    onShowCreatePlaylistDialog = { song ->
                        CreatePlaylistDialogFragment.newInstance(
                            PlaylistData.Songs(song),
                            context?.getString(R.string.playlist_create_dialog_playlist_name_hint)
                        ).show(childFragmentManager)
                    },
                    onPlayNext = { song -> viewModel.onPlayNext(song) },
                    onSongInfo = { song ->
                        SongInfoDialogFragment.newInstance(song).show(childFragmentManager)
                    },
                    onExclude = { song -> viewModel.onExclude(song) },
                    onEditTags = { song ->
                        showTagEditor(song)
                    },
                    onDelete = { song ->
                        showDeleteDialog(requireContext(), song.name) {
                            viewModel.onDelete(song)
                        }
                    },
                    onShuffle = { viewModel.onShuffle() }
                )
            }
        }
    }

    override fun onCreateOptionsMenu(
        menu: Menu,
        inflater: MenuInflater
    ) {
        super.onCreateOptionsMenu(menu, inflater)

        inflater.inflate(R.menu.menu_song_list, menu)
        updateToolbarMenuSortOrder(viewModel.uiState.value.sortOrder)
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
            contextualToolbar?.let { ctxToolbar ->
                ctxToolbar.menu.clear()
                ctxToolbar.inflateMenu(R.menu.menu_multi_select)
                TagEditorMenuSanitiser.sanitise(
                    ctxToolbar.menu,
                    viewModel.uiState.value.selectedSongs
                        .map { it.mediaProvider }
                        .distinct(),
                )
                ctxToolbar.setOnMenuItemClickListener { menuItem ->
                    playlistMenuView.createPlaylistMenu(ctxToolbar.menu)
                    val selectedSongs = viewModel.selectedSongs()
                    if (playlistMenuView.handleMenuItem(menuItem, PlaylistData.Songs(selectedSongs))) {
                        contextualToolbarHelper.hide()
                        return@setOnMenuItemClickListener true
                    }
                    when (menuItem.itemId) {
                        R.id.queue -> {
                            viewModel.onAddSelectedToQueue()
                            true
                        }
                        R.id.editTags -> {
                            TagEditorAlertDialog.newInstance(selectedSongs)
                                .show(childFragmentManager)
                            contextualToolbarHelper.hide()
                            true
                        }
                        else -> false
                    }
                }
            }
            contextualToolbarHelper.contextualToolbar = contextualToolbar
            contextualToolbarHelper.toolbar = toolbar

            if (viewModel.uiState.value.isSelecting) {
                contextualToolbarHelper.show()
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

    fun showTagEditor(song: Song) {
        TagEditorAlertDialog.newInstance(listOf(song)).show(childFragmentManager)
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
