package com.simplecityapps.shuttle.ui.screens.library.albums.detail

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.postDelayed
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.NavHostFragment
import androidx.transition.Transition
import androidx.transition.TransitionInflater
import androidx.transition.TransitionListenerAdapter
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import com.simplecityapps.adapter.RecyclerAdapter
import com.simplecityapps.adapter.ViewBinder
import com.simplecityapps.mediaprovider.model.Album
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.dagger.Injectable
import com.simplecityapps.shuttle.ui.common.error.userDescription
import com.simplecityapps.shuttle.ui.common.toHms
import com.simplecityapps.shuttle.ui.common.view.DetailImageAnimationHelper
import com.simplecityapps.shuttle.ui.common.viewbinders.DetailSongBinder
import com.simplecityapps.shuttle.ui.common.viewbinders.DiscNumberBinder
import dagger.android.support.AndroidSupportInjection
import kotlinx.android.synthetic.main.fragment_album_detail.*
import javax.inject.Inject

class AlbumDetailFragment :
    Fragment(),
    Injectable,
    AlbumDetailContract.View {

    @Inject lateinit var presenterFactory: AlbumDetailPresenter.Factory

    @Inject lateinit var imageLoader: ArtworkImageLoader

    private lateinit var presenter: AlbumDetailPresenter

    private val adapter = RecyclerAdapter()

    private lateinit var animationHelper: DetailImageAnimationHelper

    private val handler = Handler(Looper.getMainLooper())


    // Lifecycle

    override fun onAttach(context: Context) {
        super.onAttach(context)

        AndroidSupportInjection.inject(this)

        presenter = presenterFactory.create(AlbumDetailFragmentArgs.fromBundle(arguments!!).albumId)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedElementEnterTransition = TransitionInflater.from(context).inflateTransition(R.transition.image_shared_element_transition)
        (sharedElementEnterTransition as Transition).duration = 200L
        (sharedElementEnterTransition as Transition).addListener(object : TransitionListenerAdapter() {
            override fun onTransitionEnd(transition: Transition) {
                super.onTransitionEnd(transition)
                animationHelper.showHeroView()
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

        handler.postDelayed(1000) {
            startPostponedEnterTransition() // In case our Glide load takes too long
        }

        toolbar?.let { toolbar ->
            toolbar.setNavigationOnClickListener {
                NavHostFragment.findNavController(this).popBackStack()
            }
            MenuInflater(context).inflate(R.menu.menu_album_detail, toolbar.menu)
            toolbar.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.shuffle -> {
                        presenter.shuffle()
                        true
                    }
                    else -> {
                        false
                    }
                }
            }
        }

        recyclerView.adapter = adapter

        animationHelper = DetailImageAnimationHelper(heroImage, dummyImage)

        presenter.bindView(this)
    }

    override fun onResume() {
        super.onResume()

        presenter.loadData()
    }

    override fun onDestroyView() {
        adapter.dispose()

        presenter.unbindView()

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

    override fun setCurrentAlbum(album: Album) {
        toolbar?.title = album.name
        toolbar?.subtitle = "${album.year.yearToString()} • ${album.songCount} Songs • ${album.duration.toHms()}"

        dummyImage.transitionName = "album_${album.name}"

        imageLoader.loadArtwork(dummyImage, album, ArtworkImageLoader.Options.CircleCrop, ArtworkImageLoader.Options.Priority(ArtworkImageLoader.Options.Priority.Priority.Max)) {
            startPostponedEnterTransition()
        }

        imageLoader.loadArtwork(heroImage, album, ArtworkImageLoader.Options.Priority(ArtworkImageLoader.Options.Priority.Priority.Max), completionHandler = null)
    }

    override fun showLoadError(error: Error) {
        Toast.makeText(context, error.userDescription(), Toast.LENGTH_LONG).show()
    }


    // SongBinder.Listener Implementation

    private val songBinderListener = object : DetailSongBinder.Listener {

        override fun onSongClicked(song: Song) {
            presenter.onSongClicked(song)
        }
    }


    // Extensions

    private fun Int.yearToString(): String {
        if (this == 0) return "Year Unknown"
        return this.toString()
    }
}

