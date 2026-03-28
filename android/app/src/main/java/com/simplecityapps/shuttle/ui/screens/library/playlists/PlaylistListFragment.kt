package com.simplecityapps.shuttle.ui.screens.library.playlists

import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
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
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.ui.common.autoCleared
import com.simplecityapps.shuttle.ui.common.dialog.EditTextAlertDialog
import com.simplecityapps.shuttle.ui.screens.library.playlists.detail.PlaylistDetailFragmentArgs
import com.simplecityapps.shuttle.ui.screens.library.playlists.smart.SmartPlaylistDetailFragmentArgs
import com.simplecityapps.shuttle.ui.theme.AppTheme
import com.squareup.phrase.Phrase
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PlaylistListFragment :
    Fragment(),
    EditTextAlertDialog.Listener {

    private var composeView: ComposeView by autoCleared()

    private val viewModel: PlaylistListViewModel by viewModels()

    @Inject
    lateinit var preferenceManager: GeneralPreferenceManager

    // Lifecycle

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_playlists, container, false)

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        composeView = view.findViewById(R.id.composeView)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is PlaylistListUiEvent.AddedToQueue -> {
                            Toast.makeText(
                                context,
                                Phrase.from(requireContext(), R.string.queue_item_added)
                                    .put("item_name", event.playlistName)
                                    .format(),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        is PlaylistListUiEvent.PlaybackFailed -> {
                            Toast.makeText(
                                context,
                                event.errorMessage ?: getString(R.string.error_unknown),
                                Toast.LENGTH_LONG
                            ).show()
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
                PlaylistList(
                    uiState = uiState,
                    onPlaylistClick = { playlist ->
                        onPlaylistSelected(playlist)
                    },
                    onSmartPlaylistClick = { smartPlaylist ->
                        onSmartPlaylistSelected(smartPlaylist)
                    },
                    onPlay = { playlist ->
                        viewModel.onPlay(playlist)
                    },
                    onAddToQueue = { playlist ->
                        viewModel.onAddToQueue(playlist)
                    },
                    onPlayNext = { playlist ->
                        viewModel.onPlayNext(playlist)
                    },
                    onDelete = { playlist ->
                        showDeleteConfirmation(playlist)
                    },
                    onClear = { playlist ->
                        showClearConfirmation(playlist)
                    },
                    onRename = { playlist ->
                        EditTextAlertDialog
                            .newInstance(
                                title = getString(R.string.playlist_dialog_title_rename),
                                hint = getString(R.string.playlist_dialog_hint_rename),
                                initialText = playlist.name,
                                extra = playlist
                            )
                            .show(childFragmentManager)
                    }
                )
            }
        }
    }

    private fun onPlaylistSelected(playlist: Playlist) {
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

    private fun onSmartPlaylistSelected(smartPlaylist: com.simplecityapps.shuttle.model.SmartPlaylist) {
        if (findNavController().currentDestination?.id != R.id.playlistDetailFragment) {
            findNavController().navigate(
                R.id.action_libraryFragment_to_smartPlaylistDetailFragment,
                SmartPlaylistDetailFragmentArgs(smartPlaylist).toBundle(),
                null,
                null
            )
        }
    }

    private fun showDeleteConfirmation(playlist: Playlist) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.playlist_dialog_title_delete))
            .setMessage(
                Phrase.from(requireContext(), R.string.playlist_dialog_subtitle_delete)
                    .put("playlist_name", playlist.name)
                    .format()
            )
            .setPositiveButton(getString(R.string.playlist_dialog_button_delete)) { _, _ -> viewModel.onDelete(playlist) }
            .setNegativeButton(getString(R.string.dialog_button_cancel), null)
            .show()
    }

    private fun showClearConfirmation(playlist: Playlist) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.playlist_dialog_title_clear))
            .setMessage(
                Phrase.from(requireContext(), R.string.playlist_dialog_subtitle_clear)
                    .put("playlist_name", playlist.name)
                    .format()
            )
            .setPositiveButton(getString(R.string.playlist_dialog_button_clear)) { _, _ -> viewModel.onClear(playlist) }
            .setNegativeButton(getString(R.string.dialog_button_cancel), null)
            .show()
    }

    // EditTextAlertDialog.Listener

    override fun onSave(
        text: String?,
        extra: Parcelable?
    ) {
        viewModel.onRename(extra as Playlist, text!!)
    }

    // Static

    companion object {
        const val TAG = "PlaylistListFragment"

        fun newInstance() = PlaylistListFragment()
    }
}
