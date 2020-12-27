package com.simplecityapps.shuttle.ui.screens.library.albums.detail

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.Toolbar
import androidx.core.os.postDelayed
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.coroutineScope
import androidx.navigation.fragment.NavHostFragment
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.Transition
import androidx.transition.TransitionInflater
import androidx.transition.TransitionListenerAdapter
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import au.com.simplecityapps.shuttle.imageloading.glide.GlideImageLoader
import com.simplecityapps.adapter.RecyclerAdapter
import com.simplecityapps.adapter.ViewBinder
import com.simplecityapps.mediaprovider.model.Album
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.dagger.Injectable
import com.simplecityapps.shuttle.ui.common.autoCleared
import com.simplecityapps.shuttle.ui.common.autoClearedNullable
import com.simplecityapps.shuttle.ui.common.dialog.TagEditorAlertDialog
import com.simplecityapps.shuttle.ui.common.error.userDescription
import com.simplecityapps.shuttle.ui.common.utils.toHms
import com.simplecityapps.shuttle.ui.common.view.DetailImageAnimationHelper
import com.simplecityapps.shuttle.ui.common.viewbinders.DetailSongBinder
import com.simplecityapps.shuttle.ui.common.viewbinders.DiscNumberBinder
import com.simplecityapps.shuttle.ui.screens.playlistmenu.CreatePlaylistDialogFragment
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistMenuPresenter
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistMenuView
import com.simplecityapps.shuttle.ui.screens.songinfo.SongInfoDialogFragment
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject

