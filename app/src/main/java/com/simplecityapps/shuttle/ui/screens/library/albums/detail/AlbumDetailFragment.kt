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
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.Toolbar
import androidx.core.os.postDelayed
import androidx.fragment.app.Fragment
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
import com.simplecityapps.shuttle.ui.common.error.userDescription
import com.simplecityapps.shuttle.ui.common.recyclerview.clearAdapterOnDetach
import com.simplecityapps.shuttle.ui.common.utils.toHms
import com.simplecityapps.shuttle.ui.common.view.DetailImageAnimationHelper
import com.simplecityapps.shuttle.ui.common.viewbinders.DetailSongBinder
import com.simplecityapps.shuttle.ui.common.viewbinders.DiscNumberBinder
import com.simplecityapps.shuttle.ui.screens.playlistmenu.CreatePlaylistDialogFragment
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistMenuPresenter
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistMenuView
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

    private var animationHelper: DetailImageAnimationHelper? = null

    private val handler = Handler(Looper.getMainLooper())

    private lateinit var album: Album

    private lateinit var playlistMenuView: PlaylistMenuView

    private var recyclerView: RecyclerView by autoCleared()

    private var toolbar: Toolbar by autoCleared()

    private var dummyImage: ImageView by autoCleared()

    private var heroImage: ImageView by autoCleared()


    // Lifecycle

    override fun onAttach(context: Context) {
        super.onAttach(context)

        AndroidSupportInjection.inject(this)

        album = AlbumDetailFragmentArgs.fromBundle(arguments!!).album
        presenter = presenterFactory.create(album)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        adapter = RecyclerAdapter()

        sharedElementEnterTransition = TransitionInflater.from(context).inflateTransition(R.transition.image_shared_element_transition)
        (sharedElementEnterTransition as Transition).duration = 200L
        (sharedElementEnterTransition as Transition).addListener(object : TransitionListenerAdapter() {
            override fun onTransitionEnd(transition: Transition) {
                super.onTransitionEnd(transition)
                animationHelper?.showHeroView()
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

        playlistMenuView = PlaylistMenuView(context!!, playlistMenuPresenter, childFragmentManager)

        imageLoader = GlideImageLoader(this)

        handler.postDelayed(500) {
            startPostponedEnterTransition() // In case our Glide load takes too long
        }

        toolbar = view.findViewById(R.id.toolbar)
        toolbar.title = album.name
        toolbar.subtitle = "${album.year.yearToString()} • ${album.songCount} Songs • ${album.duration.toHms()}"

        dummyImage = view.findViewById(R.id.dummyImage)
        dummyImage.transitionName = "album_${album.name}"

        imageLoader.loadArtwork(dummyImage, album, ArtworkImageLoader.Options.CircleCrop, ArtworkImageLoader.Options.Priority(ArtworkImageLoader.Options.Priority.Priority.Max)) {
            startPostponedEnterTransition()
        }

        heroImage = view.findViewById(R.id.heroImage)
        imageLoader.loadArtwork(heroImage, album, ArtworkImageLoader.Options.Priority(ArtworkImageLoader.Options.Priority.Priority.Max))

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
                    else -> {
                        playlistMenuView.handleMenuItem(menuItem, PlaylistData.Albums(album))
                    }
                }
            }
        }

        recyclerView = view.findViewById(R.id.recyclerView)
        recyclerView.adapter = adapter
        recyclerView.clearAdapterOnDetach()

        animationHelper = DetailImageAnimationHelper(heroImage, dummyImage)

        presenter.bindView(this)
        playlistMenuPresenter.bindView(playlistMenuView)
    }

    override fun onResume() {
        super.onResume()

        presenter.loadData()
    }

    override fun onDestroyView() {
        adapter.dispose()

        presenter.unbindView()
        playlistMenuPresenter.unbindView()

        animationHelper = null

        super.onDestroyView()
    }


    // AlbumDetailContract.View Implementation

    override fun setData(songs: List<Song>) {
        val discSongsMap = songs.groupBy { song -> song.disc }.toSortedMap()
        adapter.setData(discSongsMap.flatMap { entry ->
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


    // SongBinder.Listener Implementation

    private val songBinderListener = object : DetailSongBinder.Listener {

        override fun onSongClicked(song: Song) {
            presenter.onSongClicked(song)
        }

        override fun onOverflowClicked(view: View, song: Song) {
            val popupMenu = PopupMenu(context!!, view)
            popupMenu.inflate(R.menu.menu_popup_add)

            playlistMenuView.createPlaylistMenu(popupMenu.menu)

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

