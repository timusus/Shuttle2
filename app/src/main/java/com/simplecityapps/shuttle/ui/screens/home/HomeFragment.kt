package com.simplecityapps.shuttle.ui.screens.home

import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.RecyclerView
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import au.com.simplecityapps.shuttle.imageloading.glide.GlideImageLoader
import com.simplecityapps.adapter.RecyclerAdapter
import com.simplecityapps.adapter.RecyclerListener
import com.simplecityapps.adapter.ViewBinder
import com.simplecityapps.mediaprovider.model.Album
import com.simplecityapps.mediaprovider.model.AlbumArtist
import com.simplecityapps.mediaprovider.repository.PlaylistQuery
import com.simplecityapps.mediaprovider.repository.PlaylistRepository
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.dagger.Injectable
import com.simplecityapps.shuttle.ui.common.autoCleared
import com.simplecityapps.shuttle.ui.common.error.userDescription
import com.simplecityapps.shuttle.ui.common.recyclerview.clearAdapterOnDetach
import com.simplecityapps.shuttle.ui.common.view.HomeButton
import com.simplecityapps.shuttle.ui.screens.library.albumartists.detail.AlbumArtistDetailFragmentArgs
import com.simplecityapps.shuttle.ui.screens.library.albums.detail.AlbumDetailFragmentArgs
import com.simplecityapps.shuttle.ui.screens.library.playlists.detail.PlaylistDetailFragmentArgs
import com.simplecityapps.shuttle.ui.screens.library.playlists.smart.SmartPlaylist
import com.simplecityapps.shuttle.ui.screens.library.playlists.smart.SmartPlaylistDetailFragmentArgs
import com.simplecityapps.shuttle.ui.screens.library.songs.GridAlbumArtistBinder
import com.simplecityapps.shuttle.ui.screens.library.songs.GridAlbumBinder
import com.simplecityapps.shuttle.ui.screens.playlistmenu.CreatePlaylistDialogFragment
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistMenuPresenter
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistMenuView
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import java.util.*
import javax.inject.Inject

