package com.simplecityapps.shuttle.ui.screens.library.albums

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.sorting.AlbumSortOrder
import com.simplecityapps.shuttle.ui.common.ComposeContextualToolbarHelper
import com.simplecityapps.shuttle.ui.common.TagEditorMenuSanitiser
import com.simplecityapps.shuttle.ui.common.autoCleared
import com.simplecityapps.shuttle.ui.common.dialog.TagEditorAlertDialog
import com.simplecityapps.shuttle.ui.common.view.findToolbarHost
import com.simplecityapps.shuttle.ui.screens.library.ViewMode
import com.simplecityapps.shuttle.ui.screens.library.albums.detail.AlbumDetailFragmentArgs
import com.simplecityapps.shuttle.ui.screens.playlistmenu.CreatePlaylistDialogFragment
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistMenuPresenter
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistMenuView
import com.simplecityapps.shuttle.ui.theme.AppTheme
import com.squareup.phrase.Phrase
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class AlbumListFragment :
    Fragment(),
    CreatePlaylistDialogFragment.Listener {

    @Inject
    lateinit var playlistMenuPresenter: PlaylistMenuPresenter

    @Inject
    lateinit var preferenceManager: GeneralPreferenceManager

    private var composeView: ComposeView by autoCleared()

    private val viewModel: AlbumListViewModel by viewModels()

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
    ): View? = inflater.inflate(R.layout.fragment_albums, container, false)

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
                        val count = state.selectedAlbums.size
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
                                    state.selectedAlbums
                                        .flatMap { it.mediaProviders }
                                        .distinct(),
                                )
                            }
                        }

                        updateToolbarMenuViewMode(state.viewMode)
                        updateToolbarMenuSortOrder(state.sortOrder)
                    }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is AlbumListUiEvent.AddedToQueue -> {
                            Toast.makeText(
                                context,
                                Phrase.fromPlural(resources, R.plurals.queue_albums_added, event.albumCount)
                                    .put("count", event.albumCount)
                                    .format(),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        is AlbumListUiEvent.PlaybackFailed -> {
                            Toast.makeText(
                                context,
                                event.errorMessage ?: getString(R.string.error_unknown),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        is AlbumListUiEvent.EditTags -> {
                            TagEditorAlertDialog.newInstance(event.songs).show(childFragmentManager)
                        }
                        is AlbumListUiEvent.LibraryEmpty -> {
                            // No-op — handled by UI state
                        }
                        is AlbumListUiEvent.AddedToPlaylist -> {
                            Toast.makeText(
                                context,
                                event.playlistData.getPlaylistSavedMessage(resources, event.playlist.name),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        is AlbumListUiEvent.PlaylistDuplicatesFound -> {
                            showPlaylistDuplicatesDialog(event.playlist, event.playlistData, event.deduplicatedSongs, event.duplicates)
                        }
                        is AlbumListUiEvent.PlaylistAddFailed -> {
                            Toast.makeText(context, event.message ?: getString(R.string.error_unknown), Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }

        composeView.setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            val theme by preferenceManager.theme(viewLifecycleOwner.lifecycleScope).collectAsStateWithLifecycle()
            val accent by preferenceManager.accent(viewLifecycleOwner.lifecycleScope).collectAsStateWithLifecycle()

            AppTheme(
                theme = theme,
                accent = accent,
            ) {
                AlbumList(
                    uiState = uiState,
                    playlists = uiState.playlists.toImmutableList(),
                    onAlbumClick = { album ->
                        if (!uiState.isSelecting) {
                            navigateToDetail(album)
                        } else {
                            viewModel.onAlbumClick(album)
                        }
                    },
                    onAlbumLongClick = { album -> viewModel.onAlbumLongClick(album) },
                    onPlay = { album -> viewModel.onPlay(album) },
                    onAddToQueue = { album -> viewModel.onAddToQueue(album) },
                    onPlayNext = { album -> viewModel.onPlayNext(album) },
                    onExclude = { album -> viewModel.onExclude(album) },
                    onEditTags = { album -> viewModel.onEditTags(album) },
                    onAddToPlaylist = { playlist, playlistData ->
                        viewModel.addToPlaylist(playlist, playlistData)
                    },
                    onShowCreatePlaylistDialog = { album ->
                        CreatePlaylistDialogFragment.newInstance(
                            PlaylistData.Albums(album),
                            context?.getString(R.string.playlist_create_dialog_playlist_name_hint)
                        ).show(childFragmentManager)
                    },
                    onShuffle = { viewModel.onShuffle() },
                )
            }
        }
    }

    override fun onCreateOptionsMenu(
        menu: Menu,
        inflater: MenuInflater
    ) {
        super.onCreateOptionsMenu(menu, inflater)

        inflater.inflate(R.menu.menu_album_list, menu)
        updateToolbarMenuViewMode(viewModel.uiState.value.viewMode)
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
        R.id.gridViewMode -> {
            viewModel.setViewMode(ViewMode.Grid)
            true
        }
        R.id.listViewMode -> {
            viewModel.setViewMode(ViewMode.List)
            true
        }
        R.id.sortAlbumName -> {
            viewModel.setSortOrder(AlbumSortOrder.AlbumName)
            true
        }
        R.id.sortArtistName -> {
            viewModel.setSortOrder(AlbumSortOrder.ArtistGroupKey)
            true
        }
        R.id.sortAlbumYear -> {
            viewModel.setSortOrder(AlbumSortOrder.Year)
            true
        }
        else -> false
    }

    // Private

    private fun navigateToDetail(album: com.simplecityapps.shuttle.model.Album) {
        if (findNavController().currentDestination?.id == R.id.libraryFragment) {
            try {
                findNavController().navigate(
                    R.id.action_libraryFragment_to_albumDetailFragment,
                    AlbumDetailFragmentArgs(album).toBundle(),
                )
            } catch (e: IllegalArgumentException) {
                Timber.e(e, "Failed to navigate to album detail")
            }
        }
    }

    private fun updateContextualToolbar() {
        findToolbarHost()?.apply {
            contextualToolbar?.let { ctxToolbar ->
                ctxToolbar.menu.clear()
                ctxToolbar.inflateMenu(R.menu.menu_multi_select)
                TagEditorMenuSanitiser.sanitise(
                    ctxToolbar.menu,
                    viewModel.uiState.value.selectedAlbums
                        .flatMap { it.mediaProviders }
                        .distinct(),
                )
                ctxToolbar.setOnMenuItemClickListener { menuItem ->
                    playlistMenuView.createPlaylistMenu(ctxToolbar.menu)
                    val selectedAlbums = viewModel.selectedAlbums()
                    if (playlistMenuView.handleMenuItem(menuItem, PlaylistData.Albums(selectedAlbums))) {
                        contextualToolbarHelper.hide()
                        return@setOnMenuItemClickListener true
                    }
                    when (menuItem.itemId) {
                        R.id.queue -> {
                            viewModel.onAddSelectedToQueue()
                            true
                        }
                        R.id.editTags -> {
                            viewModel.onEditTagsSelected()
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

    private fun updateToolbarMenuViewMode(viewMode: ViewMode) {
        findToolbarHost()?.toolbar?.menu?.let { menu ->
            when (viewMode) {
                ViewMode.List -> menu.findItem(R.id.listViewMode)?.isChecked = true
                ViewMode.Grid -> menu.findItem(R.id.gridViewMode)?.isChecked = true
            }
        }
    }

    private fun updateToolbarMenuSortOrder(sortOrder: AlbumSortOrder) {
        findToolbarHost()?.toolbar?.menu?.let { menu ->
            when (sortOrder) {
                AlbumSortOrder.AlbumName -> menu.findItem(R.id.sortAlbumName)?.isChecked = true
                AlbumSortOrder.ArtistGroupKey -> menu.findItem(R.id.sortArtistName)?.isChecked = true
                AlbumSortOrder.Year -> menu.findItem(R.id.sortAlbumYear)?.isChecked = true
                else -> {
                    // Nothing to do
                }
            }
        }
    }

    @SuppressLint("InflateParams")
    private fun showPlaylistDuplicatesDialog(
        playlist: Playlist,
        playlistData: PlaylistData,
        deduplicatedSongs: PlaylistData.Songs,
        duplicates: List<Song>,
    ) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_playlist_duplicate, null)
        val subtitle: TextView = dialogView.findViewById(R.id.title)
        val alwaysAddSwitch: SwitchCompat = dialogView.findViewById(R.id.alwaysAddSwitch)

        subtitle.text = Phrase.fromPlural(requireContext(), R.plurals.playlist_menu_duplicates_dialog_subtitle, duplicates.size)
            .putOptional("count", duplicates.size)
            .put("playlist_name", playlist.name)
            .format()

        alwaysAddSwitch.setOnCheckedChangeListener { _, isChecked ->
            preferenceManager.ignorePlaylistDuplicates = isChecked
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.playlist_menu_duplicates_dialog_title))
            .setView(dialogView)
            .setNegativeButton(getString(R.string.playlist_menu_duplicates_dialog_button_skip)) { _, _ ->
                viewModel.addToPlaylist(playlist, deduplicatedSongs, ignoreDuplicates = true)
            }
            .setPositiveButton(getString(R.string.playlist_menu_duplicates_dialog_button_add)) { _, _ ->
                viewModel.addToPlaylist(playlist, playlistData, ignoreDuplicates = true)
            }
            .show()
    }

    // CreatePlaylistDialogFragment.Listener Implementation

    override fun onSave(
        text: String,
        playlistData: PlaylistData
    ) {
        viewModel.createPlaylist(text, playlistData)
    }

    // Static

    companion object {
        const val TAG = "AlbumListFragment"

        fun newInstance() = AlbumListFragment()
    }
}
