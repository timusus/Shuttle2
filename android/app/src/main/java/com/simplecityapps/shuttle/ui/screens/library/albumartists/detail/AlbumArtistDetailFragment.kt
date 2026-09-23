package com.simplecityapps.shuttle.ui.screens.library.albumartists.detail

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
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
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.ui.common.autoCleared
import com.simplecityapps.shuttle.ui.common.dialog.TagEditorAlertDialog
import com.simplecityapps.shuttle.ui.common.dialog.showDeleteDialog
import com.simplecityapps.shuttle.ui.common.dialog.showExcludeDialog
import com.simplecityapps.shuttle.ui.screens.library.albums.detail.AlbumDetailFragmentArgs
import com.simplecityapps.shuttle.ui.screens.playlistmenu.CreatePlaylistDialogFragment
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistMenuPresenter
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistMenuView
import com.simplecityapps.shuttle.ui.screens.songinfo.SongInfoDialogFragment
import com.simplecityapps.shuttle.ui.theme.AppTheme
import com.squareup.phrase.Phrase
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AlbumArtistDetailFragment :
    Fragment(),
    CreatePlaylistDialogFragment.Listener {

    @Inject
    lateinit var playlistMenuPresenter: PlaylistMenuPresenter

    @Inject
    lateinit var preferenceManager: GeneralPreferenceManager

    private lateinit var albumArtist: com.simplecityapps.shuttle.model.AlbumArtist

    private val viewModel: AlbumArtistDetailViewModel by viewModels()

    private var composeView: ComposeView by autoCleared()
    private lateinit var playlistMenuView: PlaylistMenuView

    // Lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        albumArtist = AlbumArtistDetailFragmentArgs.fromBundle(requireArguments()).albumArtist
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_album_artist_detail, container, false)

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        playlistMenuView = PlaylistMenuView(requireContext(), playlistMenuPresenter, childFragmentManager)
        playlistMenuPresenter.bindView(playlistMenuView)

        // Collect events
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is AlbumArtistDetailUiEvent.AddedToQueue -> {
                            Toast.makeText(
                                context,
                                Phrase.fromPlural(resources, R.plurals.queue_songs_added, event.songCount)
                                    .put("count", event.songCount)
                                    .format(),
                                Toast.LENGTH_SHORT
                            ).show()
                        }

                        is AlbumArtistDetailUiEvent.PlaybackFailed -> {
                            Toast.makeText(
                                context,
                                event.errorMessage ?: getString(R.string.error_unknown),
                                Toast.LENGTH_LONG
                            ).show()
                        }

                        is AlbumArtistDetailUiEvent.EditTags -> {
                            TagEditorAlertDialog.newInstance(event.songs).show(childFragmentManager)
                        }

                        is AlbumArtistDetailUiEvent.AddedToPlaylist -> {
                            Toast.makeText(
                                context,
                                event.playlistData.getPlaylistSavedMessage(resources, event.playlist.name),
                                Toast.LENGTH_LONG
                            ).show()
                        }

                        is AlbumArtistDetailUiEvent.PlaylistDuplicatesFound -> {
                            showPlaylistDuplicatesDialog(
                                event.playlist,
                                event.playlistData,
                                event.deduplicatedSongs,
                                event.duplicates,
                            )
                        }

                        is AlbumArtistDetailUiEvent.PlaylistAddFailed -> {
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
                AlbumArtistDetail(
                    uiState = uiState,
                    playlists = uiState.playlists.toImmutableList(),
                    onNavigateUp = { NavHostFragment.findNavController(this@AlbumArtistDetailFragment).popBackStack() },
                    onPlay = { viewModel.onPlayAll() },
                    onShuffle = { viewModel.onShuffleAll() },
                    onShuffleAlbums = { viewModel.onShuffleAlbums() },
                    onAddAllToQueue = { viewModel.onAddAllToQueue() },
                    onPlayAllNext = { viewModel.onPlayAllNext() },
                    onEditArtistTags = { viewModel.onEditArtistTags() },
                    onAddArtistToPlaylist = {
                        CreatePlaylistDialogFragment.newInstance(
                            PlaylistData.AlbumArtists(albumArtist),
                            context?.getString(R.string.playlist_create_dialog_playlist_name_hint)
                        ).show(childFragmentManager)
                    },
                    onAlbumClick = { album -> viewModel.onAlbumClick(album) },
                    onOpenAlbum = { album ->
                        view.findNavController().navigate(
                            R.id.action_albumArtistDetailFragment_to_albumDetailFragment,
                            AlbumDetailFragmentArgs(album).toBundle(),
                        )
                    },
                    onAlbumPlay = { album -> viewModel.onPlayAlbum(album) },
                    onAlbumAddToQueue = { album -> viewModel.onAddAlbumToQueue(album) },
                    onAlbumPlayNext = { album -> viewModel.onPlayAlbumNext(album) },
                    onAlbumExclude = { album -> viewModel.onExcludeAlbum(album) },
                    onAlbumEditTags = { album -> viewModel.onEditAlbumTags(album) },
                    onAlbumAddToPlaylist = { playlist, playlistData ->
                        viewModel.addToPlaylist(playlist, playlistData)
                    },
                    onAlbumShowCreatePlaylistDialog = { album ->
                        CreatePlaylistDialogFragment.newInstance(
                            PlaylistData.Albums(album),
                            context?.getString(R.string.playlist_create_dialog_playlist_name_hint)
                        ).show(childFragmentManager)
                    },
                    onSongClick = { song -> viewModel.onSongClick(song) },
                    onAlbumSongClick = { song, songs -> viewModel.onAlbumSongClick(song, songs) },
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
                            viewModel.onExcludeSong(song)
                        }
                    },
                    onEditTags = { song -> viewModel.onEditSongTags(song) },
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
        // Playlist creation handled through PlaylistMenuPresenter for now.
    }
}
