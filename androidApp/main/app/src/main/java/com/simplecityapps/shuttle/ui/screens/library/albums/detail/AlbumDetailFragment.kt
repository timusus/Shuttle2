package com.simplecityapps.shuttle.ui.screens.library.albums.detail

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.Toolbar
import androidx.core.content.res.ResourcesCompat
import androidx.core.os.postDelayed
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.Transition
import androidx.transition.TransitionInflater
import androidx.transition.TransitionListenerAdapter
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import com.simplecityapps.adapter.RecyclerAdapter
import com.simplecityapps.adapter.ViewBinder
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.ui.common.TagEditorMenuSanitiser
import com.simplecityapps.shuttle.ui.common.autoCleared
import com.simplecityapps.shuttle.ui.common.autoClearedNullable
import com.simplecityapps.shuttle.ui.common.dialog.ShowDeleteDialog
import com.simplecityapps.shuttle.ui.common.dialog.ShowExcludeDialog
import com.simplecityapps.shuttle.ui.common.dialog.TagEditorAlertDialog
import com.simplecityapps.shuttle.ui.common.error.userDescription
import com.simplecityapps.shuttle.ui.common.phrase.joinSafely
import com.simplecityapps.shuttle.ui.common.utils.toHms
import com.simplecityapps.shuttle.ui.common.view.DetailImageAnimationHelper
import com.simplecityapps.shuttle.ui.common.viewbinders.DetailSongBinder
import com.simplecityapps.shuttle.ui.common.viewbinders.DiscNumberBinder
import com.simplecityapps.shuttle.ui.common.viewbinders.GroupingBinder
import com.simplecityapps.shuttle.ui.screens.playlistmenu.CreatePlaylistDialogFragment
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistMenuPresenter
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistMenuView
import com.simplecityapps.shuttle.ui.screens.songinfo.SongInfoDialogFragment
import com.squareup.phrase.ListPhrase
import com.squareup.phrase.Phrase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AlbumDetailFragment :
    Fragment(),
    AlbumDetailContract.View,
    CreatePlaylistDialogFragment.Listener {

    @Inject
    lateinit var presenterFactory: AlbumDetailPresenter.Factory

    @Inject
    lateinit var playlistMenuPresenter: PlaylistMenuPresenter

    @Inject
    lateinit var imageLoader: ArtworkImageLoader

    private lateinit var presenter: AlbumDetailPresenter

    private var adapter: RecyclerAdapter by autoCleared()

    private var animationHelper: DetailImageAnimationHelper? by autoClearedNullable()

    private val handler = Handler(Looper.getMainLooper())

    private lateinit var album: com.simplecityapps.shuttle.model.Album

    private lateinit var playlistMenuView: PlaylistMenuView

    private var recyclerView: RecyclerView by autoCleared()

    private var toolbar: Toolbar by autoCleared()

    private var dummyImage: ImageView by autoCleared()

    private var heroImage: ImageView by autoCleared()

    private var showHeroView = false
    private var animateTransition: Boolean = true

    private var recyclerViewState: Parcelable? = null

    // Lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        album = AlbumDetailFragmentArgs.fromBundle(requireArguments()).album
        animateTransition = AlbumDetailFragmentArgs.fromBundle(requireArguments()).animateTransition
        presenter = presenterFactory.create(album)

        sharedElementEnterTransition = TransitionInflater.from(requireContext()).inflateTransition(R.transition.image_shared_element_transition)
        (sharedElementEnterTransition as Transition).duration = 150L
        (sharedElementEnterTransition as Transition).addListener(object : TransitionListenerAdapter() {
            override fun onTransitionEnd(transition: Transition) {
                animationHelper?.showHeroView()
                showHeroView = true
                transition.removeListener(this)
            }
        })
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        if (savedInstanceState == null) {
            postponeEnterTransition()
        }
        return inflater.inflate(R.layout.fragment_album_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        playlistMenuView = PlaylistMenuView(requireContext(), playlistMenuPresenter, childFragmentManager)

        handler.postDelayed(300) {
            startPostponedEnterTransition() // In case our artwork load takes too long
        }

        dummyImage = view.findViewById(R.id.dummyImage)
        dummyImage.transitionName = "album_${album.name}"

        imageLoader.loadArtwork(
            dummyImage,
            album,
            listOf(
                ArtworkImageLoader.Options.CircleCrop,
                ArtworkImageLoader.Options.Priority.Max
            )
        ) {
            startPostponedEnterTransition()
        }

        heroImage = view.findViewById(R.id.heroImage)
        imageLoader.loadArtwork(
            heroImage,
            album,
            listOf(
                ArtworkImageLoader.Options.Priority.Max,
                ArtworkImageLoader.Options.Placeholder(ResourcesCompat.getDrawable(resources, R.drawable.ic_placeholder_album, requireContext().theme)!!)
            )
        )
        if (savedInstanceState != null || showHeroView || !animateTransition) {
            heroImage.isVisible = true
            dummyImage.isVisible = false
        }

        toolbar = view.findViewById(R.id.toolbar)
        toolbar.let { toolbar ->
            toolbar.setNavigationOnClickListener {
                NavHostFragment.findNavController(this).popBackStack()
            }
            toolbar.inflateMenu(R.menu.menu_album_detail)
            TagEditorMenuSanitiser.sanitise(toolbar.menu, album.mediaProviders)

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
                        true
                    }
                    R.id.editTags -> {
                        presenter.editTags(album)
                        true
                    }
                    R.id.playlist -> {
                        playlistMenuView.createPlaylistMenu(toolbar.menu)
                        true
                    }
                    else -> {
                        playlistMenuView.handleMenuItem(menuItem, PlaylistData.Albums(album))
                    }
                }
            }
        }

        adapter = RecyclerAdapter(viewLifecycleOwner.lifecycleScope)
        recyclerView = view.findViewById(R.id.recyclerView)
        recyclerView.adapter = adapter

        savedInstanceState?.getParcelable<Parcelable>(ARG_RECYCLER_STATE)?.let { recyclerViewState = it }

        animationHelper = DetailImageAnimationHelper(heroImage, dummyImage)

        presenter.bindView(this)
        playlistMenuPresenter.bindView(playlistMenuView)
    }

    override fun onResume() {
        super.onResume()

        viewLifecycleOwner.lifecycleScope.launch {
            delay(150)
            presenter.loadData()
        }

        recyclerViewState?.let {
            recyclerView.layoutManager?.onRestoreInstanceState(recyclerViewState)
        }
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

        super.onDestroyView()
    }

    // AlbumDetailContract.View Implementation

    override fun setData(songs: List<com.simplecityapps.shuttle.model.Song>) {
        val discGroupingSongsMap = songs
            .groupBy { song -> song.disc ?: 1 }
            .toSortedMap()
            .mapValues { entry ->
                entry.value.groupBy { song -> song.grouping ?: "" }
            }
        val currentSong = presenter.getCurrentSong()

        adapter.update(
            discGroupingSongsMap.flatMap { discEntry ->
                val viewBinders = mutableListOf<ViewBinder>()
                if (discGroupingSongsMap.size > 1) {
                    viewBinders.add(DiscNumberBinder(Phrase.from(context, R.string.disc_number).put("disc_number", discEntry.key).format().toString()))
                }

                val groupingMap = discEntry.value
                groupingMap.flatMap { groupingEntry ->
                    if (groupingEntry.key.isNotEmpty()) {
                        viewBinders.add(GroupingBinder(groupingEntry.key))
                    }
                    viewBinders.addAll(
                        groupingEntry.value.map { song ->
                            DetailSongBinder(song, currentSong, songBinderListener)
                        }
                    )
                    viewBinders
                }

                viewBinders
            }
        ) {
            recyclerView.layoutManager?.onRestoreInstanceState(recyclerViewState)
            recyclerViewState = null
        }
    }

    override fun showLoadError(error: Error) {
        Toast.makeText(context, error.userDescription(resources), Toast.LENGTH_LONG).show()
    }

    override fun onAddedToQueue(name: String) {
        Toast.makeText(context, Phrase.from(requireContext(), R.string.queue_item_added).put("item_name", name).format(), Toast.LENGTH_SHORT).show()
    }

    override fun onCurrentSongChanged(newCurrentSong: Song) {
        setData(presenter.songs)
    }

    override fun setAlbum(album: com.simplecityapps.shuttle.model.Album) {
        toolbar.title = album.name
        val songsQuantity = Phrase.fromPlural(resources, R.plurals.songsPlural, album.songCount)
            .put("count", album.songCount)
            .format()
        toolbar.subtitle = ListPhrase
            .from(" • ")
            .joinSafely(
                listOf(
                    album.year?.toString(),
                    songsQuantity,
                    album.duration.toHms()
                )
            )
    }

    override fun showDeleteError(error: Error) {
        Toast.makeText(requireContext(), error.userDescription(resources), Toast.LENGTH_LONG).show()
    }

    override fun showTagEditor(songs: List<com.simplecityapps.shuttle.model.Song>) {
        TagEditorAlertDialog.newInstance(songs).show(childFragmentManager)
    }

    // SongBinder.Listener Implementation

    private val songBinderListener = object : DetailSongBinder.Listener {

        override fun onSongClicked(song: com.simplecityapps.shuttle.model.Song) {
            presenter.onSongClicked(song)
        }

        override fun onOverflowClicked(view: View, song: com.simplecityapps.shuttle.model.Song) {
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
                            ShowExcludeDialog(requireContext(), song.name) {
                                presenter.exclude(song)
                            }
                            return@setOnMenuItemClickListener true
                        }
                        R.id.editTags -> {
                            presenter.editTags(song)
                            return@setOnMenuItemClickListener true
                        }
                        R.id.delete -> {
                            ShowDeleteDialog(requireContext(), song.name) {
                                presenter.delete(song)
                            }
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

    // Static

    companion object {
        const val ARG_RECYCLER_STATE = "recycler_state"
    }
}
