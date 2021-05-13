package com.simplecityapps.shuttle.ui.screens.library.albumartists.detail

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.Toolbar
import androidx.core.os.postDelayed
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.NavHostFragment
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.Transition
import androidx.transition.TransitionInflater
import androidx.transition.TransitionListenerAdapter
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import com.simplecityapps.adapter.RecyclerAdapter
import com.simplecityapps.adapter.ViewBinder
import com.simplecityapps.mediaprovider.model.Album
import com.simplecityapps.mediaprovider.model.AlbumArtist
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.TagEditorMenuSanitiser
import com.simplecityapps.shuttle.ui.common.autoCleared
import com.simplecityapps.shuttle.ui.common.autoClearedNullable
import com.simplecityapps.shuttle.ui.common.dialog.ShowDeleteDialog
import com.simplecityapps.shuttle.ui.common.dialog.ShowExcludeDialog
import com.simplecityapps.shuttle.ui.common.dialog.TagEditorAlertDialog
import com.simplecityapps.shuttle.ui.common.error.userDescription
import com.simplecityapps.shuttle.ui.common.view.DetailImageAnimationHelper
import com.simplecityapps.shuttle.ui.screens.home.search.HeaderBinder
import com.simplecityapps.shuttle.ui.screens.library.albums.detail.AlbumDetailFragmentArgs
import com.simplecityapps.shuttle.ui.screens.library.songs.SongBinder
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
class AlbumArtistDetailFragment :
    Fragment(),
    AlbumArtistDetailContract.View,
    ExpandableAlbumBinder.Listener,
    CreatePlaylistDialogFragment.Listener {

    @Inject
    lateinit var presenterFactory: AlbumArtistDetailPresenter.Factory

    @Inject
    lateinit var playlistMenuPresenter: PlaylistMenuPresenter

    @Inject
    lateinit var imageLoader: ArtworkImageLoader

    private lateinit var presenter: AlbumArtistDetailPresenter

    private var adapter: RecyclerAdapter by autoCleared()

    private var animationHelper: DetailImageAnimationHelper? by autoClearedNullable()

    private val handler = Handler(Looper.getMainLooper())

    private var postponedTransitionCounter = 2

    private lateinit var albumArtist: AlbumArtist

    private lateinit var playlistMenuView: PlaylistMenuView

    private var recyclerView: RecyclerView by autoCleared()

    private var toolbar: Toolbar by autoCleared()

    private var dummyImage: ImageView by autoCleared()

    private var heroImage: ImageView by autoCleared()

    private var showHeroView = false
    private var animateTransition: Boolean = true

    // Lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        albumArtist = AlbumArtistDetailFragmentArgs.fromBundle(requireArguments()).albumArtist
        animateTransition = AlbumArtistDetailFragmentArgs.fromBundle(requireArguments()).animateTransition
        presenter = presenterFactory.create(albumArtist)

        sharedElementEnterTransition = TransitionInflater.from(context).inflateTransition(R.transition.image_shared_element_transition)
        (sharedElementEnterTransition as Transition).duration = 150L
        (sharedElementEnterTransition as Transition).addListener(object : TransitionListenerAdapter() {
            override fun onTransitionEnd(transition: Transition) {
                animationHelper?.showHeroView()
                showHeroView = true
                transition.removeListener(this)
            }
        })

        sharedElementReturnTransition = TransitionInflater.from(context).inflateTransition(R.transition.image_shared_element_transition)
        (sharedElementReturnTransition as Transition).duration = 150L
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        postponedTransitionCounter = 2
        postponeEnterTransition()
        return inflater.inflate(R.layout.fragment_album_artist_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        playlistMenuView = PlaylistMenuView(requireContext(), playlistMenuPresenter, childFragmentManager)

        handler.postDelayed(300) {
            startPostponedEnterTransition() // In case our image load takes too long
        }

        toolbar = view.findViewById(R.id.toolbar)
        toolbar.let { toolbar ->
            toolbar.setNavigationOnClickListener { NavHostFragment.findNavController(this).popBackStack() }
            toolbar.inflateMenu(R.menu.menu_album_artist_detail)
            TagEditorMenuSanitiser.sanitise(toolbar.menu, albumArtist.mediaProviders)

            toolbar.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.play -> {
                        presenter.play()
                        true
                    }
                    R.id.shuffle -> {
                        presenter.shuffle()
                        true
                    }
                    R.id.albumShuffle -> {
                        presenter.shuffleAlbums()
                        true
                    }
                    R.id.queue -> {
                        presenter.addToQueue(albumArtist)
                        true
                    }
                    R.id.playNext -> {
                        presenter.playNext(albumArtist)
                        true
                    }
                    R.id.editTags -> {
                        presenter.editTags(albumArtist)
                        true
                    }
                    R.id.playlist -> {
                        playlistMenuView.createPlaylistMenu(toolbar.menu)
                        true
                    }
                    else -> {
                        playlistMenuView.handleMenuItem(menuItem, PlaylistData.AlbumArtists(albumArtist))
                    }
                }
            }
        }

        dummyImage = view.findViewById(R.id.dummyImage)
        dummyImage.transitionName = "album_artist_${albumArtist.name ?: albumArtist.friendlyArtistName}"

        imageLoader.loadArtwork(
            imageView = dummyImage,
            data = albumArtist,
            options = listOf(
                ArtworkImageLoader.Options.CircleCrop,
                ArtworkImageLoader.Options.Priority.Max
            )
        ) {
            maybeStartPostponedEnterTransition()
        }

        heroImage = view.findViewById(R.id.heroImage)
        imageLoader.loadArtwork(
            imageView = heroImage, data = albumArtist,
            options = listOf(
                ArtworkImageLoader.Options.Priority.Max,
                ArtworkImageLoader.Options.Placeholder(R.drawable.ic_placeholder_artist)
            )
        )
        if (showHeroView || !animateTransition) {
            heroImage.isVisible = true
            dummyImage.isVisible = false
        }

        adapter = RecyclerAdapter(viewLifecycleOwner.lifecycleScope)
        recyclerView = view.findViewById(R.id.recyclerView)
        recyclerView.adapter = adapter
        recyclerView.doOnPreDraw { maybeStartPostponedEnterTransition() }

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
    }

    override fun onDestroyView() {
        presenter.unbindView()
        playlistMenuPresenter.unbindView()

        super.onDestroyView()
    }

    private fun maybeStartPostponedEnterTransition() {
        postponedTransitionCounter--
        if (postponedTransitionCounter == 0) {
            startPostponedEnterTransition()
        }
    }


    // AlbumArtistDetailContract.View Implementation

    override fun setListData(albumSongsMap: Map<Album, List<Song>>) {
        val viewBinders = mutableListOf<ViewBinder>()
        if (albumSongsMap.isNotEmpty()) {
            viewBinders.add(HeaderBinder(getString(R.string.albums)))
            viewBinders.addAll(albumSongsMap.map { entry ->
                ExpandableAlbumBinder(
                    entry.key,
                    entry.value,
                    imageLoader,
                    expanded = adapter.items
                        .filterIsInstance<ExpandableAlbumBinder>()
                        .find { binder -> binder.album.groupKey == entry.key.groupKey }
                        ?.expanded
                        ?: false,
                    scope = lifecycle.coroutineScope,
                    listener = this
                )
            })
        }
        val songs = albumSongsMap.values.flatten()
        if (songs.isNotEmpty()) {
            viewBinders.add(HeaderBinder(getString(R.string.songs)))
            viewBinders.addAll(songs.map { song ->
                SongBinder(song, imageLoader, songBinderListener)
            })
        }
        adapter.update(viewBinders)
    }

    override fun showLoadError(error: Error) {
        Toast.makeText(context, error.userDescription(resources), Toast.LENGTH_LONG).show()
    }

    override fun onAddedToQueue(name: String) {
        Toast.makeText(context, Phrase.from(requireContext(), R.string.queue_item_added).put("item_name", name).format(), Toast.LENGTH_SHORT).show()
    }

    override fun setAlbumArtist(albumArtist: AlbumArtist) {
        toolbar.title = albumArtist.name ?: albumArtist.friendlyArtistName
        val albumQuantity = Phrase.fromPlural(resources, R.plurals.albumsPlural, albumArtist.albumCount)
            .put("count", albumArtist.albumCount)
            .format()
        val songQuantity = Phrase.fromPlural(resources, R.plurals.songsPlural, albumArtist.songCount)
            .put("count", albumArtist.songCount)
            .format()
        toolbar.subtitle = ListPhrase
            .from(" • ")
            .join(albumQuantity, songQuantity)
    }

    override fun showDeleteError(error: Error) {
        Toast.makeText(requireContext(), error.userDescription(resources), Toast.LENGTH_LONG).show()
    }

    override fun showTagEditor(songs: List<Song>) {
        TagEditorAlertDialog.newInstance(songs).show(childFragmentManager)
    }


    // ExpandableAlbumArtistBinder.Listener Implementation

    override fun onArtworkClicked(album: Album, viewHolder: ExpandableAlbumBinder.ViewHolder) {
        view?.findNavController()?.navigate(
            R.id.action_albumArtistDetailFragment_to_albumDetailFragment,
            AlbumDetailFragmentArgs(album).toBundle(),
            null,
            FragmentNavigatorExtras(viewHolder.imageView to viewHolder.imageView.transitionName)
        )
    }

    override fun onItemClicked(position: Int, expanded: Boolean) {
        val items = adapter.items.toMutableList()
        items[position] = (items[position] as ExpandableAlbumBinder).clone(!expanded)
        adapter.update(items)
    }

    override fun onSongClicked(song: Song, songs: List<Song>) {
        presenter.onSongClicked(song, songs)
    }

    override fun onOverflowClicked(view: View, song: Song) {
        val popupMenu = PopupMenu(requireContext(), view)
        popupMenu.inflate(R.menu.menu_popup_song)
        TagEditorMenuSanitiser.sanitise(popupMenu.menu, listOf(song.mediaProvider))

        playlistMenuView.createPlaylistMenu(popupMenu.menu)

        if (song.mediaStoreId != null) {
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

    override fun onOverflowClicked(view: View, album: Album) {
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


    // SongBinder.Listener Implementation

    private val songBinderListener = object : SongBinder.Listener {

        override fun onSongClicked(song: Song) {
            presenter.onSongClicked(song)
        }

        override fun onOverflowClicked(view: View, song: Song) {
            val popupMenu = PopupMenu(requireContext(), view)
            popupMenu.inflate(R.menu.menu_popup_song)
            TagEditorMenuSanitiser.sanitise(popupMenu.menu, listOf(song.mediaProvider))

            playlistMenuView.createPlaylistMenu(popupMenu.menu)

            if (song.mediaStoreId != null) {
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
}