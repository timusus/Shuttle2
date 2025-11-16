package com.simplecityapps.shuttle.ui.screens.library.genres

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.model.Genre
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.ui.common.autoCleared
import com.simplecityapps.shuttle.ui.common.dialog.TagEditorAlertDialog
import com.simplecityapps.shuttle.ui.common.error.userDescription
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

@AndroidEntryPoint
class GenreListFragment :
    Fragment(),
    CreatePlaylistDialogFragment.Listener {
    private var composeView: ComposeView by autoCleared()

    private val viewModel: GenreListViewModel by viewModels()

    @Inject
    lateinit var playlistMenuPresenter: PlaylistMenuPresenter

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

        composeView.setContent {
            val viewState by viewModel.viewState.collectAsState()
            val theme by viewModel.theme.collectAsStateWithLifecycle()
            val accent by viewModel.accent.collectAsStateWithLifecycle()

            AppTheme(
                theme = theme,
                accent = accent
            ) {
                GenreList(
                    viewState = viewState,
                    playlists = playlistMenuPresenter.playlists.toImmutableList(),
                    onSelectGenre = {
                        onGenreSelected(it)
                    },
                    onPlayGenre = { genre ->
                        viewModel.play(genre) { result ->
                            result.onFailure { error -> showLoadError(error as Error) }
                        }
                    },
                    onAddToQueue = { genre ->
                        viewModel.addToQueue(genre) { result ->
                            result.onSuccess { genre ->
                                onAddedToQueue(genre)
                            }
                        }
                    },
                    onPlayNext = { genre ->
                        viewModel.playNext(genre) { result ->
                            result.onSuccess { genre ->
                                onAddedToQueue(genre)
                            }
                        }
                    },
                    onExclude = { genre ->
                        viewModel.exclude(genre)
                    },
                    onEditTags = { genre ->
                        viewModel.editTags(genre) { result ->
                            result.onSuccess { songs ->
                                showTagEditor(songs)
                            }
                        }
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

    fun onAddedToQueue(genre: Genre) {
        Toast.makeText(context, Phrase.from(requireContext(), R.string.queue_item_added).put("item_name", genre.name).format(), Toast.LENGTH_SHORT).show()
    }

    fun showLoadError(error: Error) {
        Toast.makeText(context, error.userDescription(resources), Toast.LENGTH_LONG).show()
    }

    fun showTagEditor(songs: List<Song>) {
        TagEditorAlertDialog.newInstance(songs).show(childFragmentManager)
    }

    private fun onGenreSelected(genre: Genre) {
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

//    sealed class LoadingState {
//        data object Scanning : LoadingState()
//        data object Loading : LoadingState()
//        data object Empty : LoadingState()
//    }
}
