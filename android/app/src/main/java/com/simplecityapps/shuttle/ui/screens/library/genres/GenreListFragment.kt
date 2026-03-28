package com.simplecityapps.shuttle.ui.screens.library.genres

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
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
import com.simplecityapps.shuttle.ui.common.autoCleared
import com.simplecityapps.shuttle.ui.common.dialog.TagEditorAlertDialog
import com.simplecityapps.shuttle.ui.screens.library.genres.detail.GenreDetailFragmentArgs
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

@AndroidEntryPoint
class GenreListFragment :
    Fragment(),
    CreatePlaylistDialogFragment.Listener {
    private var composeView: ComposeView by autoCleared()

    private val viewModel: GenreListViewModel by viewModels()

    @Inject
    lateinit var playlistMenuPresenter: PlaylistMenuPresenter

    @Inject
    lateinit var preferenceManager: GeneralPreferenceManager

    private lateinit var playlistMenuView: PlaylistMenuView

    // Lifecycle

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_genres, container, false)

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        playlistMenuView = PlaylistMenuView(requireContext(), playlistMenuPresenter, childFragmentManager)
        playlistMenuPresenter.bindView(playlistMenuView)

        composeView = view.findViewById(R.id.composeView)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is GenreListUiEvent.AddedToQueue -> {
                            Toast.makeText(
                                context,
                                Phrase.from(requireContext(), R.string.queue_item_added)
                                    .put("item_name", event.genreName)
                                    .format(),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        is GenreListUiEvent.PlaybackFailed -> {
                            Toast.makeText(
                                context,
                                event.errorMessage ?: getString(R.string.error_unknown),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        is GenreListUiEvent.EditTags -> {
                            TagEditorAlertDialog.newInstance(event.songs).show(childFragmentManager)
                        }
                        is GenreListUiEvent.AddedToPlaylist -> {
                            Toast.makeText(
                                context,
                                event.playlistData.getPlaylistSavedMessage(resources, event.playlist.name),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        is GenreListUiEvent.PlaylistDuplicatesFound -> {
                            showPlaylistDuplicatesDialog(event.playlist, event.playlistData, event.deduplicatedSongs, event.duplicates)
                        }
                        is GenreListUiEvent.PlaylistAddFailed -> {
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
                GenreList(
                    uiState = uiState,
                    playlists = uiState.playlists.toImmutableList(),
                    onSelectGenre = { genre ->
                        onGenreSelected(genre)
                    },
                    onPlayGenre = { genre ->
                        viewModel.onPlay(genre)
                    },
                    onAddToQueue = { genre ->
                        viewModel.onAddToQueue(genre)
                    },
                    onPlayNext = { genre ->
                        viewModel.onPlayNext(genre)
                    },
                    onExclude = { genre ->
                        viewModel.onExclude(genre)
                    },
                    onEditTags = { genre ->
                        viewModel.onEditTags(genre)
                    },
                    onAddToPlaylist = { playlist, playlistData ->
                        viewModel.addToPlaylist(playlist, playlistData)
                    },
                    onShowCreatePlaylistDialog = { genre ->
                        CreatePlaylistDialogFragment.newInstance(
                            PlaylistData.Genres(genre),
                            context?.getString(R.string.playlist_create_dialog_playlist_name_hint)
                        ).show(childFragmentManager)
                    }
                )
            }
        }
    }

    override fun onDestroyView() {
        playlistMenuPresenter.unbindView()

        super.onDestroyView()
    }

    private fun onGenreSelected(genre: com.simplecityapps.shuttle.model.Genre) {
        if (findNavController().currentDestination?.id != R.id.genreDetailFragment) {
            findNavController().navigate(
                R.id.action_libraryFragment_to_genreDetailFragment,
                GenreDetailFragmentArgs(genre).toBundle()
            )
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
        const val TAG = "GenreListFragment"

        fun newInstance() = GenreListFragment()
    }
}
