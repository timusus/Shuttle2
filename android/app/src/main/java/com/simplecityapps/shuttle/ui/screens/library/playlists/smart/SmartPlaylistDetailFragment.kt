package com.simplecityapps.shuttle.ui.screens.library.playlists.smart

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.Toolbar
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.recyclerview.widget.RecyclerView
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import com.simplecityapps.adapter.RecyclerAdapter
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.model.SmartPlaylist
import com.simplecityapps.shuttle.query.SongQuery
import com.simplecityapps.shuttle.ui.common.autoCleared
import com.simplecityapps.shuttle.ui.common.dialog.TagEditorAlertDialog
import com.simplecityapps.shuttle.ui.common.dialog.showDeleteDialog
import com.simplecityapps.shuttle.ui.common.dialog.showExcludeDialog
import com.simplecityapps.shuttle.ui.common.error.userDescription
import com.simplecityapps.shuttle.ui.screens.library.songs.SongBinder
import com.simplecityapps.shuttle.ui.screens.playlistmenu.CreatePlaylistDialogFragment
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistMenuPresenter
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistMenuView
import com.simplecityapps.shuttle.ui.screens.songinfo.SongInfoDialogFragment
import com.squareup.phrase.Phrase
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SmartPlaylistDetailFragment :
    Fragment(),
    SmartPlaylistDetailContract.View,
    CreatePlaylistDialogFragment.Listener {
    @Inject
    lateinit var presenterFactory: SmartPlaylistDetailPresenter.Factory

    @Inject
    lateinit var playlistMenuPresenter: PlaylistMenuPresenter

    private var recyclerView: RecyclerView by autoCleared()

    private var toolbar: Toolbar by autoCleared()

    @Inject
    lateinit var imageLoader: ArtworkImageLoader

    private var heroImageView: ImageView by autoCleared()

    private var adapter: RecyclerAdapter by autoCleared()

    private lateinit var presenter: SmartPlaylistDetailPresenter

    private lateinit var playlist: SmartPlaylist

    private lateinit var playlistMenuView: PlaylistMenuView

    // Lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        playlist = SmartPlaylistDetailFragmentArgs.fromBundle(requireArguments()).playlist
        presenter = presenterFactory.create(playlist)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_smart_playlist_detail, container, false)
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        playlistMenuView = PlaylistMenuView(requireContext(), playlistMenuPresenter, childFragmentManager)

        adapter = RecyclerAdapter(viewLifecycleOwner.lifecycleScope)

        toolbar = view.findViewById(R.id.toolbar)
        toolbar.let { toolbar ->
            toolbar.setNavigationOnClickListener {
                NavHostFragment.findNavController(this).popBackStack()
            }
            toolbar.inflateMenu(R.menu.menu_playlist_detail)
            toolbar.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.shuffle -> {
                        presenter.shuffle()
                        true
                    }
                    R.id.queue -> {
                        presenter.addToQueue(playlist)
                        true
                    }
                    else -> {
                        false
                    }
                }
            }
        }

        toolbar.setTitle(playlist.nameResId)

        recyclerView = view.findViewById(R.id.recyclerView)
        recyclerView.adapter = adapter

        heroImageView = view.findViewById(R.id.heroImage)

        presenter.bindView(this)
        playlistMenuPresenter.bindView(playlistMenuView)
    }

    override fun onResume() {
        super.onResume()

        presenter.loadData()
    }

    override fun onDestroyView() {
        presenter.unbindView()
        playlistMenuPresenter.unbindView()

        super.onDestroyView()
    }

    // PlaylistDetailContract.View Implementation

    override fun setData(songs: List<com.simplecityapps.shuttle.model.Song>) {
        if (songs.isNotEmpty()) {
            if (heroImageView.drawable == null) {
                imageLoader.loadArtwork(
                    heroImageView,
                    songs.random(),
                    listOf(
                        ArtworkImageLoader.Options.Placeholder(ResourcesCompat.getDrawable(resources, com.simplecityapps.core.R.drawable.ic_placeholder_playlist, requireContext().theme)!!),
                        ArtworkImageLoader.Options.Priority.Max
                    )
                )
            }
        } else {
            heroImageView.setImageResource(com.simplecityapps.core.R.drawable.ic_placeholder_playlist)
        }

        adapter.update(
            songs.map { song ->
                SongBinder(song, imageLoader, songBinderListener, showPlayCountBadge = playlist.songQuery is SongQuery.PlayCount)
            }
        )
    }

    override fun onAddedToQueue(song: com.simplecityapps.shuttle.model.Song) {
        Toast.makeText(context, Phrase.from(requireContext(), R.string.queue_item_added).put("item_name", song.name).format(), Toast.LENGTH_SHORT).show()
    }

    override fun onAddedToQueue(playlist: SmartPlaylist) {
        Toast.makeText(context, Phrase.from(requireContext(), R.string.queue_item_added).put("item_name", getString(playlist.nameResId)).format(), Toast.LENGTH_SHORT).show()
    }

    override fun showLoadError(error: Error) {
        Toast.makeText(context, error.userDescription(resources), Toast.LENGTH_LONG).show()
    }

    override fun showDeleteError(error: Error) {
        Toast.makeText(requireContext(), error.userDescription(resources), Toast.LENGTH_LONG).show()
    }

    // SongBinder.Listener Implementation

    private val songBinderListener =
        object : SongBinder.Listener {
            override fun onSongClicked(song: com.simplecityapps.shuttle.model.Song) {
                presenter.onSongClicked(song)
            }

            override fun onOverflowClicked(
                view: View,
                song: com.simplecityapps.shuttle.model.Song
            ) {
                val popupMenu = PopupMenu(requireContext(), view)
                popupMenu.inflate(R.menu.menu_popup_song)

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
                                TagEditorAlertDialog.newInstance(listOf(song)).show(childFragmentManager)
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
