package com.simplecityapps.shuttle.ui.screens.library.playlists.detail

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import au.com.simplecityapps.shuttle.imageloading.glide.GlideImageLoader
import com.simplecityapps.adapter.RecyclerAdapter
import com.simplecityapps.mediaprovider.model.Playlist
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.dagger.Injectable
import com.simplecityapps.shuttle.ui.common.autoCleared
import com.simplecityapps.shuttle.ui.common.error.userDescription
import com.simplecityapps.shuttle.ui.common.recyclerview.clearAdapterOnDetach
import com.simplecityapps.shuttle.ui.common.utils.toHms
import com.simplecityapps.shuttle.ui.screens.library.songs.SongBinder
import com.simplecityapps.shuttle.ui.screens.playlistmenu.CreatePlaylistDialogFragment
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistMenuPresenter
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistMenuView
import com.simplecityapps.shuttle.ui.screens.songinfo.SongInfoDialogFragmentArgs
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject

class PlaylistDetailFragment :
    Fragment(),
    Injectable,
    PlaylistDetailContract.View,
    CreatePlaylistDialogFragment.Listener {

    @Inject lateinit var presenterFactory: PlaylistDetailPresenter.Factory

    @Inject lateinit var playlistMenuPresenter: PlaylistMenuPresenter

    private var imageLoader: ArtworkImageLoader by autoCleared()

    private lateinit var presenter: PlaylistDetailPresenter

    private lateinit var adapter: RecyclerAdapter

    private lateinit var playlist: Playlist

    private lateinit var playlistMenuView: PlaylistMenuView

    private var toolbar: Toolbar? = null

    private var recyclerView: RecyclerView by autoCleared()

    private var heroImage: ImageView by autoCleared()


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

        recyclerView = view.findViewById(R.id.recyclerView)
        toolbar = view.findViewById(R.id.toolbar)

        playlistMenuView = PlaylistMenuView(context!!, playlistMenuPresenter, childFragmentManager)

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
        recyclerView.clearAdapterOnDetach()

        heroImage = view.findViewById(R.id.heroImage)

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

    override fun onAddedToQueue(song: Song) {
        Toast.makeText(context, "${song.name} added to queue", Toast.LENGTH_SHORT).show()
    }

    override fun showLoadError(error: Error) {
        Toast.makeText(context, error.userDescription(), Toast.LENGTH_LONG).show()
    }


    // SongBinder.Listener Implementation

    private val songBinderListener = object : SongBinder.Listener {

        override fun onSongClicked(song: Song) {
            presenter.onSongClicked(song)
        }

        override fun onOverflowClicked(view: View, song: Song) {
            val popupMenu = PopupMenu(context!!, view)
            popupMenu.inflate(R.menu.menu_popup_song)

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
                        R.id.songInfo -> {
                            findNavController().navigate(R.id.action_playlistDetailFragment_to_songInfoDialogFragment, SongInfoDialogFragmentArgs(song).toBundle())
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