class HomeFragment :
    Fragment(),
    Injectable,
    HomeContract.View,
    CreatePlaylistDialogFragment.Listener {

    @Inject lateinit var presenter: HomePresenter

    @Inject lateinit var playlistMenuPresenter: PlaylistMenuPresenter

    @Inject lateinit var playlistRepository: PlaylistRepository

    private var recyclerView: RecyclerView by autoCleared()

    private lateinit var adapter: RecyclerAdapter

    private var imageLoader: ArtworkImageLoader by autoCleared()

    private val disposable: CompositeDisposable = CompositeDisposable()

    private lateinit var playlistMenuView: PlaylistMenuView

    private var recyclerViewState: Parcelable? = null


    // Lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        adapter = RecyclerAdapter()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val historyButton: HomeButton = view.findViewById(R.id.historyButton)
        val latestButton: HomeButton = view.findViewById(R.id.latestButton)
        val favoritesButton: HomeButton = view.findViewById(R.id.favoritesButton)
        val shuffleButton: HomeButton = view.findViewById(R.id.shuffleButton)

        playlistMenuView = PlaylistMenuView(requireContext(), playlistMenuPresenter, childFragmentManager)

        historyButton.setOnClickListener {
            val navController = findNavController()
            if (navController.currentDestination?.id != R.id.smartPlaylistDetailFragment) {
                navController.navigate(
                    R.id.action_homeFragment_to_smartPlaylistDetailFragment,
                    SmartPlaylistDetailFragmentArgs(SmartPlaylist.RecentlyPlayed).toBundle()
                )
            }
        }
        latestButton.setOnClickListener {
            val navController = findNavController()
            if (navController.currentDestination?.id != R.id.smartPlaylistDetailFragment) {
                navController.navigate(
                    R.id.action_homeFragment_to_smartPlaylistDetailFragment,
                    SmartPlaylistDetailFragmentArgs(SmartPlaylist.RecentlyAdded).toBundle()
                )
            }
        }
        favoritesButton.setOnClickListener { navigateToPlaylist(PlaylistQuery.PlaylistName("Favorites")) }
        shuffleButton.setOnClickListener { presenter.shuffleAll() }

        recyclerView = view.findViewById(R.id.recyclerView)
        recyclerView.adapter = adapter
        recyclerView.setRecyclerListener(RecyclerListener())
        recyclerView.clearAdapterOnDetach()

        val decoration = DividerItemDecoration(context, LinearLayout.VERTICAL)
        decoration.setDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.divider)!!)
        recyclerView.addItemDecoration(decoration)

        imageLoader = GlideImageLoader(this)

        savedInstanceState?.getParcelable<Parcelable>(ARG_RECYCLER_STATE)?.let { recyclerViewState = it }

        presenter.bindView(this)
        playlistMenuPresenter.bindView(playlistMenuView)
    }

    override fun onResume() {
        super.onResume()

        recyclerViewState?.let {
            recyclerView.layoutManager?.onRestoreInstanceState(recyclerViewState)
        }

        presenter.loadData()
    }

    override fun onPause() {
        super.onPause()
        recyclerViewState = recyclerView.layoutManager?.onSaveInstanceState()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putParcelable(ARG_RECYCLER_STATE, recyclerViewState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        presenter.unbindView()
        playlistMenuPresenter.unbindView()
        adapter.dispose()
        disposable.clear()
        super.onDestroyView()
    }


    // HomeContract.View Implementation

    override fun showLoadError(error: Error) {
        Toast.makeText(context, error.userDescription(), Toast.LENGTH_LONG).show()
    }

    override fun setData(data: HomeContract.HomeData) {
        val viewBinders = mutableListOf<ViewBinder>()
        if (data.recentlyPlayedAlbums.isNotEmpty()) {
            viewBinders.add(
                HorizontalAlbumListBinder("Recent", "Recently played albums", data.recentlyPlayedAlbums, imageLoader, listener = albumBinderListener)
            )
        }
        if (data.mostPlayedAlbums.isNotEmpty()) {
            viewBinders.add(
                HorizontalAlbumListBinder("Most Played", "Albums in heavy rotation", data.mostPlayedAlbums, imageLoader, showPlayCountBadge = true, listener = albumBinderListener)
            )
        }
        if (data.albumsFromThisYear.isNotEmpty()) {
            viewBinders.add(
                HorizontalAlbumListBinder("This Year", "Albums released in ${Calendar.getInstance().get(Calendar.YEAR)}", data.albumsFromThisYear, imageLoader, listener = albumBinderListener)
            )
        }
        if (data.unplayedAlbumArtists.isNotEmpty()) {
            viewBinders.add(
                HorizontalAlbumArtistListBinder("Something Different", "Artists you haven't listened to in a while", data.unplayedAlbumArtists, imageLoader, listener = albumArtistBinderListener)
            )
        }
        adapter.setData(viewBinders.toList(), completion = {
            recyclerViewState?.let {
                recyclerView.layoutManager?.onRestoreInstanceState(recyclerViewState)
            }
        })
    }

    override fun showDeleteError(error: Error) {
        Toast.makeText(requireContext(), error.userDescription(), Toast.LENGTH_LONG).show()
    }

    override fun onAddedToQueue(albumArtist: AlbumArtist) {
        Toast.makeText(context, "${albumArtist.name} added to queue", Toast.LENGTH_SHORT).show()
    }

    override fun onAddedToQueue(album: Album) {
        Toast.makeText(context, "${album.name} added to queue", Toast.LENGTH_SHORT).show()
    }


    // Private

    private val albumArtistBinderListener = object : GridAlbumArtistBinder.Listener {

        override fun onAlbumArtistClicked(albumArtist: AlbumArtist, viewHolder: GridAlbumArtistBinder.ViewHolder) {
            findNavController().navigate(
                R.id.action_homeFragment_to_albumArtistDetailFragment,
                AlbumArtistDetailFragmentArgs(albumArtist, true).toBundle(),
                null,
                FragmentNavigatorExtras(viewHolder.imageView to viewHolder.imageView.transitionName)
            )
        }

        override fun onAlbumArtistLongPressed(view: View, albumArtist: AlbumArtist) {
            val popupMenu = PopupMenu(requireContext(), view)
            popupMenu.inflate(R.menu.menu_popup_add)

            playlistMenuView.createPlaylistMenu(popupMenu.menu)

            popupMenu.setOnMenuItemClickListener { menuItem ->
                if (playlistMenuView.handleMenuItem(menuItem, PlaylistData.AlbumArtists(albumArtist))) {
                    return@setOnMenuItemClickListener true
                } else {
                    when (menuItem.itemId) {
                        R.id.play -> {
                            presenter.play(albumArtist)
                            return@setOnMenuItemClickListener true
                        }
                        R.id.queue -> {
                            presenter.addToQueue(albumArtist)
                            return@setOnMenuItemClickListener true
                        }
                        R.id.playNext -> {
                            presenter.playNext(albumArtist)
                            return@setOnMenuItemClickListener true
                        }
                        R.id.blacklist -> {
                            presenter.blacklist(albumArtist)
                            return@setOnMenuItemClickListener true
                        }
                    }
                }
                false
            }
            popupMenu.show()
        }
    }

    private val albumBinderListener = object : GridAlbumBinder.Listener {

        override fun onAlbumClicked(album: Album, viewHolder: GridAlbumBinder.ViewHolder) {
            findNavController().navigate(
                R.id.action_homeFragment_to_albumDetailFragment,
                AlbumDetailFragmentArgs(album, true).toBundle(),
                null,
                FragmentNavigatorExtras(viewHolder.imageView to viewHolder.imageView.transitionName)
            )
        }

        override fun onAlbumLongPressed(view: View, album: Album) {
            val popupMenu = PopupMenu(requireContext(), view)
            popupMenu.inflate(R.menu.menu_popup_add)

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
                        R.id.blacklist -> {
                            presenter.blacklist(album)
                            return@setOnMenuItemClickListener true
                        }
                    }
                }
                false
            }
            popupMenu.show()
        }
    }

    private fun navigateToPlaylist(query: PlaylistQuery) {
        disposable.add(playlistRepository
            .getPlaylists(query)
            .first(emptyList())
            .map { playlists -> playlists.first() }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeBy(
                onSuccess = { playlist ->
                    if (findNavController().currentDestination?.id != R.id.playlistDetailFragment) {
                        findNavController().navigate(R.id.action_homeFragment_to_playlistDetailFragment, PlaylistDetailFragmentArgs(playlist).toBundle())
                    }
                },
                onError = { throwable -> Timber.e(throwable, "Failed to retrieve favorites playlist") }
            ))
    }


    // CreatePlaylistDialogFragment.Listener Implementation

    override fun onSave(text: String, playlistData: PlaylistData) {
        playlistMenuPresenter.createPlaylist(text, playlistData)
    }

    companion object {
        const val ARG_RECYCLER_STATE = "recycler_state"
    }
}