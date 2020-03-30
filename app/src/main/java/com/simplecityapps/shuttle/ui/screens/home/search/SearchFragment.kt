package com.simplecityapps.shuttle.ui.screens.home.search

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.Transition
import androidx.transition.TransitionInflater
import au.com.simplecityapps.shuttle.imageloading.glide.GlideImageLoader
import com.simplecityapps.adapter.RecyclerAdapter
import com.simplecityapps.adapter.ViewBinder
import com.simplecityapps.mediaprovider.model.Album
import com.simplecityapps.mediaprovider.model.AlbumArtist
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.dagger.Injectable
import com.simplecityapps.shuttle.ui.common.autoCleared
import com.simplecityapps.shuttle.ui.common.error.userDescription
import com.simplecityapps.shuttle.ui.common.recyclerview.clearAdapterOnDetach
import com.simplecityapps.shuttle.ui.screens.library.albumartists.AlbumArtistBinder
import com.simplecityapps.shuttle.ui.screens.library.albums.AlbumBinder
import com.simplecityapps.shuttle.ui.screens.library.songs.SongBinder
import com.simplecityapps.shuttle.ui.screens.playlistmenu.CreatePlaylistDialogFragment
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistMenuPresenter
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistMenuView
import com.simplecityapps.shuttle.ui.screens.songinfo.SongInfoDialogFragment
import io.reactivex.Completable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class SearchFragment : Fragment(),
    Injectable,
    SearchContract.View,
    CreatePlaylistDialogFragment.Listener {

    private lateinit var adapter: RecyclerAdapter

    private var searchView: SearchView by autoCleared()

    private var recyclerView: RecyclerView by autoCleared()

    private var toolbar: Toolbar by autoCleared()

    @Inject lateinit var presenter: SearchPresenter

    @Inject lateinit var playlistMenuPresenter: PlaylistMenuPresenter

    private var compositeDisposable = CompositeDisposable()

    private var imageLoader: GlideImageLoader by autoCleared()

    private val queryPublishSubject = PublishSubject.create<String>()

    private lateinit var playlistMenuView: PlaylistMenuView


    // Lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        adapter = RecyclerAdapter()

        sharedElementEnterTransition = TransitionInflater.from(context).inflateTransition(android.R.transition.move)
        (sharedElementEnterTransition as Transition).duration = 200L
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_search, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        playlistMenuView = PlaylistMenuView(requireContext(), playlistMenuPresenter, childFragmentManager)

        imageLoader = GlideImageLoader(this)

        recyclerView = view.findViewById(R.id.recyclerView)
        recyclerView.adapter = adapter
        recyclerView.clearAdapterOnDetach()

        searchView = view.findViewById(R.id.searchView)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {

            override fun onQueryTextSubmit(s: String): Boolean {
                queryPublishSubject.onComplete()
                closeKeyboard()
                return true
            }

            override fun onQueryTextChange(text: String): Boolean {
                queryPublishSubject.onNext(text.trim())
                return true
            }
        })

        toolbar = view.findViewById(R.id.toolbar)
        toolbar.setNavigationOnClickListener {
            searchView.clearFocus()
            findNavController().popBackStack()
        }

        presenter.bindView(this)
        playlistMenuPresenter.bindView(playlistMenuView)
    }

    override fun onResume() {
        super.onResume()

        compositeDisposable.add(
            queryPublishSubject
                .debounce(300, TimeUnit.MILLISECONDS)
                .distinctUntilChanged()
                .switchMapCompletable { Completable.fromAction { presenter.loadData(it) } }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeBy(onError = { throwable -> Timber.e(throwable, "Failed to perform search.") })
        )
    }

    override fun onPause() {
        compositeDisposable.clear()
        super.onPause()
    }

    override fun onDestroyView() {
        adapter.dispose()

        presenter.unbindView()
        playlistMenuPresenter.unbindView()

        super.onDestroyView()
    }


    // SearchContract.View Implementation

    override fun setData(searchResult: Triple<List<AlbumArtist>, List<Album>, List<Song>>) {
        val list = mutableListOf<ViewBinder>().apply {
            if (searchResult.first.isNotEmpty()) {
                add(SearchHeaderBinder("Artists"))
                addAll(searchResult.first.map { albumArtist -> AlbumArtistBinder(albumArtist, imageLoader, albumArtistBinderListener) })
            }
            if (searchResult.first.isNotEmpty()) {
                add(SearchHeaderBinder("Albums"))
                addAll(searchResult.second.map { album -> AlbumBinder(album, imageLoader, albumBinderListener) })
            }
            if (searchResult.first.isNotEmpty()) {
                add(SearchHeaderBinder("Songs"))
                addAll(searchResult.third.map { song -> SongBinder(song, imageLoader, songBinderListener) })
            }
        }
        adapter.setData(list, completion = { recyclerView.scrollToPosition(0) })
    }

    override fun showLoadError(error: Error) {
        Toast.makeText(context, error.userDescription(), Toast.LENGTH_LONG).show()
    }

    override fun onAddedToQueue(albumArtist: AlbumArtist) {
        Toast.makeText(context, "${albumArtist.name} added to queue", Toast.LENGTH_SHORT).show()
    }

    override fun onAddedToQueue(album: Album) {
        Toast.makeText(context, "${album.name} added to queue", Toast.LENGTH_SHORT).show()
    }

    override fun onAddedToQueue(song: Song) {
        Toast.makeText(context, "${song.name} added to queue", Toast.LENGTH_SHORT).show()
    }


    // Private

    private val songBinderListener = object : SongBinder.Listener {

        override fun onSongClicked(song: Song) {
            closeKeyboard()
            presenter.onSongClicked(song)
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
                        R.id.blacklist -> {
                            presenter.blacklist(song)
                            return@setOnMenuItemClickListener true
                        }
                    }
                }
                false
            }
            popupMenu.show()
        }
    }

    private val albumArtistBinderListener = object : AlbumArtistBinder.Listener {

        override fun onAlbumArtistClicked(albumArtist: AlbumArtist, viewHolder: AlbumArtistBinder.ViewHolder) {
            closeKeyboard()
            presenter.onAlbumArtistCLicked(albumArtist)
        }

        override fun onOverflowClicked(view: View, albumArtist: AlbumArtist) {
            val popupMenu = PopupMenu(requireContext(), view)
            popupMenu.inflate(R.menu.menu_popup_add)

            playlistMenuView.createPlaylistMenu(popupMenu.menu)

            popupMenu.setOnMenuItemClickListener { menuItem ->
                if (playlistMenuView.handleMenuItem(menuItem, PlaylistData.AlbumArtists(albumArtist))) {
                    return@setOnMenuItemClickListener true
                } else {
                    when (menuItem.itemId) {
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

    private val albumBinderListener = object : AlbumBinder.Listener {

        override fun onAlbumClicked(album: Album, viewHolder: AlbumBinder.ViewHolder) {
            closeKeyboard()
            presenter.onAlbumClicked(album)
        }

        override fun onOverflowClicked(view: View, album: Album) {
            val popupMenu = PopupMenu(requireContext(), view)
            popupMenu.inflate(R.menu.menu_popup_add)

            playlistMenuView.createPlaylistMenu(popupMenu.menu)

            popupMenu.setOnMenuItemClickListener { menuItem ->
                if (playlistMenuView.handleMenuItem(menuItem, PlaylistData.Albums(album))) {
                    return@setOnMenuItemClickListener true
                } else {
                    when (menuItem.itemId) {
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

    private fun closeKeyboard() {
        view?.let { view ->
            (context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }


    // CreatePlaylistDialogFragment.Listener Implementation

    override fun onSave(text: String, playlistData: PlaylistData) {
        playlistMenuPresenter.createPlaylist(text, playlistData)
    }
}