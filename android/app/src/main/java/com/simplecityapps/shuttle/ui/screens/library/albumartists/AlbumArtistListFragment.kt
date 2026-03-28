package com.simplecityapps.shuttle.ui.screens.library.albumartists

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
import com.simplecityapps.shuttle.ui.common.ComposeContextualToolbarHelper
import com.simplecityapps.shuttle.ui.common.TagEditorMenuSanitiser
import com.simplecityapps.shuttle.ui.common.autoCleared
import com.simplecityapps.shuttle.ui.common.dialog.TagEditorAlertDialog
import com.simplecityapps.shuttle.ui.common.view.findToolbarHost
import com.simplecityapps.shuttle.ui.screens.library.ViewMode
import com.simplecityapps.shuttle.ui.screens.library.albumartists.detail.AlbumArtistDetailFragmentArgs
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
class AlbumArtistListFragment :
    Fragment(),
    CreatePlaylistDialogFragment.Listener {

    @Inject
    lateinit var playlistMenuPresenter: PlaylistMenuPresenter

    @Inject
    lateinit var preferenceManager: GeneralPreferenceManager

    private var composeView: ComposeView by autoCleared()

    private val viewModel: AlbumArtistListViewModel by viewModels()

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
    ): View? = inflater.inflate(R.layout.fragment_album_artists, container, false)

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
                        val count = state.selectedArtists.size
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
                                    state.selectedArtists
                                        .flatMap { it.mediaProviders }
                                        .distinct(),
                                )
                            }
                        }

                        updateToolbarMenuViewMode(state.viewMode)
                    }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is AlbumArtistListUiEvent.AddedToQueue -> {
                            Toast.makeText(
                                context,
                                Phrase.fromPlural(resources, R.plurals.queue_artists_added, event.artistCount)
                                    .put("count", event.artistCount)
                                    .format(),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        is AlbumArtistListUiEvent.PlaybackFailed -> {
                            Toast.makeText(
                                context,
                                event.errorMessage ?: getString(R.string.error_unknown),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        is AlbumArtistListUiEvent.EditTags -> {
                            TagEditorAlertDialog.newInstance(event.songs).show(childFragmentManager)
                        }
                        is AlbumArtistListUiEvent.AddedToPlaylist -> {
                            Toast.makeText(
                                context,
                                event.playlistData.getPlaylistSavedMessage(resources, event.playlist.name),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        is AlbumArtistListUiEvent.PlaylistDuplicatesFound -> {
                            showPlaylistDuplicatesDialog(event.playlist, event.playlistData, event.deduplicatedSongs, event.duplicates)
                        }
                        is AlbumArtistListUiEvent.PlaylistAddFailed -> {
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
                AlbumArtistList(
                    uiState = uiState,
                    playlists = uiState.playlists.toImmutableList(),
                    onArtistClick = { albumArtist ->
                        if (!uiState.isSelecting) {
                            navigateToDetail(albumArtist)
                        } else {
                            viewModel.onArtistClick(albumArtist)
                        }
                    },
                    onArtistLongClick = { albumArtist -> viewModel.onArtistLongClick(albumArtist) },
                    onPlay = { albumArtist -> viewModel.onPlay(albumArtist) },
                    onAddToQueue = { albumArtist -> viewModel.onAddToQueue(albumArtist) },
                    onPlayNext = { albumArtist -> viewModel.onPlayNext(albumArtist) },
                    onExclude = { albumArtist -> viewModel.onExclude(albumArtist) },
                    onEditTags = { albumArtist -> viewModel.onEditTags(albumArtist) },
                    onAddToPlaylist = { playlist, playlistData ->
                        viewModel.addToPlaylist(playlist, playlistData)
                    },
                    onShowCreatePlaylistDialog = { albumArtist ->
                        CreatePlaylistDialogFragment.newInstance(
                            PlaylistData.AlbumArtists(albumArtist),
                            context?.getString(R.string.playlist_create_dialog_playlist_name_hint)
                        ).show(childFragmentManager)
                    },
                )
            }
        }
    }

    override fun onCreateOptionsMenu(
        menu: Menu,
        inflater: MenuInflater
    ) {
        super.onCreateOptionsMenu(menu, inflater)

        inflater.inflate(R.menu.menu_artist_list, menu)
        updateToolbarMenuViewMode(viewModel.uiState.value.viewMode)
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
        else -> false
    }

    // Private

    private fun navigateToDetail(albumArtist: com.simplecityapps.shuttle.model.AlbumArtist) {
        if (findNavController().currentDestination?.id == R.id.libraryFragment) {
            try {
                findNavController().navigate(
                    R.id.action_libraryFragment_to_albumArtistDetailFragment,
                    AlbumArtistDetailFragmentArgs(albumArtist).toBundle(),
                )
            } catch (e: IllegalArgumentException) {
                Timber.e(e, "Failed to navigate to album artist detail")
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
                    viewModel.uiState.value.selectedArtists
                        .flatMap { it.mediaProviders }
                        .distinct(),
                )
                ctxToolbar.setOnMenuItemClickListener { menuItem ->
                    playlistMenuView.createPlaylistMenu(ctxToolbar.menu)
                    val selectedArtists = viewModel.selectedArtists()
                    if (playlistMenuView.handleMenuItem(menuItem, PlaylistData.AlbumArtists(selectedArtists))) {
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
        const val TAG = "AlbumArtistListFragment"

        fun newInstance() = AlbumArtistListFragment()
    }
}
