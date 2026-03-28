package com.simplecityapps.shuttle.ui.screens.library.genres

import android.os.Bundle
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
import com.simplecityapps.shuttle.R
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
                        is GenreListUiEvent.Error -> {
                            Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
                        }
                        is GenreListUiEvent.EditTags -> {
                            TagEditorAlertDialog.newInstance(event.songs).show(childFragmentManager)
                        }
                    }
                }
            }
        }

        composeView.setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val playlists by playlistMenuPresenter.playlistsState.collectAsStateWithLifecycle()
            val theme by preferenceManager.theme(viewLifecycleOwner.lifecycleScope).collectAsStateWithLifecycle()
            val accent by preferenceManager.accent(viewLifecycleOwner.lifecycleScope).collectAsStateWithLifecycle()

            AppTheme(
                theme = theme,
                accent = accent
            ) {
                GenreList(
                    uiState = uiState,
                    playlists = playlists.toImmutableList(),
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
                        playlistMenuPresenter.addToPlaylist(playlist, playlistData)
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

    // CreatePlaylistDialogFragment.Listener Implementation

    override fun onSave(
        text: String,
        playlistData: PlaylistData
    ) {
        playlistMenuView.onSave(text, playlistData)
    }

    // Static

    companion object {
        const val TAG = "GenreListFragment"

        fun newInstance() = GenreListFragment()
    }
}
