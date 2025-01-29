package com.simplecityapps.shuttle.ui.screens.library.songs

import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import au.com.simplecityapps.shuttle.imageloading.glide.GlideImageLoader
import com.bumptech.glide.integration.recyclerview.RecyclerViewPreloader
import com.bumptech.glide.util.ViewPreloadSizeProvider
import com.simplecityapps.adapter.RecyclerAdapter
import com.simplecityapps.adapter.RecyclerListener
import com.simplecityapps.adapter.ViewBinder
import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.sorting.SongSortOrder
import com.simplecityapps.shuttle.ui.common.ContextualToolbarHelper
import com.simplecityapps.shuttle.ui.common.TagEditorMenuSanitiser
import com.simplecityapps.shuttle.ui.common.autoCleared
import com.simplecityapps.shuttle.ui.common.dialog.TagEditorAlertDialog
import com.simplecityapps.shuttle.ui.common.dialog.showDeleteDialog
import com.simplecityapps.shuttle.ui.common.dialog.showExcludeDialog
import com.simplecityapps.shuttle.ui.common.error.userDescription
import com.simplecityapps.shuttle.ui.common.recyclerview.GlidePreloadModelProvider
import com.simplecityapps.shuttle.ui.common.recyclerview.SectionedAdapter
import com.simplecityapps.shuttle.ui.common.view.CircularLoadingView
import com.simplecityapps.shuttle.ui.common.view.HorizontalLoadingView
import com.simplecityapps.shuttle.ui.common.view.findToolbarHost
import com.simplecityapps.shuttle.ui.screens.library.albums.ShuffleBinder
import com.simplecityapps.shuttle.ui.screens.playlistmenu.CreatePlaylistDialogFragment
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistMenuPresenter
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistMenuView
import com.simplecityapps.shuttle.ui.screens.songinfo.SongInfoDialogFragment
import com.squareup.phrase.Phrase
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SongListFragment :
    Fragment(),
    SongListContract.View,
    CreatePlaylistDialogFragment.Listener {
    @Inject
    lateinit var presenter: SongListPresenter

    @Inject
    lateinit var playlistMenuPresenter: PlaylistMenuPresenter

    private var adapter: RecyclerAdapter by autoCleared()

    lateinit var imageLoader: GlideImageLoader

    private lateinit var shuffleBinder: ShuffleBinder

    private lateinit var playlistMenuView: PlaylistMenuView

    private var circularLoadingView: CircularLoadingView by autoCleared()
    private var horizontalLoadingView: HorizontalLoadingView by autoCleared()

    private var recyclerView: RecyclerView by autoCleared()

    private var recyclerViewState: Parcelable? = null

    private var contextualToolbarHelper: ContextualToolbarHelper<com.simplecityapps.shuttle.model.Song> by autoCleared()

    private val viewPreloadSizeProvider by lazy { ViewPreloadSizeProvider<com.simplecityapps.shuttle.model.Song>() }
    private val preloadModelProvider by lazy {
        GlidePreloadModelProvider<com.simplecityapps.shuttle.model.Song>(
            imageLoader,
            listOf(ArtworkImageLoader.Options.CacheDecodedResource)
        )
    }

    // Lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_songs, container, false)

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        imageLoader = GlideImageLoader(this)

        setHasOptionsMenu(true)

        playlistMenuView = PlaylistMenuView(requireContext(), playlistMenuPresenter, childFragmentManager)

        adapter =
            object : SectionedAdapter(viewLifecycleOwner.lifecycleScope) {
                override fun getSectionName(viewBinder: ViewBinder?): String = (viewBinder as? SongBinder)?.song?.let { song ->
                    presenter.getFastscrollPrefix(song)
                } ?: ""
            }
        recyclerView = view.findViewById(R.id.recyclerView)
        recyclerView.adapter = adapter
        recyclerView.setRecyclerListener(RecyclerListener())
        val preloader: RecyclerViewPreloader<com.simplecityapps.shuttle.model.Song> =
            RecyclerViewPreloader(
                imageLoader.requestManager,
                preloadModelProvider,
                viewPreloadSizeProvider,
                12
            )
        recyclerView.addOnScrollListener(preloader)

        circularLoadingView = view.findViewById(R.id.circularLoadingView)
        horizontalLoadingView = view.findViewById(R.id.horizontalLoadingView)

        shuffleBinder =
            ShuffleBinder(
                R.string.btn_shuffle,
                object : ShuffleBinder.Listener {
                    override fun onClicked() {
                        presenter.shuffle()
                    }
                }
            )

        savedInstanceState?.getParcelable<Parcelable>(ARG_RECYCLER_STATE)?.let { recyclerViewState = it }

        contextualToolbarHelper = ContextualToolbarHelper()

        updateContextualToolbar()

        presenter.bindView(this)
        playlistMenuPresenter.bindView(playlistMenuView)
    }

    override fun onCreateOptionsMenu(
        menu: Menu,
        inflater: MenuInflater
    ) {
        super.onCreateOptionsMenu(menu, inflater)

        inflater.inflate(R.menu.menu_song_list, menu)

        presenter.updateToolbarMenu()
    }

    override fun onResume() {
        super.onResume()

        presenter.loadSongs(false)

        updateContextualToolbar()
    }

    override fun onPause() {
        super.onPause()

        findToolbarHost()?.apply {
            contextualToolbar?.setOnMenuItemClickListener(null)
        }

        recyclerViewState = recyclerView.layoutManager?.onSaveInstanceState()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putParcelable(ARG_RECYCLER_STATE, recyclerViewState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        presenter.unbindView()
        playlistMenuPresenter.unbindView()

        super.onDestroyView()
    }

    // Toolbar item selection

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.sortSongName -> {
            presenter.setSortOrder(SongSortOrder.SongName)
            true
        }
        R.id.sortArtistName -> {
            presenter.setSortOrder(SongSortOrder.ArtistGroupKey)
            true
        }
        R.id.sortAlbumName -> {
            presenter.setSortOrder(SongSortOrder.AlbumGroupKey)
            true
        }
        R.id.sortSongYear -> {
            presenter.setSortOrder(SongSortOrder.Year)
            true
        }
        R.id.sortSongDuration -> {
            presenter.setSortOrder(SongSortOrder.Duration)
            true
        }
        R.id.sortSongDateModified -> {
            presenter.setSortOrder(SongSortOrder.LastModified)
            true
        }
        else -> false
    }

    // Private

    private fun updateContextualToolbar() {
        findToolbarHost()?.apply {
            contextualToolbar?.let { contextualToolbar ->
                contextualToolbar.menu.clear()
                contextualToolbar.inflateMenu(R.menu.menu_multi_select)
                TagEditorMenuSanitiser.sanitise(contextualToolbar.menu, contextualToolbarHelper.selectedItems.map { it.mediaProvider }.distinct())
                contextualToolbar.setOnMenuItemClickListener { menuItem ->
                    playlistMenuView.createPlaylistMenu(contextualToolbar.menu)
                    if (playlistMenuView.handleMenuItem(menuItem, PlaylistData.Songs(contextualToolbarHelper.selectedItems.toList()))) {
                        contextualToolbarHelper.hide()
                        return@setOnMenuItemClickListener true
                    }
                    when (menuItem.itemId) {
                        R.id.queue -> {
                            presenter.addToQueue(contextualToolbarHelper.selectedItems.toList())
                            contextualToolbarHelper.hide()
                            true
                        }
                        R.id.editTags -> {
                            TagEditorAlertDialog.newInstance(contextualToolbarHelper.selectedItems.toList()).show(childFragmentManager)
                            contextualToolbarHelper.hide()
                            true
                        }
                        else -> false
                    }
                }
            }
            contextualToolbarHelper.contextualToolbar = contextualToolbar
            contextualToolbarHelper.toolbar = toolbar
            contextualToolbarHelper.callback = contextualToolbarCallback

            if (contextualToolbarHelper.selectedItems.isNotEmpty()) {
                contextualToolbarHelper.show()
            }
        }
    }

    // SongListContract.View Implementation

    override fun setData(
        songs: List<com.simplecityapps.shuttle.model.Song>,
        resetPosition: Boolean
    ) {
        preloadModelProvider.items = songs

        if (resetPosition) {
            adapter.clear()
        }

        val data =
            songs.map { song ->
                SongBinder(song, imageLoader, songBinderListener).apply {
                    selected = contextualToolbarHelper.selectedItems.any { it.id == song.id }
                }
            }.toMutableList<ViewBinder>()

        if (songs.isNotEmpty()) {
            data.add(0, shuffleBinder)
        }

        adapter.update(data) {
            recyclerViewState?.let {
                recyclerView.layoutManager?.onRestoreInstanceState(recyclerViewState)
                recyclerViewState = null
            }
        }
    }

    override fun updateToolbarMenuSortOrder(sortOrder: SongSortOrder) {
        findToolbarHost()?.toolbar?.menu?.let { menu ->
            when (sortOrder) {
                SongSortOrder.SongName -> menu.findItem(R.id.sortSongName)?.isChecked = true
                SongSortOrder.ArtistGroupKey -> menu.findItem(R.id.sortArtistName)?.isChecked = true
                SongSortOrder.AlbumGroupKey -> menu.findItem(R.id.sortAlbumName)?.isChecked = true
                SongSortOrder.Year -> menu.findItem(R.id.sortSongYear)?.isChecked = true
                SongSortOrder.Duration -> menu.findItem(R.id.sortSongDuration)?.isChecked = true
                SongSortOrder.LastModified -> menu.findItem(R.id.sortSongDateModified)?.isChecked = true
                else -> {
                    // Nothing to do
                }
            }
        }
    }

    override fun showLoadError(error: Error) {
        Toast.makeText(context, error.userDescription(resources), Toast.LENGTH_LONG).show()
    }

    override fun onAddedToQueue(songs: List<com.simplecityapps.shuttle.model.Song>) {
        Toast.makeText(
            context,
            Phrase.fromPlural(resources, R.plurals.queue_songs_added, songs.size)
                .put("count", songs.size)
                .format(),
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun setLoadingState(state: SongListContract.LoadingState) {
        when (state) {
            is SongListContract.LoadingState.Scanning -> {
                horizontalLoadingView.setState(HorizontalLoadingView.State.Loading(getString(R.string.library_scan_in_progress)))
                circularLoadingView.setState(CircularLoadingView.State.None)
            }
            is SongListContract.LoadingState.Loading -> {
                horizontalLoadingView.setState(HorizontalLoadingView.State.None)
                circularLoadingView.setState(CircularLoadingView.State.Loading(getString(R.string.loading)))
            }
            is SongListContract.LoadingState.Empty -> {
                horizontalLoadingView.setState(HorizontalLoadingView.State.None)
                circularLoadingView.setState(CircularLoadingView.State.Empty(getString(R.string.song_list_empty)))
            }
            is SongListContract.LoadingState.None -> {
                horizontalLoadingView.setState(HorizontalLoadingView.State.None)
                circularLoadingView.setState(CircularLoadingView.State.None)
            }
        }
    }

    override fun setLoadingProgress(progress: Progress?) {
        progress?.let {
            horizontalLoadingView.setProgress(progress.asFloat())
        }
    }

    override fun showDeleteError(error: Error) {
        Toast.makeText(requireContext(), error.userDescription(resources), Toast.LENGTH_LONG).show()
    }

    // Private

    private val songBinderListener =
        object : SongBinder.Listener {
            override fun onSongClicked(song: com.simplecityapps.shuttle.model.Song) {
                if (!contextualToolbarHelper.handleClick(song)) {
                    presenter.onSongClicked(song)
                }
            }

            override fun onSongLongClicked(song: com.simplecityapps.shuttle.model.Song) {
                contextualToolbarHelper.handleLongClick(song)
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
                                presenter.addToQueue(listOf(song))
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
                                TagEditorAlertDialog.newInstance(listOf(song)).show(childFragmentManager)
                            }
                        }
                    }
                    false
                }
                popupMenu.show()
            }

            override fun onViewHolderCreated(holder: SongBinder.ViewHolder) {
                viewPreloadSizeProvider.setView(holder.imageView)
            }
        }

    // CreatePlaylistDialogFragment.Listener Implementation

    override fun onSave(
        text: String,
        playlistData: PlaylistData
    ) {
        playlistMenuPresenter.createPlaylist(text, playlistData)
    }

    // ContextualToolbarHelper.Callback Implementation

    private val contextualToolbarCallback =
        object : ContextualToolbarHelper.Callback<com.simplecityapps.shuttle.model.Song> {
            override fun onCountChanged(count: Int) {
                contextualToolbarHelper.contextualToolbar?.title =
                    Phrase.fromPlural(requireContext(), R.plurals.multi_select_items_selected, count)
                        .put("count", count)
                        .format()
                contextualToolbarHelper.contextualToolbar?.menu?.let { menu ->
                    TagEditorMenuSanitiser.sanitise(menu, contextualToolbarHelper.selectedItems.map { it.mediaProvider }.distinct())
                }
            }

            override fun onItemUpdated(
                item: com.simplecityapps.shuttle.model.Song,
                isSelected: Boolean
            ) {
                adapter.items
                    .filterIsInstance<SongBinder>()
                    .firstOrNull { it.song.id == item.id }
                    ?.let { viewBinder ->
                        viewBinder.selected = isSelected
                        adapter.notifyItemChanged(adapter.items.indexOf(viewBinder))
                    }
            }
        }

    // Static

    companion object {
        const val TAG = "SongListFragment"
        const val ARG_RECYCLER_STATE = "recycler_state"

        fun newInstance() = SongListFragment()
    }
}
