package com.simplecityapps.shuttle.ui.screens.home.search

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.CompoundButton
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.Transition
import androidx.transition.TransitionInflater
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import com.google.android.material.chip.Chip
import com.simplecityapps.adapter.RecyclerAdapter
import com.simplecityapps.adapter.ViewBinder
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.TagEditorMenuSanitiser
import com.simplecityapps.shuttle.ui.common.autoCleared
import com.simplecityapps.shuttle.ui.common.closeKeyboard
import com.simplecityapps.shuttle.ui.common.dialog.TagEditorAlertDialog
import com.simplecityapps.shuttle.ui.common.dialog.showDeleteDialog
import com.simplecityapps.shuttle.ui.common.dialog.showExcludeDialog
import com.simplecityapps.shuttle.ui.common.error.userDescription
import com.simplecityapps.shuttle.ui.screens.library.albumartists.AlbumArtistBinder
import com.simplecityapps.shuttle.ui.screens.library.albumartists.detail.AlbumArtistDetailFragmentArgs
import com.simplecityapps.shuttle.ui.screens.library.albums.AlbumBinder
import com.simplecityapps.shuttle.ui.screens.library.albums.detail.AlbumDetailFragmentArgs
import com.simplecityapps.shuttle.ui.screens.playlistmenu.CreatePlaylistDialogFragment
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistMenuPresenter
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistMenuView
import com.simplecityapps.shuttle.ui.screens.songinfo.SongInfoDialogFragment
import com.squareup.phrase.Phrase
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SearchFragment :
    Fragment(),
    SearchContract.View,
    CreatePlaylistDialogFragment.Listener {
    private var adapter: RecyclerAdapter by autoCleared()
    private var searchView: SearchView by autoCleared()
    private var recyclerView: RecyclerView by autoCleared()
    private var progressBar: View by autoCleared()
    private var emptyStateView: View by autoCleared()
    private var toolbar: Toolbar by autoCleared()
    private var artistsChip: Chip by autoCleared()
    private var albumsChip: Chip by autoCleared()
    private var songsChip: Chip by autoCleared()

    private var hasSearched = false

    @Inject
    lateinit var presenter: SearchPresenter

    @Inject
    lateinit var playlistMenuPresenter: PlaylistMenuPresenter

    @Inject
    lateinit var imageLoader: ArtworkImageLoader

    private lateinit var playlistMenuView: PlaylistMenuView

    private val queryFlow = MutableStateFlow("")

    // Lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedElementEnterTransition = TransitionInflater.from(requireContext()).inflateTransition(android.R.transition.move)
        (sharedElementEnterTransition as Transition).duration = 150L
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_search, container, false)

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        adapter = RecyclerAdapter(viewLifecycleOwner.lifecycleScope)

        playlistMenuView = PlaylistMenuView(requireContext(), playlistMenuPresenter, childFragmentManager)

        recyclerView = view.findViewById(R.id.recyclerView)
        recyclerView.adapter = adapter

        progressBar = view.findViewById(R.id.progressBar)
        emptyStateView = view.findViewById(R.id.emptyStateView)

        searchView = view.findViewById(R.id.searchView)
        searchView.setOnQueryTextListener(
            object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(s: String): Boolean {
                    view.closeKeyboard()
                    return true
                }

                override fun onQueryTextChange(text: String): Boolean {
                    viewLifecycleOwner.lifecycleScope.launch {
                        val trimmedText = text.trim()
                        queryFlow.update { trimmedText }

                        // Show loading indicator when user types a non-empty query
                        if (trimmedText.isNotEmpty()) {
                            progressBar.visibility = View.VISIBLE
                            recyclerView.visibility = View.GONE
                            emptyStateView.visibility = View.GONE
                        } else {
                            // Clear all views when query is empty (initial state)
                            progressBar.visibility = View.GONE
                            recyclerView.visibility = View.VISIBLE
                            emptyStateView.visibility = View.GONE
                            hasSearched = false
                            // Clear the adapter to show empty recycler view
                            adapter.clear()
                        }
                    }
                    return true
                }
            }
        )
        searchView.setOnQueryTextFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                val inputMethodManager = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                inputMethodManager.showSoftInput(v.findFocus(), 0)
            }
        }
        searchView.post { searchView.requestFocus() }

        toolbar = view.findViewById(R.id.toolbar)
        toolbar.setNavigationOnClickListener {
            searchView.clearFocus()
            findNavController().popBackStack()
        }

        artistsChip = view.findViewById(R.id.artistsChip)
        albumsChip = view.findViewById(R.id.albumsChip)
        songsChip = view.findViewById(R.id.songsChip)

        val checkedChangedListener = CompoundButton.OnCheckedChangeListener { _, _ -> presenter.updateFilters(artistsChip.isChecked, albumsChip.isChecked, songsChip.isChecked) }
        artistsChip.setOnCheckedChangeListener(checkedChangedListener)
        albumsChip.setOnCheckedChangeListener(checkedChangedListener)
        songsChip.setOnCheckedChangeListener(checkedChangedListener)

        presenter.bindView(this)
        playlistMenuPresenter.bindView(playlistMenuView)
    }

    override fun onResume() {
        super.onResume()

        viewLifecycleOwner.lifecycleScope.launch {
            queryFlow
                .debounce(300)  // Reduced from 500ms to 300ms based on UX research
                .flowOn(Dispatchers.IO)
                .collect { query ->
                    presenter.loadData(query)
                }
        }
    }

    override fun onDestroyView() {
        presenter.unbindView()
        playlistMenuPresenter.unbindView()

        super.onDestroyView()
    }

    // SearchContract.View Implementation

    override fun setData(searchResult: Triple<List<ArtistJaroSimilarity>, List<AlbumJaroSimilarity>, List<SongJaroSimilarity>>) {
        // Mark that we've completed a search
        hasSearched = true

        // Hide loading indicator
        progressBar.visibility = View.GONE

        // Check if we have any results
        val hasResults = searchResult.first.isNotEmpty() || searchResult.second.isNotEmpty() || searchResult.third.isNotEmpty()

        // Show/hide views based on whether we have results
        if (hasResults) {
            recyclerView.visibility = View.VISIBLE
            emptyStateView.visibility = View.GONE
        } else {
            // Only show "No results found" if we've performed a search
            recyclerView.visibility = View.GONE
            emptyStateView.visibility = if (hasSearched) View.VISIBLE else View.GONE
        }

        // If we're displaying too many items, clear the adapter data, so calculating the diff is faster
        if (adapter.itemCount > 100) {
            adapter.clear()
        }
        val list =
            mutableListOf<ViewBinder>().apply {
                if (searchResult.first.isNotEmpty()) {
                    add(HeaderBinder(getString(R.string.artists)))
                    addAll(
                        searchResult.first.map { artistResult ->
                            SearchAlbumArtistBinder(
                                albumArtist = artistResult.albumArtist,
                                imageLoader = imageLoader,
                                listener = albumArtistBinderListener,
                                jaroSimilarity = artistResult
                            )
                        }
                    )
                }
                if (searchResult.second.isNotEmpty()) {
                    add(HeaderBinder(getString(R.string.albums)))
                    addAll(
                        searchResult.second.map { albumResult ->
                            SearchAlbumBinder(
                                album = albumResult.album,
                                imageLoader = imageLoader,
                                listener = albumBinderListener,
                                jaroSimilarity = albumResult
                            )
                        }
                    )
                }
                if (searchResult.third.isNotEmpty()) {
                    add(HeaderBinder(getString(R.string.songs)))
                    addAll(
                        searchResult.third.map { songResult ->
                            SearchSongBinder(
                                song = songResult.song,
                                imageLoader = imageLoader,
                                listener = songBinderListener,
                                jaroSimilarity = songResult
                            )
                        }
                    )
                }
            }
        adapter.update(list) { recyclerView.scrollToPosition(0) }
    }

    override fun showLoadError(error: Error) {
        Toast.makeText(context, error.userDescription(resources), Toast.LENGTH_LONG).show()
    }

    override fun onAddedToQueue(albumArtist: com.simplecityapps.shuttle.model.AlbumArtist) {
        Toast.makeText(context, Phrase.from(requireContext(), R.string.queue_item_added).put("item_name", albumArtist.name ?: albumArtist.friendlyArtistName).format(), Toast.LENGTH_SHORT).show()
    }

    override fun onAddedToQueue(album: com.simplecityapps.shuttle.model.Album) {
        Toast.makeText(context, Phrase.from(requireContext(), R.string.queue_item_added).put("item_name", album.name).format(), Toast.LENGTH_SHORT).show()
    }

    override fun onAddedToQueue(song: com.simplecityapps.shuttle.model.Song) {
        Toast.makeText(context, Phrase.from(requireContext(), R.string.queue_item_added).put("item_name", song.name).format(), Toast.LENGTH_SHORT).show()
    }

    override fun showDeleteError(error: Error) {
        Toast.makeText(requireContext(), error.userDescription(resources), Toast.LENGTH_LONG).show()
    }

    override fun showTagEditor(songs: List<com.simplecityapps.shuttle.model.Song>) {
        TagEditorAlertDialog.newInstance(songs).show(childFragmentManager)
    }

    override fun updateFilters(
        artists: Boolean,
        albums: Boolean,
        songs: Boolean
    ) {
        artistsChip.isChecked = artists
        albumsChip.isChecked = albums
        songsChip.isChecked = songs
    }

    override fun updateQuery(query: String?) {
        queryFlow.update { query ?: "" }
        searchView.setQuery(query, false)
    }

    // Private

    private val songBinderListener =
        object : SearchSongBinder.Listener {
            override fun onSongClicked(song: com.simplecityapps.shuttle.model.Song) {
                view?.closeKeyboard()
                presenter.play(song)
            }

            override fun onOverflowClicked(
                view: View,
                song: com.simplecityapps.shuttle.model.Song
            ) {
                val popupMenu = PopupMenu(requireContext(), view)
                popupMenu.inflate(R.menu.menu_popup_song)
                TagEditorMenuSanitiser.sanitise(popupMenu.menu, listOf(song.mediaProvider))

                playlistMenuView.createPlaylistMenu(popupMenu.menu)

                if (song.externalId != null) {
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
                                showExcludeDialog(requireContext(), song.name) {
                                    presenter.exclude(song)
                                }
                                return@setOnMenuItemClickListener true
                            }

                            R.id.delete -> {
                                showDeleteDialog(requireContext(), song.name) {
                                    presenter.delete(song)
                                }
                                return@setOnMenuItemClickListener true
                            }

                            R.id.editTags -> {
                                presenter.editTags(song)
                                return@setOnMenuItemClickListener true
                            }
                        }
                    }
                    false
                }
                popupMenu.show()
            }
        }

    private val albumArtistBinderListener =
        object : AlbumArtistBinder.Listener {
            override fun onAlbumArtistClicked(
                albumArtist: com.simplecityapps.shuttle.model.AlbumArtist,
                viewHolder: AlbumArtistBinder.ViewHolder
            ) {
                view?.closeKeyboard()
                if (findNavController().currentDestination?.id != R.id.albumArtistDetailFragment) {
                    findNavController().navigate(
                        R.id.action_searchFragment_to_albumArtistDetailFragment,
                        AlbumArtistDetailFragmentArgs(albumArtist, true).toBundle(),
                        null,
                        FragmentNavigatorExtras(viewHolder.imageView to viewHolder.imageView.transitionName)
                    )
                }
            }

            override fun onAlbumArtistLongClicked(
                view: View,
                albumArtist: com.simplecityapps.shuttle.model.AlbumArtist
            ) {
            }

            override fun onOverflowClicked(
                view: View,
                albumArtist: com.simplecityapps.shuttle.model.AlbumArtist
            ) {
                val popupMenu = PopupMenu(requireContext(), view)
                popupMenu.inflate(R.menu.menu_popup)
                TagEditorMenuSanitiser.sanitise(popupMenu.menu, albumArtist.mediaProviders)

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

                            R.id.exclude -> {
                                presenter.exclude(albumArtist)
                                return@setOnMenuItemClickListener true
                            }

                            R.id.editTags -> {
                                presenter.editTags(albumArtist)
                                return@setOnMenuItemClickListener true
                            }
                        }
                    }
                    false
                }
                popupMenu.show()
            }
        }

    private val albumBinderListener =
        object : AlbumBinder.Listener {
            override fun onAlbumClicked(
                album: com.simplecityapps.shuttle.model.Album,
                viewHolder: AlbumBinder.ViewHolder
            ) {
                view?.closeKeyboard()
                if (findNavController().currentDestination?.id != R.id.albumDetailFragment) {
                    findNavController().navigate(
                        R.id.action_searchFragment_to_albumDetailFragment,
                        AlbumDetailFragmentArgs(album, true).toBundle(),
                        null,
                        FragmentNavigatorExtras(viewHolder.imageView to viewHolder.imageView.transitionName)
                    )
                }
            }

            override fun onAlbumLongClicked(
                album: com.simplecityapps.shuttle.model.Album,
                viewHolder: AlbumBinder.ViewHolder
            ) {
            }

            override fun onOverflowClicked(
                view: View,
                album: com.simplecityapps.shuttle.model.Album
            ) {
                val popupMenu = PopupMenu(requireContext(), view)
                popupMenu.inflate(R.menu.menu_popup)
                TagEditorMenuSanitiser.sanitise(popupMenu.menu, album.mediaProviders)

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

    override fun onSave(
        text: String,
        playlistData: PlaylistData
    ) {
        playlistMenuPresenter.createPlaylist(text, playlistData)
    }
}
