package com.simplecityapps.shuttle.ui.screens.library.playlists.detail

import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.simplecityapps.adapter.RecyclerAdapter
import com.simplecityapps.mediaprovider.model.Playlist
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.TagEditorMenuSanitiser
import com.simplecityapps.shuttle.ui.common.autoCleared
import com.simplecityapps.shuttle.ui.common.dialog.EditTextAlertDialog
import com.simplecityapps.shuttle.ui.common.dialog.ShowDeleteDialog
import com.simplecityapps.shuttle.ui.common.dialog.ShowExcludeDialog
import com.simplecityapps.shuttle.ui.common.dialog.TagEditorAlertDialog
import com.simplecityapps.shuttle.ui.common.error.userDescription
import com.simplecityapps.shuttle.ui.common.utils.toHms
import com.simplecityapps.shuttle.ui.screens.library.songs.SongBinder
import com.simplecityapps.shuttle.ui.screens.playlistmenu.CreatePlaylistDialogFragment
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistMenuPresenter
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistMenuView
import com.simplecityapps.shuttle.ui.screens.songinfo.SongInfoDialogFragment
import com.squareup.phrase.Phrase
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class PlaylistDetailFragment :
    Fragment(),
    PlaylistDetailContract.View,
    CreatePlaylistDialogFragment.Listener,
    EditTextAlertDialog.Listener {

    @Inject
    lateinit var presenterFactory: PlaylistDetailPresenter.Factory

    @Inject
    lateinit var playlistMenuPresenter: PlaylistMenuPresenter

    @Inject
    lateinit var imageLoader: ArtworkImageLoader

    private lateinit var presenter: PlaylistDetailPresenter

    private var adapter: RecyclerAdapter by autoCleared()

    private lateinit var playlist: Playlist

    private lateinit var playlistMenuView: PlaylistMenuView

    private var toolbar: Toolbar? = null

    private var recyclerView: RecyclerView by autoCleared()

    private var heroImage: ImageView by autoCleared()


    // Lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        playlist = PlaylistDetailFragmentArgs.fromBundle(requireArguments()).playlist
        presenter = presenterFactory.create(playlist)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_playlist_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.recyclerView)
        toolbar = view.findViewById(R.id.toolbar)

        playlistMenuView = PlaylistMenuView(requireContext(), playlistMenuPresenter, childFragmentManager)

        adapter = RecyclerAdapter(viewLifecycleOwner.lifecycleScope)

        toolbar?.let { toolbar ->
            toolbar.setNavigationOnClickListener {
                NavHostFragment.findNavController(this).popBackStack()
            }
            MenuInflater(context).inflate(R.menu.menu_playlist_detail, toolbar.menu)
            toolbar.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.shuffle -> {
                        presenter.shuffle()
                        true
                    }
                    R.id.queue -> {
                        presenter.addToQueue(playlist)
                        true
                    }
                    R.id.rename -> {
                        EditTextAlertDialog
                            .newInstance(
                                title = getString(R.string.playlist_dialog_title_rename),
                                hint = getString(R.string.playlist_dialog_hint_rename),
                                initialText = playlist.name,
                                extra = playlist
                            )
                            .show(childFragmentManager)
                        true
                    }
                    R.id.clear -> {
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle(getString(R.string.playlist_dialog_title_clear))
                            .setMessage(getString(R.string.playlist_dialog_subtitle_clear))
                            .setPositiveButton(getString(R.string.playlist_dialog_button_clear)) { _, _ -> presenter.clear(playlist) }
                            .setNegativeButton(getString(R.string.dialog_button_cancel), null)
                            .show()
                        true
                    }
                    R.id.delete -> {
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle(getString(R.string.playlist_dialog_title_delete))
                            .setMessage(getString(R.string.playlist_dialog_subtitle_delete))
                            .setPositiveButton(getString(R.string.playlist_dialog_button_delete)) { _, _ -> presenter.delete(playlist) }
                            .setNegativeButton(getString(R.string.dialog_button_cancel), null)
                            .show()
                        true
                    }
                    else -> {
                        false
                    }
                }
            }
        }

        recyclerView.adapter = adapter

        heroImage = view.findViewById(R.id.heroImage)

        presenter.bindView(this)
        playlistMenuPresenter.bindView(playlistMenuView)
    }

    override fun onResume() {
        super.onResume()

        presenter.loadData()
    }

    override fun onDestroyView() {
        presenter.unbindView()
        playlistMenuPresenter.unbindView()

        super.onDestroyView()
    }


    // PlaylistDetailContract.View Implementation

    override fun setPlaylist(playlist: Playlist) {
        toolbar?.title = playlist.name
        val quantityString = Phrase.fromPlural(requireContext(), R.plurals.songsPlural, playlist.songCount)
            .put("count", playlist.songCount)
            .format()
        toolbar?.subtitle = Phrase.from(requireContext(), R.string.songs_duration)
            .put("song_count", quantityString)
            .put("duration", playlist.duration.toHms())
            .format()
    }

    override fun setData(songs: List<Song>) {
        if (songs.isNotEmpty()) {
            if (heroImage.drawable == null) {
                imageLoader.loadArtwork(
                    heroImage,
                    songs.random(),
                    listOf(
                        ArtworkImageLoader.Options.Placeholder(R.drawable.ic_placeholder_playlist),
                        ArtworkImageLoader.Options.Priority.Max
                    ),
                )
            }
        } else {
            heroImage.setImageResource(R.drawable.ic_placeholder_playlist)
        }

        adapter.update(songs.map { song ->
            SongBinder(song, imageLoader, songBinderListener)
        })
    }

    override fun onAddedToQueue(song: Song) {
        Toast.makeText(context, Phrase.from(requireContext(), R.string.queue_item_added).put("itemName", song.name).format(), Toast.LENGTH_SHORT).show()
    }

    override fun onAddedToQueue(playlist: Playlist) {
        Toast.makeText(context, Phrase.from(requireContext(), R.string.queue_item_added).put("itemName", playlist.name).format(), Toast.LENGTH_SHORT).show()
    }

    override fun showLoadError(error: Error) {
        Toast.makeText(context, error.userDescription(resources), Toast.LENGTH_LONG).show()
    }

    override fun showDeleteError(error: Error) {
        Toast.makeText(requireContext(), error.userDescription(resources), Toast.LENGTH_LONG).show()
    }

    override fun showTagEditor(songs: List<Song>) {
        TagEditorAlertDialog.newInstance(songs).show(childFragmentManager)
    }

    override fun dismiss() {
        findNavController().popBackStack()
    }


    // SongBinder.Listener Implementation

    private val songBinderListener = object : SongBinder.Listener {

        override fun onSongClicked(song: Song) {
            presenter.onSongClicked(song)
        }

        override fun onSongLongClicked(song: Song) {

        }

        override fun onOverflowClicked(view: View, song: Song) {
            val popupMenu = PopupMenu(requireContext(), view)
            popupMenu.inflate(R.menu.menu_popup_playlist_song)
            TagEditorMenuSanitiser.sanitise(popupMenu.menu, listOf(song.mediaProvider))

            playlistMenuView.createPlaylistMenu(popupMenu.menu)

            if (song.mediaStoreId != null) {
                popupMenu.menu.findItem(R.id.delete)?.isVisible = false
            }

            popupMenu.setOnMenuItemClickListener { menuItem ->
                if (playlistMenuView.handleMenuItem(menuItem, PlaylistData.Songs(song))) {
                    return@setOnMenuItemClickListener true
                } else {
                    when (menuItem.itemId) {
                        R.id.queue -> {
                            presenter.addToQueue(song)
                            return@setOnMenuItemClickListener true
                        }
                        R.id.playNext -> {
                            presenter.playNext(song)
                            return@setOnMenuItemClickListener true
                        }
                        R.id.songInfo -> {
                            SongInfoDialogFragment.newInstance(song).show(childFragmentManager)
                            return@setOnMenuItemClickListener true
                        }
                        R.id.exclude -> {
                            ShowExcludeDialog(requireContext(), song.name) {
                                presenter.exclude(song)
                            }
                            return@setOnMenuItemClickListener true
                        }
                        R.id.editTags -> {
                            presenter.editTags(song)
                            return@setOnMenuItemClickListener true
                        }
                        R.id.remove -> {
                            presenter.remove(song)
                            return@setOnMenuItemClickListener true
                        }
                        R.id.delete -> {
                            ShowDeleteDialog(requireContext(), song.name) {
                                presenter.delete(song)
                            }
                            return@setOnMenuItemClickListener true
                        }
                    }
                }
                false
            }
            popupMenu.show()
        }
    }


    // CreatePlaylistDialogFragment.Listener Implementation

    override fun onSave(text: String, playlistData: PlaylistData) {
        playlistMenuPresenter.createPlaylist(text, playlistData)
    }


    // EditTextAlertDialog.Listener

    override fun onSave(text: String?, extra: Parcelable?) {
        presenter.rename(extra as Playlist, text!!) // default validation ensures text is not null
    }
}