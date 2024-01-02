package com.simplecityapps.shuttle.ui.screens.library.genres

import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.model.Genre
import com.simplecityapps.shuttle.ui.common.TagEditorMenuSanitiser
import com.simplecityapps.shuttle.ui.common.autoCleared
import com.simplecityapps.shuttle.ui.common.dialog.TagEditorAlertDialog
import com.simplecityapps.shuttle.ui.common.dialog.showExcludeDialog
import com.simplecityapps.shuttle.ui.common.error.userDescription
import com.simplecityapps.shuttle.ui.common.view.CircularLoadingView
import com.simplecityapps.shuttle.ui.common.view.HorizontalLoadingView
import com.simplecityapps.shuttle.ui.screens.library.genres.detail.GenreDetailFragmentArgs
import com.simplecityapps.shuttle.ui.screens.playlistmenu.CreatePlaylistDialogFragment
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistMenuPresenter
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistMenuView
import com.squareup.phrase.Phrase
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class GenreListFragment :
    Fragment(),
    GenreBinder.Listener,
    GenreListContract.View,
    CreatePlaylistDialogFragment.Listener {
    private var composeView: ComposeView by autoCleared()
    private var circularLoadingView: CircularLoadingView by autoCleared()
    private var horizontalLoadingView: HorizontalLoadingView by autoCleared()

    @Inject
    lateinit var presenter: GenreListPresenter

    @Inject
    lateinit var playlistMenuPresenter: PlaylistMenuPresenter

    private lateinit var playlistMenuView: PlaylistMenuView

    private var recyclerViewState: Parcelable? = null

    // Lifecycle

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_genres, container, false)
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        playlistMenuView = PlaylistMenuView(requireContext(), playlistMenuPresenter, childFragmentManager)

        composeView = view.findViewById(R.id.composeView)
        presenter.loadGenres(false)


        circularLoadingView = view.findViewById(R.id.circularLoadingView)
        horizontalLoadingView = view.findViewById(R.id.horizontalLoadingView)

        savedInstanceState?.getParcelable<Parcelable>(ARG_RECYCLER_STATE)?.let { recyclerViewState = it }

        presenter.bindView(this)
        playlistMenuPresenter.bindView(playlistMenuView)
    }

    override fun onResume() {
        super.onResume()

        presenter.loadGenres(false)
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putParcelable(ARG_RECYCLER_STATE, recyclerViewState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        presenter.unbindView()
        playlistMenuPresenter.unbindView()

        super.onDestroyView()
    }

    // GenreListContract.View Implementation

    override fun setGenres(genres: List<com.simplecityapps.shuttle.model.Genre>, resetPosition: Boolean) {
        composeView.setContent {
            GenreList(genres)
        }
    }

    @Composable
    private fun GenreList(genres: List<Genre>, modifier: Modifier = Modifier) {
        LazyColumn(
            modifier = modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(genres) { genre ->
                GenreListItem(genre)
            }
        }
    }

    @Composable
    private fun GenreListItem(genre: Genre, modifier: Modifier = Modifier) {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .clickable { this@GenreListFragment.onGenreSelected(genre) },
            ) {
                Text(
                    text = genre.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = Phrase
                        .fromPlural(LocalContext.current, R.plurals.songsPlural, genre.songCount)
                        .put("count", genre.songCount)
                        .format()
                        .toString(),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            GenreMenu(genre)
        }
    }

    @Composable
    private fun GenreMenu(genre: Genre) {
        var isMenuOpened by remember { mutableStateOf(false) }
        var isAddToPlaylistSubmenuOpen by remember { mutableStateOf(false) }

        IconButton(
            onClick = { isMenuOpened = true },
        ) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = "Genre menu",
            )
            DropdownMenu(
                expanded = isMenuOpened,
                onDismissRequest = { isMenuOpened = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(id = R.string.menu_title_play)) },
                    onClick = {
                        presenter.play(genre)
                        isMenuOpened = false
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(id = R.string.menu_title_add_to_queue)) },
                    onClick = {
                        presenter.addToQueue(genre)
                        isMenuOpened = false
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(id = R.string.menu_title_add_to_playlist)) },
                    onClick = {
                        isMenuOpened = false
                        isAddToPlaylistSubmenuOpen = true
                    },
                    trailingIcon = {
                        Icon(Icons.Default.KeyboardArrowRight, contentDescription = null)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(id = R.string.menu_title_play_next)) },
                    onClick = {
                        presenter.playNext(genre)
                        isMenuOpened = false
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(id = R.string.menu_title_exclude)) },
                    onClick = {
                        presenter.exclude(genre)
                        isMenuOpened = false
                    },
                )

                val supportsTagEditing = genre.mediaProviders.all {
                        mediaProvider -> mediaProvider.supportsTagEditing
                }

                if (supportsTagEditing) {
                    DropdownMenuItem(
                        text = { Text(stringResource(id = R.string.menu_title_edit_tags)) },
                        onClick = {
                            presenter.editTags(genre)
                            isMenuOpened = false
                        },
                    )
                }
            }
            AddToPlaylistSubmenu(
                genre = genre,
                expanded = isAddToPlaylistSubmenuOpen,
                onDismiss = { isAddToPlaylistSubmenuOpen = false },
            )
        }
    }

    @Composable
    private fun AddToPlaylistSubmenu(
        genre: Genre,
        expanded: Boolean = false,
        onDismiss: () -> Unit = {},
    ) {
        val playlistData = PlaylistData.Genres(genre)

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismiss,
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(id = R.string.playlist_menu_create_playlist)) },
                onClick = {
                    CreatePlaylistDialogFragment.newInstance(
                        playlistData,
                        context?.getString(R.string.playlist_create_dialog_playlist_name_hint)
                    ).show(childFragmentManager)
                    onDismiss()
                },
            )

            for (playlist in playlistMenuPresenter.playlists) {
                DropdownMenuItem(
                    text = { Text(playlist.name) },
                    onClick = {
                        playlistMenuPresenter.addToPlaylist(playlist, playlistData)
                        onDismiss()
                    },
                )
            }
        }
    }

    override fun onAddedToQueue(genre: com.simplecityapps.shuttle.model.Genre) {
        Toast.makeText(context, Phrase.from(requireContext(), R.string.queue_item_added).put("item_name", genre.name).format(), Toast.LENGTH_SHORT).show()
    }

    override fun setLoadingState(state: GenreListContract.LoadingState) {
        when (state) {
            is GenreListContract.LoadingState.Scanning -> {
                horizontalLoadingView.setState(HorizontalLoadingView.State.Loading(getString(R.string.library_scan_in_progress)))
                circularLoadingView.setState(CircularLoadingView.State.None)
            }
            is GenreListContract.LoadingState.Loading -> {
                horizontalLoadingView.setState(HorizontalLoadingView.State.None)
                circularLoadingView.setState(CircularLoadingView.State.Loading(getString(R.string.loading)))
            }
            is GenreListContract.LoadingState.Empty -> {
                horizontalLoadingView.setState(HorizontalLoadingView.State.None)
                circularLoadingView.setState(CircularLoadingView.State.Empty(getString(R.string.genre_list_empty)))
            }
            is GenreListContract.LoadingState.None -> {
                horizontalLoadingView.setState(HorizontalLoadingView.State.None)
                circularLoadingView.setState(CircularLoadingView.State.None)
            }
        }
    }

    override fun setLoadingProgress(progress: Progress?) {
        progress?.let {
            horizontalLoadingView.setProgress(progress.asFloat())
        }
    }

    override fun showLoadError(error: Error) {
        Toast.makeText(context, error.userDescription(resources), Toast.LENGTH_LONG).show()
    }

    override fun showTagEditor(songs: List<com.simplecityapps.shuttle.model.Song>) {
        TagEditorAlertDialog.newInstance(songs).show(childFragmentManager)
    }

    // GenreBinder.Listener Implementation

    override fun onGenreSelected(
        genre: com.simplecityapps.shuttle.model.Genre
    ) {
        if (findNavController().currentDestination?.id != R.id.genreDetailFragment) {
            findNavController().navigate(
                R.id.action_libraryFragment_to_genreDetailFragment,
                GenreDetailFragmentArgs(genre).toBundle()
            )
        }
    }

    override fun onOverflowClicked(
        view: View,
        genre: com.simplecityapps.shuttle.model.Genre
    ) {
        val popupMenu = PopupMenu(requireContext(), view)
        popupMenu.inflate(R.menu.menu_popup)
        TagEditorMenuSanitiser.sanitise(popupMenu.menu, genre.mediaProviders)

        playlistMenuView.createPlaylistMenu(popupMenu.menu)

        popupMenu.setOnMenuItemClickListener { menuItem ->
            if (playlistMenuView.handleMenuItem(menuItem, PlaylistData.Genres(genre))) {
                return@setOnMenuItemClickListener true
            } else {
                when (menuItem.itemId) {
                    R.id.play -> {
                        presenter.play(genre)
                        return@setOnMenuItemClickListener true
                    }
                    R.id.queue -> {
                        presenter.addToQueue(genre)
                        return@setOnMenuItemClickListener true
                    }
                    R.id.playNext -> {
                        presenter.playNext(genre)
                        return@setOnMenuItemClickListener true
                    }
                    R.id.exclude -> {
                        showExcludeDialog(requireContext(), genre.name) {
                            presenter.exclude(genre)
                        }
                        return@setOnMenuItemClickListener true
                    }
                    R.id.editTags -> {
                        presenter.editTags(genre)
                        return@setOnMenuItemClickListener true
                    }
                }
            }
            false
        }
        popupMenu.show()
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

        const val ARG_RECYCLER_STATE = "recycler_state"

        fun newInstance() = GenreListFragment()
    }
}
