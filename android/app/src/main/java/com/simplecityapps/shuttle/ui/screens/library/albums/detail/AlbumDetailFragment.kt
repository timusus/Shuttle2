package com.simplecityapps.shuttle.ui.screens.library.albums.detail

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
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
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.ui.common.TagEditorMenuSanitiser
import com.simplecityapps.shuttle.ui.common.autoCleared
import com.simplecityapps.shuttle.ui.common.dialog.TagEditorAlertDialog
import com.simplecityapps.shuttle.ui.common.dialog.showDeleteDialog
import com.simplecityapps.shuttle.ui.common.dialog.showExcludeDialog
import com.simplecityapps.shuttle.ui.common.phrase.joinSafely
import com.simplecityapps.shuttle.ui.common.utils.toHms
import com.simplecityapps.shuttle.ui.screens.playlistmenu.CreatePlaylistDialogFragment
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistMenuPresenter
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistMenuView
import com.simplecityapps.shuttle.ui.screens.songinfo.SongInfoDialogFragment
import com.simplecityapps.shuttle.ui.theme.AppTheme
import com.squareup.phrase.ListPhrase
import com.squareup.phrase.Phrase
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AlbumDetailFragment :
    Fragment(),
    CreatePlaylistDialogFragment.Listener {

    @Inject
    lateinit var playlistMenuPresenter: PlaylistMenuPresenter

    @Inject
    lateinit var preferenceManager: GeneralPreferenceManager

    private lateinit var album: com.simplecityapps.shuttle.model.Album

    private val viewModel: AlbumDetailViewModel by viewModels()

    private var composeView: ComposeView by autoCleared()
    private var toolbar: Toolbar by autoCleared()
    private lateinit var playlistMenuView: PlaylistMenuView

    // Lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        album = AlbumDetailFragmentArgs.fromBundle(requireArguments()).album
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_album_detail, container, false)

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        playlistMenuView = PlaylistMenuView(requireContext(), playlistMenuPresenter, childFragmentManager)
        playlistMenuPresenter.bindView(playlistMenuView)

        toolbar = view.findViewById(R.id.toolbar)
        toolbar.setNavigationOnClickListener {
            NavHostFragment.findNavController(this).popBackStack()
        }
        toolbar.inflateMenu(R.menu.menu_album_detail)
        TagEditorMenuSanitiser.sanitise(toolbar.menu, album.mediaProviders)

        toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.shuffle -> {
                    viewModel.onShuffle()
                    true
                }
                R.id.queue -> {
                    viewModel.onAddAlbumToQueue()
                    true
                }
                R.id.playNext -> {
                    viewModel.onPlayAlbumNext()
                    true
                }
                R.id.editTags -> {
                    viewModel.onEditAlbumTags()
                    true
                }
                R.id.playlist -> {
                    playlistMenuView.createPlaylistMenu(toolbar.menu)
                    true
                }
                else -> {
                    playlistMenuView.handleMenuItem(menuItem, PlaylistData.Albums(album))
                }
            }
        }

        // Collect uiState for toolbar title/subtitle
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    state.album?.let { albumData ->
                        toolbar.title = albumData.name
                        val songsQuantity = Phrase.fromPlural(resources, R.plurals.songsPlural, albumData.songCount)
                            .put("count", albumData.songCount)
                            .format()
                        toolbar.subtitle = ListPhrase
                            .from(" · ")
                            .joinSafely(
                                listOf(
                                    albumData.year?.toString(),
                                    songsQuantity,
                                    albumData.duration.toHms()
                                )
                            )
                    }
                }
            }
        }

        // Collect events
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is AlbumDetailUiEvent.AddedToQueue -> {
                            Toast.makeText(
                                context,
                                Phrase.fromPlural(resources, R.plurals.queue_songs_added, event.songCount)
                                    .put("count", event.songCount)
                                    .format(),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        is AlbumDetailUiEvent.PlaybackFailed -> {
                            Toast.makeText(
                                context,
                                event.errorMessage ?: getString(R.string.error_unknown),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        is AlbumDetailUiEvent.EditTags -> {
                            TagEditorAlertDialog.newInstance(event.songs).show(childFragmentManager)
                        }
                        is AlbumDetailUiEvent.AddedToPlaylist -> {
                            Toast.makeText(
                                context,
                                event.playlistData.getPlaylistSavedMessage(resources, event.playlist.name),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        is AlbumDetailUiEvent.PlaylistDuplicatesFound -> {
                            showPlaylistDuplicatesDialog(
                                event.playlist,
                                event.playlistData,
                                event.deduplicatedSongs,
                                event.duplicates,
                            )
                        }
                        is AlbumDetailUiEvent.PlaylistAddFailed -> {
                            Toast.makeText(context, event.message ?: getString(R.string.error_unknown), Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }

        composeView = view.findViewById(R.id.composeView)
        composeView.setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val theme by preferenceManager.theme(viewLifecycleOwner.lifecycleScope).collectAsStateWithLifecycle()
            val accent by preferenceManager.accent(viewLifecycleOwner.lifecycleScope).collectAsStateWithLifecycle()

            AppTheme(
                theme = theme,
                accent = accent,
            ) {
                AlbumDetail(
                    uiState = uiState,
                    playlists = uiState.playlists.toImmutableList(),
                    onSongClick = { song -> viewModel.onSongClick(song) },
                    onAddToQueue = { song -> viewModel.onAddToQueue(song) },
                    onAddToPlaylist = { playlist, playlistData ->
                        viewModel.addToPlaylist(playlist, playlistData)
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
                    onExclude = { song ->
                        showExcludeDialog(requireContext(), song.name) {
                            viewModel.onExclude(song)
                        }
                    },
                    onEditTags = { song -> viewModel.onEditTags(song) },
                    onDelete = { song ->
                        showDeleteDialog(requireContext(), song.name) {
                            deleteSong(song)
                        }
                    },
                )
            }
        }
    }

    override fun onDestroyView() {
        playlistMenuPresenter.unbindView()
        super.onDestroyView()
    }

    // Private

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

        subtitle.text = Phrase.fromPlural(requireContext(), R.plurals.playlist_menu_duplicates_dialog_subtitle, duplicates.size)
            .putOptional("count", duplicates.size)
            .put("playlist_name", playlist.name)
            .format()

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

    // CreatePlaylistDialogFragment.Listener

    override fun onSave(
        text: String,
        playlistData: PlaylistData
    ) {
        // Playlist creation via the ViewModel is not yet implemented for album detail.
        // The playlist menu handles this through PlaylistMenuPresenter for now.
    }

    companion object {
        const val ARG_RECYCLER_STATE = "recycler_state"
    }
}