class AlbumDetailFragment :
    Fragment(),
    Injectable,
    AlbumDetailContract.View,
    CreatePlaylistDialogFragment.Listener {

    @Inject lateinit var presenterFactory: AlbumDetailPresenter.Factory

    @Inject lateinit var playlistMenuPresenter: PlaylistMenuPresenter

    private var imageLoader: ArtworkImageLoader by autoCleared()

    private lateinit var presenter: AlbumDetailPresenter

    private lateinit var adapter: RecyclerAdapter

    private var animationHelper: DetailImageAnimationHelper? by autoClearedNullable()

    private val handler = Handler(Looper.getMainLooper())

    private lateinit var album: Album

    private lateinit var playlistMenuView: PlaylistMenuView

    private var recyclerView: RecyclerView by autoCleared()

    private var toolbar: Toolbar by autoCleared()

    private var dummyImage: ImageView by autoCleared()

    private var heroImage: ImageView by autoCleared()

    private var showHeroView = false
    private var animateTransition: Boolean = true


    // Lifecycle

    override fun onAttach(context: Context) {
        super.onAttach(context)

        AndroidSupportInjection.inject(this)

        album = AlbumDetailFragmentArgs.fromBundle(requireArguments()).album
        animateTransition = AlbumDetailFragmentArgs.fromBundle(requireArguments()).animateTransition
        presenter = presenterFactory.create(album)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        adapter = RecyclerAdapter(lifecycle.coroutineScope)

        sharedElementEnterTransition = TransitionInflater.from(context).inflateTransition(R.transition.image_shared_element_transition)
        (sharedElementEnterTransition as Transition).duration = 200L
        (sharedElementEnterTransition as Transition).addListener(object : TransitionListenerAdapter() {
            override fun onTransitionEnd(transition: Transition) {
                animationHelper?.showHeroView()
                showHeroView = true
                transition.removeListener(this)
            }
        })
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        postponeEnterTransition()
        return inflater.inflate(R.layout.fragment_album_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        playlistMenuView = PlaylistMenuView(requireContext(), playlistMenuPresenter, childFragmentManager)

        imageLoader = GlideImageLoader(this)

        handler.postDelayed(500) {
            startPostponedEnterTransition() // In case our Glide load takes too long
        }

        dummyImage = view.findViewById(R.id.dummyImage)
        dummyImage.transitionName = "album_${album.name}"

        imageLoader.loadArtwork(
            dummyImage, album, ArtworkImageLoader.Options.CircleCrop,
            ArtworkImageLoader.Options.Priority(ArtworkImageLoader.Options.Priority.Priority.Max)
        ) {
            startPostponedEnterTransition()
        }

        heroImage = view.findViewById(R.id.heroImage)
        imageLoader.loadArtwork(
            heroImage,
            album,
            ArtworkImageLoader.Options.Priority(ArtworkImageLoader.Options.Priority.Priority.Max),
            ArtworkImageLoader.Options.Placeholder(R.drawable.ic_placeholder_album)
        )
        if (showHeroView || !animateTransition) {
            heroImage.isVisible = true
            dummyImage.isVisible = false
        }

        toolbar = view.findViewById(R.id.toolbar)
        toolbar.let { toolbar ->
            toolbar.setNavigationOnClickListener {
                NavHostFragment.findNavController(this).popBackStack()
            }
            MenuInflater(context).inflate(R.menu.menu_album_detail, toolbar.menu)
            playlistMenuView.createPlaylistMenu(toolbar.menu)
            toolbar.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.shuffle -> {
                        presenter.shuffle()
                        true
                    }
                    R.id.queue -> {
                        presenter.addToQueue(album)
                        true
                    }
                    R.id.playNext -> {
                        presenter.playNext(album)
                        return@setOnMenuItemClickListener true
                    }
                    R.id.editTags -> {
                        presenter.editTags(album)
                        return@setOnMenuItemClickListener true
                    }
                    else -> {
                        playlistMenuView.handleMenuItem(menuItem, PlaylistData.Albums(album))
                    }
                }
            }
        }

        recyclerView = view.findViewById(R.id.recyclerView)
        recyclerView.adapter = adapter

        animationHelper = DetailImageAnimationHelper(heroImage, dummyImage)

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


    // AlbumDetailContract.View Implementation

    override fun setData(songs: List<Song>) {
        val discSongsMap = songs.groupBy { song -> song.disc }.toSortedMap()
        adapter.update(discSongsMap.flatMap { entry ->
            val viewBinders = mutableListOf<ViewBinder>()
            if (discSongsMap.size > 1) {
                viewBinders.add(DiscNumberBinder(entry.key))
            }
            viewBinders.addAll(entry.value.map { song -> DetailSongBinder(song, songBinderListener) })
            viewBinders
        })
    }

    override fun showLoadError(error: Error) {
        Toast.makeText(context, error.userDescription(), Toast.LENGTH_LONG).show()
    }

    override fun onAddedToQueue(name: String) {
        Toast.makeText(context, "$name added to queue", Toast.LENGTH_SHORT).show()
    }

    override fun setAlbum(album: Album) {
        toolbar.title = album.name
        toolbar.subtitle = "${album.year.yearToString()} • ${resources.getQuantityString(R.plurals.songsPlural, album.songCount, album.songCount)} • ${album.duration.toHms()}"
    }

    override fun showDeleteError(error: Error) {
        Toast.makeText(requireContext(), error.userDescription(), Toast.LENGTH_LONG).show()
    }

    override fun showTagEditor(songs: List<Song>) {
        TagEditorAlertDialog.newInstance(songs).show(childFragmentManager)
    }


    // SongBinder.Listener Implementation

    private val songBinderListener = object : DetailSongBinder.Listener {

        override fun onSongClicked(song: Song) {
            presenter.onSongClicked(song)
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
                            AlertDialog.Builder(requireContext())
                                .setTitle("Exclude Song")
                                .setMessage("\"${song.name}\" will be hidden from your library.\n\nYou can view excluded songs in settings.")
                                .setPositiveButton("Exclude") { _, _ ->
                                    presenter.exclude(song)
                                }
                                .setNegativeButton("Cancel", null)
                                .show()
                            return@setOnMenuItemClickListener true
                        }
                        R.id.editTags -> {
                            presenter.editTags(song)
                            return@setOnMenuItemClickListener true
                        }
                        R.id.delete -> {
                            AlertDialog.Builder(requireContext())
                                .setTitle("Delete Song")
                                .setMessage("\"${song.name}\" will be permanently deleted")
                                .setPositiveButton("Delete") { _, _ ->
                                    presenter.delete(song)
                                }
                                .setNegativeButton("Cancel", null)
                                .show()
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


    // Extensions

    private fun Int.yearToString(): String {
        if (this == 0) return "Year Unknown"
        return this.toString()
    }
}

