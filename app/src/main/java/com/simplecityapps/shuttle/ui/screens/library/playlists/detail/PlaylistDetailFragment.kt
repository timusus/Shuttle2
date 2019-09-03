package com.simplecityapps.shuttle.ui.screens.library.playlists.detail

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.NavHostFragment
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import au.com.simplecityapps.shuttle.imageloading.glide.GlideImageLoader
import com.simplecityapps.adapter.RecyclerAdapter
import com.simplecityapps.mediaprovider.model.Playlist
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.dagger.Injectable
import com.simplecityapps.shuttle.ui.common.error.userDescription
import com.simplecityapps.shuttle.ui.common.utils.toHms
import com.simplecityapps.shuttle.ui.screens.library.songs.SongBinder
import dagger.android.support.AndroidSupportInjection
import kotlinx.android.synthetic.main.fragment_album_detail.*
import javax.inject.Inject

class PlaylistDetailFragment :
    Fragment(),
    Injectable,
    PlaylistDetailContract.View {

    @Inject lateinit var presenterFactory: PlaylistDetailPresenter.Factory

    private lateinit var imageLoader: ArtworkImageLoader

    private lateinit var presenter: PlaylistDetailPresenter

    private lateinit var adapter: RecyclerAdapter

    private lateinit var playlist: Playlist


    // Lifecycle

    override fun onAttach(context: Context) {
        super.onAttach(context)

        AndroidSupportInjection.inject(this)

        playlist = PlaylistDetailFragmentArgs.fromBundle(arguments!!).playlist
        presenter = presenterFactory.create(playlist)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_playlist_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = RecyclerAdapter()

        imageLoader = GlideImageLoader(this)

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
                    else -> {
                        false
                    }
                }
            }
        }

        toolbar?.title = playlist.name
        toolbar?.subtitle = "${playlist.songCount} Songs • ${playlist.duration.toHms()}"

        recyclerView.adapter = adapter

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


    // PlaylistDetailContract.View Implementation

    override fun setData(songs: List<Song>) {
        if (songs.isNotEmpty()) {
            if (heroImage.drawable == null) {
                imageLoader.loadArtwork(
                    heroImage,
                    songs.random(),
                    ArtworkImageLoader.Options.Priority(ArtworkImageLoader.Options.Priority.Priority.Max),
                    ArtworkImageLoader.Options.Crossfade(600)
                )
            }
        } else {
            heroImage.setImageResource(R.drawable.ic_music_note_black_24dp)
        }

        adapter.setData(songs.map { song ->
            SongBinder(song, imageLoader, songBinderListener)
        })
    }


    // SongBinder.Listener Implementation

    private val songBinderListener = object : SongBinder.Listener {

        override fun onSongClicked(song: Song) {
            presenter.onSongClicked(song)
        }
    }

    override fun showLoadError(error: Error) {
        Toast.makeText(context, error.userDescription(), Toast.LENGTH_LONG).show()
    }
}