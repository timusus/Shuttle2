package com.simplecityapps.shuttle.ui.screens.library.playlists.detail

import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.Toolbar
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.simplecityapps.adapter.RecyclerAdapter
import com.simplecityapps.mediaprovider.model.Playlist
import com.simplecityapps.mediaprovider.model.PlaylistSong
import com.simplecityapps.mediaprovider.repository.PlaylistSongSortOrder
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.TagEditorMenuSanitiser
import com.simplecityapps.shuttle.ui.common.autoCleared
import com.simplecityapps.shuttle.ui.common.dialog.EditTextAlertDialog
import com.simplecityapps.shuttle.ui.common.dialog.ShowDeleteDialog
import com.simplecityapps.shuttle.ui.common.dialog.ShowExcludeDialog
import com.simplecityapps.shuttle.ui.common.dialog.TagEditorAlertDialog
import com.simplecityapps.shuttle.ui.common.error.userDescription
import com.simplecityapps.shuttle.ui.common.phrase.joinSafely
import com.simplecityapps.shuttle.ui.common.recyclerview.ItemTouchHelperCallback
import com.simplecityapps.shuttle.ui.common.utils.toHms
import com.simplecityapps.shuttle.ui.screens.playlistmenu.CreatePlaylistDialogFragment
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistMenuPresenter
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistMenuView
import com.simplecityapps.shuttle.ui.screens.songinfo.SongInfoDialogFragment
import com.squareup.phrase.ListPhrase
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
            toolbar.inflateMenu(R.menu.menu_playlist_detail)
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
                            .setMessage(
                                Phrase.from(requireContext(), R.string.playlist_dialog_subtitle_clear)
                                    .put("playlist_name", playlist.name)
                                    .format()
                            )
                            .setPositiveButton(getString(R.string.playlist_dialog_button_clear)) { _, _ -> presenter.clear(playlist) }
                            .setNegativeButton(getString(R.string.dialog_button_cancel), null)
                            .show()
                        true
                    }
                    R.id.delete -> {
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle(getString(R.string.playlist_dialog_title_delete))
                            .setMessage(
                                Phrase.from(requireContext(), R.string.playlist_dialog_subtitle_delete)
                                    .put("playlist_name", playlist.name)
                                    .format()
                            )
                            .setPositiveButton(getString(R.string.playlist_dialog_button_delete)) { _, _ -> presenter.delete(playlist) }
                            .setNegativeButton(getString(R.string.dialog_button_cancel), null)
                            .show()
                        true
                    }
                    R.id.sortCustom -> {
                        presenter.setSortOrder(PlaylistSongSortOrder.Position)
                        true
                    }
                    R.id.sortSongName -> {
                        presenter.setSortOrder(PlaylistSongSortOrder.SongName)
                        true
                    }
                    R.id.sortArtistName -> {
                        presenter.setSortOrder(PlaylistSongSortOrder.ArtistGroupKey)
                        true
                    }
                    R.id.sortAlbumName -> {
                        presenter.setSortOrder(PlaylistSongSortOrder.AlbumGroupKey)
                        true
                    }
                    R.id.sortSongYear -> {
                        presenter.setSortOrder(PlaylistSongSortOrder.Year)
                        true
                    }
                    R.id.sortSongDuration -> {
                        presenter.setSortOrder(PlaylistSongSortOrder.Duration)
                        true
                    }
                    else -> {
                        false
                    }
                }
            }
        }

        recyclerView.adapter = adapter
        itemTouchHelper.attachToRecyclerView(recyclerView)

        heroImage = view.findViewById(R.id.heroImage)

        presenter.bindView(this)
        playlistMenuPresenter.bindView(playlistMenuView)

        presenter.updateToolbarMenu()
    }

    override fun onDestroyView() {
        presenter.unbindView()
        playlistMenuPresenter.unbindView()
        itemTouchHelper.attachToRecyclerView(null)

        super.onDestroyView()
    }


    // Private

    private val itemTouchHelper = object : ItemTouchHelper(object : ItemTouchHelperCallback(object : OnItemMoveListener {
        override fun onItemMoved(from: Int, to: Int) {
            presenter.movePlaylistItem(from, to)
        }
    }) {}) {}


    // PlaylistDetailContract.View Implementation

    override fun setPlaylist(playlist: Playlist) {
        toolbar?.title = playlist.name
        val quantityString = Phrase.fromPlural(requireContext(), R.plurals.songsPlural, playlist.songCount)
            .put("count", playlist.songCount)
            .format()
        toolbar?.subtitle = ListPhrase
            .from(" • ")
            .joinSafely(
                listOf(
                    quantityString,
                    playlist.duration.toHms(),
                )
            )
    }

    override fun setData(playlistSongs: List<PlaylistSong>, showDragHandle: Boolean) {
        if (playlistSongs.isNotEmpty()) {
            if (heroImage.drawable == null) {
                imageLoader.loadArtwork(
                    heroImage,
                    playlistSongs.random().song,
                    listOf(
                        ArtworkImageLoader.Options.Placeholder(ResourcesCompat.getDrawable(resources, R.drawable.ic_placeholder_playlist, requireContext().theme)!!),
                        ArtworkImageLoader.Options.Priority.Max
                    ),
                )
            }
        } else {
            heroImage.setImageResource(R.drawable.ic_placeholder_playlist)
        }

        adapter.update(playlistSongs.map { playlistSong ->
            PlaylistSongBinder(
                playlistSong = playlistSong,
                imageLoader = imageLoader,
                listener = songBinderListener,
                showDragHandle = showDragHandle
            )
        })
    }

    override fun updateToolbarMenuSortOrder(sortOrder: PlaylistSongSortOrder) {
        toolbar?.menu?.let { menu ->
            when (sortOrder) {
                PlaylistSongSortOrder.Position -> menu.findItem(R.id.sortCustom)?.isChecked = true
                PlaylistSongSortOrder.SongName -> menu.findItem(R.id.sortSongName)?.isChecked = true
                PlaylistSongSortOrder.ArtistGroupKey -> menu.findItem(R.id.sortArtistName)?.isChecked = true
                PlaylistSongSortOrder.AlbumGroupKey -> menu.findItem(R.id.sortAlbumName)?.isChecked = true
                PlaylistSongSortOrder.Year -> menu.findItem(R.id.sortSongYear)?.isChecked = true
                PlaylistSongSortOrder.Duration -> menu.findItem(R.id.sortSongDuration)?.isChecked = true
                else -> {
                    // Nothing to do
                }
            }
        }
    }

    override fun onAddedToQueue(playlistSong: PlaylistSong) {
        Toast.makeText(context, Phrase.from(requireContext(), R.string.queue_item_added).put("item_name", playlistSong.song.name).format(), Toast.LENGTH_SHORT).show()
    }

    override fun onAddedToQueue(playlist: Playlist) {
        Toast.makeText(context, Phrase.from(requireContext(), R.string.queue_item_added).put("item_name", playlist.name).format(), Toast.LENGTH_SHORT).show()
    }

    override fun showLoadError(error: Error) {
        Toast.makeText(context, error.userDescription(resources), Toast.LENGTH_LONG).show()
    }

    override fun showDeleteError(error: Error) {
        Toast.makeText(requireContext(), error.userDescription(resources), Toast.LENGTH_LONG).show()
    }

    override fun showTagEditor(playlistSongs: List<PlaylistSong>) {
        TagEditorAlertDialog.newInstance(playlistSongs.map { it.song }.distinct()).show(childFragmentManager)
    }

    override fun dismiss() {
        findNavController().popBackStack()
    }


    // SongBinder.Listener Implementation

    private val songBinderListener = object : PlaylistSongBinder.Listener {

        override fun onPlaylistSongClicked(index: Int, playlistSong: PlaylistSong) {
            presenter.onSongClicked(playlistSong, index)
        }

        override fun onPlaylistSongLongClicked(holder: PlaylistSongBinder.ViewHolder, playlistSong: PlaylistSong) {
            val popupMenu = PopupMenu(requireContext(), holder.itemView)
            popupMenu.inflate(R.menu.menu_popup_playlist_song)
            TagEditorMenuSanitiser.sanitise(popupMenu.menu, listOf(playlistSong.song.mediaProvider))

            playlistMenuView.createPlaylistMenu(popupMenu.menu)

            if (playlistSong.song.externalId != null) {
                popupMenu.menu.findItem(R.id.delete)?.isVisible = false
            }

            popupMenu.setOnMenuItemClickListener { menuItem ->
                if (playlistMenuView.handleMenuItem(menuItem, PlaylistData.Songs(playlistSong.song))) {
                    return@setOnMenuItemClickListener true
                } else {
                    when (menuItem.itemId) {
                        R.id.queue -> {
                            presenter.addToQueue(playlistSong)
                            return@setOnMenuItemClickListener true
                        }
                        R.id.playNext -> {
                            presenter.playNext(playlistSong)
                            return@setOnMenuItemClickListener true
                        }
                        R.id.songInfo -> {
                            SongInfoDialogFragment.newInstance(playlistSong.song).show(childFragmentManager)
                            return@setOnMenuItemClickListener true
                        }
                        R.id.exclude -> {
                            ShowExcludeDialog(requireContext(), playlistSong.song.name) {
                                presenter.exclude(playlistSong)
                            }
                            return@setOnMenuItemClickListener true
                        }
                        R.id.editTags -> {
                            presenter.editTags(playlistSong)
                            return@setOnMenuItemClickListener true
                        }
                        R.id.remove -> {
                            presenter.remove(playlistSong)
                            return@setOnMenuItemClickListener true
                        }
                        R.id.delete -> {
                            ShowDeleteDialog(requireContext(), playlistSong.song.name) {
                                presenter.delete(playlistSong)
                            }
                            return@setOnMenuItemClickListener true
                        }
                    }
                }
                false
            }
            popupMenu.show()
        }

        override fun onStartDrag(viewHolder: PlaylistSongBinder.ViewHolder) {
            itemTouchHelper.startDrag(viewHolder)
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