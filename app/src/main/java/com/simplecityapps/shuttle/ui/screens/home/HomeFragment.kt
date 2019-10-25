package com.simplecityapps.shuttle.ui.screens.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.RecyclerView
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import au.com.simplecityapps.shuttle.imageloading.glide.GlideImageLoader
import com.simplecityapps.adapter.RecyclerAdapter
import com.simplecityapps.adapter.ViewBinder
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.mediaprovider.repository.PlaylistQuery
import com.simplecityapps.mediaprovider.repository.PlaylistRepository
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.dagger.Injectable
import com.simplecityapps.shuttle.ui.common.error.userDescription
import com.simplecityapps.shuttle.ui.screens.library.playlists.detail.PlaylistDetailFragmentArgs
import com.simplecityapps.shuttle.ui.screens.library.playlists.smart.SmartPlaylist
import com.simplecityapps.shuttle.ui.screens.library.playlists.smart.SmartPlaylistDetailFragmentArgs
import com.simplecityapps.shuttle.ui.screens.playlistmenu.CreatePlaylistDialogFragment
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistMenuPresenter
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistMenuView
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.fragment_home.*
import timber.log.Timber
import javax.inject.Inject

class HomeFragment :
    Fragment(),
    Injectable,
    HomeContract.View,
    CreatePlaylistDialogFragment.Listener {

    @Inject lateinit var presenter: HomePresenter

    @Inject lateinit var playlistMenuPresenter: PlaylistMenuPresenter

    @Inject lateinit var playlistRepository: PlaylistRepository

    private lateinit var recyclerView: RecyclerView

    private val adapter = RecyclerAdapter()

    private lateinit var imageLoader: ArtworkImageLoader

    private var viewBinders = mutableSetOf<ViewBinder>()

    private val disposable: CompositeDisposable = CompositeDisposable()

    private lateinit var playlistMenuView: PlaylistMenuView


    // Lifecycle

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        playlistMenuView = PlaylistMenuView(context!!, playlistMenuPresenter, childFragmentManager)

        historyButton.setOnClickListener { findNavController().navigate(R.id.action_homeFragment_to_historyFragment) }
        latestButton.setOnClickListener { findNavController().navigate(R.id.action_homeFragment_to_recentFragment) }
        favoritesButton.setOnClickListener { navigateToPlaylist(PlaylistQuery.PlaylistName("Favorites")) }
        shuffleButton.setOnClickListener { presenter.shuffleAll() }

        recyclerView = view.findViewById(R.id.recyclerView)
        recyclerView.adapter = adapter

        val decoration = DividerItemDecoration(context, LinearLayout.VERTICAL)
        decoration.setDrawable(resources.getDrawable(R.drawable.divider))
        recyclerView.addItemDecoration(decoration)

        imageLoader = GlideImageLoader(this)

        presenter.bindView(this)
        playlistMenuPresenter.bindView(playlistMenuView)
    }

    override fun onResume() {
        super.onResume()

        searchView.setOnSearchClickListener {
            if (isResumed)
                navigateToSearch()
        }
        searchView.setOnQueryTextFocusChangeListener { v, hasFocus ->
            if (hasFocus && isResumed) {
                navigateToSearch()
            }
        }

        presenter.loadMostPlayed()
        presenter.loadRecentlyPlayed()
        playlistMenuPresenter.bindView(playlistMenuView)
    }

    override fun onPause() {
        searchView.setOnSearchClickListener(null)
        searchView.setOnQueryTextFocusChangeListener(null)
        super.onPause()
    }

    override fun onDestroyView() {
        presenter.unbindView()
        playlistMenuPresenter.unbindView()
        disposable.clear()
        super.onDestroyView()
    }


    // HomeContract.View Implementation

    override fun showLoadError(error: Error) {
        Toast.makeText(context, error.userDescription(), Toast.LENGTH_LONG).show()
    }

    override fun setMostPlayed(songs: List<Song>) {
        viewBinders.add(MostPlayedSectionBinder(songs, imageLoader, sectionBinderListener))
        adapter.setData(viewBinders.toList())
    }

    override fun setRecentlyPlayed(songs: List<Song>) {
        viewBinders.add(RecentlyPlayedSectionBinder(songs, imageLoader, sectionBinderListener))
        adapter.setData(viewBinders.toList())
    }

    override fun onAddedToQueue(song: Song) {
        Toast.makeText(context, "${song.name} added to queue", Toast.LENGTH_SHORT).show()
    }


    // MostPlayedSectionBinder.Listener Implementation

    private val sectionBinderListener = object : SectionBinderListener {
        override fun onSongClicked(song: Song, songs: List<Song>) {
            presenter.play(song, songs)
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
                    }
                }
                false
            }
            popupMenu.show()
        }

        override fun onHeaderClicked(playlist: SmartPlaylist) {
            findNavController().navigate(R.id.action_homeFragment_to_smartPlaylistDetailFragment, SmartPlaylistDetailFragmentArgs(playlist).toBundle())
        }
    }


    // Private

    private fun navigateToPlaylist(query: PlaylistQuery) {
        disposable.add(playlistRepository
            .getPlaylists(query)
            .first(emptyList())
            .map { playlists -> playlists.first() }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeBy(
                onSuccess = { playlist -> findNavController().navigate(R.id.action_homeFragment_to_playlistDetailFragment, PlaylistDetailFragmentArgs(playlist).toBundle()) },
                onError = { throwable -> Timber.e(throwable, "Failed to retrieve favorites playlist") }
            ))
    }

    private fun navigateToSearch() {
        if (findNavController().currentDestination?.id != R.id.searchFragment) {
            findNavController().navigate(R.id.action_homeFragment_to_searchFragment, null, null, FragmentNavigatorExtras(searchView to searchView.transitionName))
        }
    }


    // CreatePlaylistDialogFragment.Listener Implementation

    override fun onSave(text: String, playlistData: PlaylistData) {
        playlistMenuPresenter.createPlaylist(text, playlistData)
    }
}