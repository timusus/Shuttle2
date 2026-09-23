package com.simplecityapps.shuttle.ui.screens.library.folders

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.SwitchCompat
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.ui.common.autoCleared
import com.simplecityapps.shuttle.ui.common.dialog.TagEditorAlertDialog
import com.simplecityapps.shuttle.ui.common.dialog.showDeleteDialog
import com.simplecityapps.shuttle.ui.screens.playlistmenu.CreatePlaylistDialogFragment
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData
import com.simplecityapps.shuttle.ui.screens.songinfo.SongInfoDialogFragment
import com.simplecityapps.shuttle.ui.theme.AppTheme
import com.squareup.phrase.Phrase
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch

@AndroidEntryPoint
class FolderListFragment :
    Fragment(),
    CreatePlaylistDialogFragment.Listener {
    private var composeView: ComposeView by autoCleared()

    private val viewModel: FolderListViewModel by viewModels()

    @Inject
    lateinit var preferenceManager: GeneralPreferenceManager

    /**
     * Back goes up a folder. Only while this tab is the visible page: off-screen library pages are still STARTED,
     * so a lifecycle-scoped callback alone would steal back presses from the other tabs.
     */
    private val navigateUpCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            viewModel.onNavigateUp()
        }
    }

    // Lifecycle

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_folders, container, false)

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        composeView = view.findViewById(R.id.composeView)

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, navigateUpCallback)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                try {
                    viewModel.uiState.collect { uiState ->
                        navigateUpCallback.isEnabled = uiState.canNavigateUp
                    }
                } finally {
                    navigateUpCallback.isEnabled = false
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is FolderListUiEvent.FolderAddedToQueue -> {
                            showAddedToQueueToast(event.folder.displayName(resources))
                        }
                        is FolderListUiEvent.SongAddedToQueue -> {
                            showAddedToQueueToast(event.song.name ?: getString(com.simplecityapps.core.R.string.unknown))
                        }
                        is FolderListUiEvent.PlaybackFailed -> {
                            Toast.makeText(
                                context,
                                event.errorMessage ?: getString(R.string.error_unknown),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        is FolderListUiEvent.AddedToPlaylist -> {
                            Toast.makeText(
                                context,
                                event.playlistData.getPlaylistSavedMessage(resources, event.playlist.name),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        is FolderListUiEvent.PlaylistDuplicatesFound -> {
                            showPlaylistDuplicatesDialog(event.playlist, event.playlistData, event.deduplicatedSongs, event.duplicates)
                        }
                        is FolderListUiEvent.PlaylistAddFailed -> {
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
                accent = accent
            ) {
                FolderList(
                    uiState = uiState,
                    playlists = uiState.playlists.toImmutableList(),
                    onFolderClick = { folder -> viewModel.onFolderClick(folder) },
                    onNavigateUp = { viewModel.onNavigateUp() },
                    onPlayFolder = { folder -> viewModel.onPlay(folder) },
                    onShuffleFolder = { folder -> viewModel.onShuffle(folder) },
                    onAddFolderToQueue = { folder -> viewModel.onAddToQueue(folder) },
                    onPlayFolderNext = { folder -> viewModel.onPlayNext(folder) },
                    onSongClick = { song -> viewModel.onSongClick(song) },
                    onAddSongToQueue = { song -> viewModel.onAddToQueue(song) },
                    onPlaySongNext = { song -> viewModel.onPlayNext(song) },
                    onSongInfo = { song ->
                        SongInfoDialogFragment.newInstance(song).show(childFragmentManager)
                    },
                    onExcludeSong = { song -> viewModel.onExclude(song) },
                    onEditSongTags = { song ->
                        TagEditorAlertDialog.newInstance(listOf(song)).show(childFragmentManager)
                    },
                    onDeleteSong = { song ->
                        showDeleteDialog(requireContext(), song.name) {
                            deleteSong(song)
                        }
                    },
                    onAddToPlaylist = { playlist, playlistData ->
                        viewModel.addToPlaylist(playlist, playlistData)
                    },
                    onShowCreatePlaylistDialog = { playlistData ->
                        CreatePlaylistDialogFragment.newInstance(
                            playlistData,
                            context?.getString(R.string.playlist_create_dialog_playlist_name_hint)
                        ).show(childFragmentManager)
                    }
                )
            }
        }
    }

    private fun showAddedToQueueToast(itemName: String) {
        Toast.makeText(
            context,
            Phrase.from(requireContext(), R.string.queue_item_added)
                .put("item_name", itemName)
                .format(),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun deleteSong(song: Song) {
        val documentFile = DocumentFile.fromSingleUri(requireContext(), song.path.toUri())
        if (documentFile?.delete() == false) {
            Toast.makeText(context, R.string.delete_song_failed, Toast.LENGTH_LONG).show()
            return
        }
        viewModel.onSongDeleted(song)
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
        const val TAG = "FolderListFragment"

        fun newInstance() = FolderListFragment()
    }
}
