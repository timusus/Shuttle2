package com.simplecityapps.shuttle.ui.screens.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.SearchView
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
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.mediaprovider.repository.PlaylistQuery
import com.simplecityapps.mediaprovider.repository.PlaylistRepository
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.dagger.Injectable
import com.simplecityapps.shuttle.ui.common.autoCleared
import com.simplecityapps.shuttle.ui.common.error.userDescription
import com.simplecityapps.shuttle.ui.common.recyclerview.clearAdapterOnDetach
import com.simplecityapps.shuttle.ui.common.view.HomeButton
import com.simplecityapps.shuttle.ui.screens.library.playlists.detail.PlaylistDetailFragmentArgs
import com.simplecityapps.shuttle.ui.screens.library.playlists.smart.SmartPlaylist
import com.simplecityapps.shuttle.ui.screens.library.playlists.smart.SmartPlaylistDetailFragmentArgs
import com.simplecityapps.shuttle.ui.screens.playlistmenu.CreatePlaylistDialogFragment
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistMenuPresenter
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistMenuView
import com.simplecityapps.shuttle.ui.screens.songinfo.SongInfoDialogFragment
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
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

    private var recyclerView: RecyclerView by autoCleared()

    private lateinit var adapter: RecyclerAdapter

    private var imageLoader: ArtworkImageLoader by autoCleared()

    private var viewBinders = mutableSetOf<ViewBinder>()

    private val disposable: CompositeDisposable = CompositeDisposable()

    private lateinit var playlistMenuView: PlaylistMenuView

    private var searchView: SearchView by autoCleared()


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

        searchView = view.findViewById(R.id.searchView)

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

        presenter.bindView(this)
        playlistMenuPresenter.bindView(playlistMenuView)
    }

    override fun onResume() {
        super.onResume()

        searchView.setOnSearchClickListener {
            if (isResumed)
                navigateToSearch()
        }
        searchView.setOnQueryTextFocusChangeListener { _, hasFocus ->
            if (hasFocus && isResumed) {
                navigateToSearch()
            }
        }

        presenter.loadMostPlayed()
        presenter.loadRecentlyPlayed()
    }

    override fun onPause() {
        searchView.setOnSearchClickListener(null)
        searchView.setOnQueryTextFocusChangeListener(null)
        super.onPause()
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
            val popupMenu = PopupMenu(requireContext(), view)
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
                            SongInfoDialogFragment.newInstance(song).show(childFragmentManager)
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
                onSuccess = { playlist ->
                    if (findNavController().currentDestination?.id != R.id.playlistDetailFragment) {
                        findNavController().navigate(R.id.action_homeFragment_to_playlistDetailFragment, PlaylistDetailFragmentArgs(playlist).toBundle())
                    }
                },
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