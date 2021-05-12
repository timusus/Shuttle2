package com.simplecityapps.shuttle.ui.screens.library.genres.detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import com.simplecityapps.adapter.RecyclerAdapter
import com.simplecityapps.adapter.ViewBinder
import com.simplecityapps.mediaprovider.model.Album
import com.simplecityapps.mediaprovider.model.Genre
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.TagEditorMenuSanitiser
import com.simplecityapps.shuttle.ui.common.autoCleared
import com.simplecityapps.shuttle.ui.common.dialog.ShowDeleteDialog
import com.simplecityapps.shuttle.ui.common.dialog.ShowExcludeDialog
import com.simplecityapps.shuttle.ui.common.dialog.TagEditorAlertDialog
import com.simplecityapps.shuttle.ui.common.error.userDescription
import com.simplecityapps.shuttle.ui.screens.home.HorizontalAlbumListBinder
import com.simplecityapps.shuttle.ui.screens.home.search.HeaderBinder
import com.simplecityapps.shuttle.ui.screens.library.albums.AlbumBinder
import com.simplecityapps.shuttle.ui.screens.library.albums.detail.AlbumDetailFragmentArgs
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
class GenreDetailFragment :
    Fragment(),
    GenreDetailContract.View,
    CreatePlaylistDialogFragment.Listener {

    @Inject
    lateinit var presenterFactory: GenreDetailPresenter.Factory

    @Inject
    lateinit var playlistMenuPresenter: PlaylistMenuPresenter

    @Inject
    lateinit var imageLoader: ArtworkImageLoader

    private var adapter: RecyclerAdapter by autoCleared()

    private lateinit var presenter: GenreDetailPresenter

    private lateinit var genre: Genre

    private lateinit var playlistMenuView: PlaylistMenuView

    private var recyclerView: RecyclerView by autoCleared()

    private var toolbar: Toolbar by autoCleared()


    // Lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        genre = GenreDetailFragmentArgs.fromBundle(requireArguments()).genre
        presenter = presenterFactory.create(genre)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_genre_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = RecyclerAdapter(viewLifecycleOwner.lifecycleScope)

        playlistMenuView = PlaylistMenuView(requireContext(), playlistMenuPresenter, childFragmentManager)

        toolbar = view.findViewById(R.id.toolbar)
        toolbar.let { toolbar ->
            toolbar.setNavigationOnClickListener {
                NavHostFragment.findNavController(this).popBackStack()
            }
            MenuInflater(context).inflate(R.menu.menu_album_detail, toolbar.menu)
            TagEditorMenuSanitiser.sanitise(toolbar.menu, genre.mediaProviders)
            playlistMenuView.createPlaylistMenu(toolbar.menu)
            toolbar.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.shuffle -> {
                        presenter.shuffle()
                        true
                    }
                    R.id.queue -> {
                        presenter.addToQueue(genre)
                        true
                    }
                    R.id.playNext -> {
                        presenter.playNext(genre)
                        return@setOnMenuItemClickListener true
                    }
                    R.id.editTags -> {
                        presenter.editTags(genre)
                        return@setOnMenuItemClickListener true
                    }
                    else -> {
                        playlistMenuView.handleMenuItem(menuItem, PlaylistData.Genres(genre))
                    }
                }
            }
        }

        recyclerView = view.findViewById(R.id.recyclerView)
        recyclerView.adapter = adapter

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


    // GenreDetailContract.View Implementation

    override fun setData(albums: List<Album>, songs: List<Song>) {
        val viewBinders = mutableListOf<ViewBinder>()
        viewBinders.add(HeaderBinder(getString(R.string.albums)))
        viewBinders.add(HorizontalAlbumListBinder(albums, imageLoader, false, viewLifecycleOwner.lifecycleScope, albumBinderListener))
        viewBinders.add(HeaderBinder(getString(R.string.songs)))
        viewBinders.addAll(songs.map { song -> SongBinder(song, imageLoader, songBinderListener) })
        adapter.update(viewBinders)
    }

    override fun showLoadError(error: Error) {
        Toast.makeText(context, error.userDescription(resources), Toast.LENGTH_LONG).show()
    }

    override fun onAddedToQueue(album: Album) {
        Toast.makeText(context, Phrase.from(requireContext(), R.string.queue_item_added).put("itemName", album.name).format(), Toast.LENGTH_SHORT).show()
    }

    override fun onAddedToQueue(name: String) {
        Toast.makeText(context, Phrase.from(requireContext(), R.string.queue_item_added).put("itemName", name).format(), Toast.LENGTH_SHORT).show()
    }

    override fun setGenre(genre: Genre) {
        toolbar.title = genre.name
        toolbar.subtitle = Phrase.fromPlural(resources, R.plurals.songsPlural, genre.songCount)
            .put("count", genre.songCount)
            .format()
    }

    override fun showDeleteError(error: Error) {
        Toast.makeText(requireContext(), error.userDescription(resources), Toast.LENGTH_LONG).show()
    }

    override fun showTagEditor(songs: List<Song>) {
        TagEditorAlertDialog.newInstance(songs).show(childFragmentManager)
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
            popupMenu.inflate(R.menu.menu_popup_song)

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

    private val albumBinderListener = object : AlbumBinder.Listener {

        override fun onAlbumClicked(album: Album, viewHolder: AlbumBinder.ViewHolder) {
            findNavController().navigate(
                R.id.action_genreDetailFragment_to_albumDetailFragment,
                AlbumDetailFragmentArgs(album, true).toBundle(),
                null,
                FragmentNavigatorExtras(viewHolder.imageView to viewHolder.imageView.transitionName)
            )
        }

        override fun onAlbumLongClicked(album: Album, viewHolder: AlbumBinder.ViewHolder) {

        }

        override fun onOverflowClicked(view: View, album: Album) {
            val popupMenu = PopupMenu(requireContext(), view)
            popupMenu.inflate(R.menu.menu_popup)

            playlistMenuView.createPlaylistMenu(popupMenu.menu)

            popupMenu.setOnMenuItemClickListener { menuItem ->
                if (playlistMenuView.handleMenuItem(menuItem, PlaylistData.Albums(album))) {
                    return@setOnMenuItemClickListener true
                } else {
                    when (menuItem.itemId) {
                        R.id.play -> {
                            presenter.play(album)
                            return@setOnMenuItemClickListener true
                        }
                        R.id.queue -> {
                            presenter.addToQueue(album)
                            return@setOnMenuItemClickListener true
                        }
                        R.id.playNext -> {
                            presenter.playNext(album)
                            return@setOnMenuItemClickListener true
                        }
                        R.id.exclude -> {
                            presenter.exclude(album)
                            return@setOnMenuItemClickListener true
                        }
                        R.id.editTags -> {
                            presenter.editTags(album)
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
}

