package com.simplecityapps.shuttle.ui.screens.library.genres

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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.model.Genre
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.sorting.GenreSortOrder
import com.simplecityapps.shuttle.ui.common.autoCleared
import com.simplecityapps.shuttle.ui.common.dialog.TagEditorAlertDialog
import com.simplecityapps.shuttle.ui.common.error.userDescription
import com.simplecityapps.shuttle.ui.common.view.CircularLoadingView
import com.simplecityapps.shuttle.ui.common.view.HorizontalLoadingView
import com.simplecityapps.shuttle.ui.common.view.findToolbarHost
import com.simplecityapps.shuttle.ui.screens.library.genres.detail.GenreDetailFragmentArgs
import com.simplecityapps.shuttle.ui.screens.playlistmenu.CreatePlaylistDialogFragment
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistMenuPresenter
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistMenuView
import com.simplecityapps.shuttle.ui.theme.AppTheme
import com.squareup.phrase.Phrase
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class GenreListFragment :
    Fragment(),
    CreatePlaylistDialogFragment.Listener {
    private var composeView: ComposeView by autoCleared()
    private var circularLoadingView: CircularLoadingView by autoCleared()
    private var horizontalLoadingView: HorizontalLoadingView by autoCleared()

    private val viewModel: GenreListViewModel by viewModels()

    @Inject
    lateinit var playlistMenuPresenter: PlaylistMenuPresenter

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
    ): View? = inflater.inflate(R.layout.fragment_genres, container, false)

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        circularLoadingView = view.findViewById(R.id.circularLoadingView)
        horizontalLoadingView = view.findViewById(R.id.horizontalLoadingView)

        playlistMenuView = PlaylistMenuView(requireContext(), playlistMenuPresenter, childFragmentManager)
        playlistMenuPresenter.bindView(playlistMenuView)

        composeView = view.findViewById(R.id.composeView)

        composeView.setContent {
            val viewState by viewModel.viewState.collectAsState()

            val theme by viewModel.theme.collectAsStateWithLifecycle()
            val accent by viewModel.accent.collectAsStateWithLifecycle()
            val extraDark by viewModel.extraDark.collectAsStateWithLifecycle()

            AppTheme(
                theme = theme,
                accent = accent,
                extraDark = extraDark
            ) {
                GenreList(
                    viewState = viewState,
                    setToolbarMenu = { sortOrder ->
                        updateToolbarMenu(sortOrder)
                    },
                    playlists = playlistMenuPresenter.playlists,
                    setLoadingState = {
                        setLoadingState(it)
                    },
                    setLoadingProgress = {
                        setLoadingProgress(it)
                    },
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

    override fun onCreateOptionsMenu(
        menu: Menu,
        inflater: MenuInflater
    ) {
        super.onCreateOptionsMenu(menu, inflater)

        inflater.inflate(R.menu.menu_genre_list, menu)
    }

    override fun onDestroyView() {
        playlistMenuPresenter.unbindView()

        super.onDestroyView()
    }

    // Toolbar item selection

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.sortGenreName -> {
            viewModel.setSortOrder(GenreSortOrder.Name)
            true
        }
        R.id.sortSongCount -> {
            viewModel.setSortOrder(GenreSortOrder.SongCount)
            true
        }
        else -> false
    }

    private fun updateToolbarMenu(sortOrder: GenreSortOrder) {
        findToolbarHost()?.toolbar?.menu?.let { menu ->
            when (sortOrder) {
                GenreSortOrder.Name -> menu.findItem(R.id.sortGenreName)?.isChecked = true
                GenreSortOrder.SongCount -> menu.findItem(R.id.sortSongCount)?.isChecked = true
                else -> {
                    // Nothing to do
                }
            }
        }
    }

    fun onAddedToQueue(genre: Genre) {
        Toast.makeText(context, Phrase.from(requireContext(), R.string.queue_item_added).put("item_name", genre.name).format(), Toast.LENGTH_SHORT).show()
    }

    private fun setLoadingState(state: LoadingState) {
        when (state) {
            is LoadingState.Scanning -> {
                horizontalLoadingView.setState(HorizontalLoadingView.State.Loading(getString(R.string.library_scan_in_progress)))
                circularLoadingView.setState(CircularLoadingView.State.None)
            }

            is LoadingState.Loading -> {
                horizontalLoadingView.setState(HorizontalLoadingView.State.None)
                circularLoadingView.setState(CircularLoadingView.State.Loading(getString(R.string.loading)))
            }

            is LoadingState.Empty -> {
                horizontalLoadingView.setState(HorizontalLoadingView.State.None)
                circularLoadingView.setState(CircularLoadingView.State.Empty(getString(R.string.genre_list_empty)))
            }

            is LoadingState.None -> {
                horizontalLoadingView.setState(HorizontalLoadingView.State.None)
                circularLoadingView.setState(CircularLoadingView.State.None)
            }
        }
    }

    private fun setLoadingProgress(progress: Progress?) {
        progress?.let {
            horizontalLoadingView.setProgress(progress.asFloat())
        }
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

    sealed class LoadingState {
        data object Scanning : LoadingState()
        data object Loading : LoadingState()
        data object Empty : LoadingState()
        data object None : LoadingState()
    }
}
