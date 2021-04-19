package com.simplecityapps.shuttle.ui.screens.playback

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.widget.Toolbar
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import com.google.android.gms.cast.framework.CastButtonFactory
import com.simplecityapps.adapter.RecyclerAdapter
import com.simplecityapps.adapter.RecyclerListener
import com.simplecityapps.mediaprovider.model.Album
import com.simplecityapps.mediaprovider.model.AlbumArtist
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.mediaprovider.model.friendlyArtistName
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.queue.QueueItem
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.dagger.Injectable
import com.simplecityapps.shuttle.ui.common.TagEditorMenuSanitiser
import com.simplecityapps.shuttle.ui.common.autoCleared
import com.simplecityapps.shuttle.ui.common.dialog.TagEditorAlertDialog
import com.simplecityapps.shuttle.ui.common.recyclerview.SnapOnScrollListener
import com.simplecityapps.shuttle.ui.common.recyclerview.SpacesItemDecoration
import com.simplecityapps.shuttle.ui.common.recyclerview.attachSnapHelperWithListener
import com.simplecityapps.shuttle.ui.common.utils.toHms
import com.simplecityapps.shuttle.ui.common.view.*
import com.simplecityapps.shuttle.ui.common.view.multisheet.MultiSheetView
import com.simplecityapps.shuttle.ui.common.view.multisheet.findParentMultiSheetView
import com.simplecityapps.shuttle.ui.screens.library.albumartists.detail.AlbumArtistDetailFragmentArgs
import com.simplecityapps.shuttle.ui.screens.library.albums.detail.AlbumDetailFragmentArgs
import com.simplecityapps.shuttle.ui.screens.lyrics.QuickLyricManager
import com.simplecityapps.shuttle.ui.screens.sleeptimer.SleepTimerDialogFragment
import com.simplecityapps.shuttle.ui.screens.songinfo.SongInfoDialogFragment
import javax.inject.Inject
import kotlin.math.abs

class PlaybackFragment :
    Fragment(),
    Injectable,
    PlaybackContract.View,
    SeekBar.OnSeekBarChangeListener {

    @Inject
    lateinit var presenter: PlaybackPresenter

    @Inject
    lateinit var imageLoader: ArtworkImageLoader

    @Inject
    lateinit var queueManager: QueueManager

    private var recyclerView: RecyclerView by autoCleared()

    private var adapter: RecyclerAdapter by autoCleared()

    private var playStateView: PlayStateView by autoCleared()
    private var skipNextButton: ImageButton by autoCleared()
    private var skipPrevButton: ImageButton by autoCleared()
    private var shuffleButton: ShuffleButton by autoCleared()
    private var repeatButton: RepeatButton by autoCleared()
    private var seekBackwardButton: SeekButton by autoCleared()
    private var seekForwardButton: SeekButton by autoCleared()
    private var seekBar: SeekBar by autoCleared()
    private var titleTextView: TextView by autoCleared()
    private var artistTextView: TextView by autoCleared()
    private var albumTextView: TextView by autoCleared()
    private var currentTimeTextView: TextView by autoCleared()
    private var durationTextView: TextView by autoCleared()
    private var toolbar: Toolbar by autoCleared()
    private var favoriteButton: FavoriteButton by autoCleared()
    private var lyricsView: View by autoCleared()
    private var lyricsText: TextView by autoCleared()
    private var closeLyricsButton: Button by autoCleared()
    private var quickLyricButton: Button by autoCleared()

    var position: Int = 0
    var duration: Int = 0


    // Lifecycle

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_playback, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.recyclerView)

        toolbar = view.findViewById(R.id.toolbar)

        skipNextButton = view.findViewById(R.id.skipNextButton)
        skipPrevButton = view.findViewById(R.id.skipPrevButton)
        playStateView = view.findViewById(R.id.playPauseButton)
        shuffleButton = view.findViewById(R.id.shuffleButton)
        repeatButton = view.findViewById(R.id.repeatButton)
        seekBackwardButton = view.findViewById(R.id.seekBackwardButton)
        seekForwardButton = view.findViewById(R.id.seekForwardButton)
        seekBar = view.findViewById(R.id.seekBar)
        titleTextView = view.findViewById(R.id.titleTextView)
        artistTextView = view.findViewById(R.id.artistTextView)
        albumTextView = view.findViewById(R.id.albumTextView)
        currentTimeTextView = view.findViewById(R.id.currentTimeTextView)
        durationTextView = view.findViewById(R.id.durationTextView)

        adapter = RecyclerAdapter(viewLifecycleOwner.lifecycleScope)

        playStateView.setOnClickListener {
            presenter.togglePlayback()
        }
        shuffleButton.setOnClickListener { presenter.toggleShuffle() }
        repeatButton.setOnClickListener { presenter.toggleRepeat() }
        skipNextButton.setOnClickListener { presenter.skipNext() }
        skipPrevButton.setOnClickListener { presenter.skipPrev() }
        seekBackwardButton.listener = object : SeekButton.OnSeekListener {
            override fun onSeek(seekAmount: Int) {
                presenter.seekBackward(seekAmount)
            }
        }
        seekForwardButton.listener = object : SeekButton.OnSeekListener {
            override fun onSeek(seekAmount: Int) {
                presenter.seekForward(seekAmount)
            }
        }
        seekBar.setOnSeekBarChangeListener(this)
        artistTextView.setOnClickListener {
            presenter.goToArtist()
        }
        albumTextView.setOnClickListener {
            presenter.goToAlbum()
        }

        lyricsView = view.findViewById(R.id.lyricsView)
        lyricsText = view.findViewById(R.id.lyricsTextView)
        closeLyricsButton = view.findViewById(R.id.closeLyricsButton)
        closeLyricsButton.setOnClickListener { lyricsView.fadeOut() }
        quickLyricButton = view.findViewById(R.id.quickLyricButton)
        quickLyricButton.setOnClickListener { presenter.launchQuickLyric() }

        recyclerView.adapter = adapter
        recyclerView.setRecyclerListener(RecyclerListener())

        val snapHelper = PagerSnapHelper()
        snapHelper.attachToRecyclerView(recyclerView)
        recyclerView.attachSnapHelperWithListener(snapHelper, SnapOnScrollListener.Behavior.NOTIFY_ON_SCROLL_STATE_IDLE) { position ->
            presenter.skipTo(position)
        }

        recyclerView.addItemDecoration(SpacesItemDecoration(8))

        toolbar.inflateMenu(R.menu.menu_playback)
        queueManager.getCurrentItem()?.song?.let { song -> TagEditorMenuSanitiser.sanitise(toolbar.menu, listOf(song.mediaProvider)) }

        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.sleepTimer -> {
                    presenter.sleepTimerClicked()
                    true
                }
                R.id.lyrics -> {
                    presenter.showOrLaunchLyrics()
                    true
                }
                R.id.songInfo -> {
                    presenter.showSongInfo()
                    true
                }
                R.id.editTags -> {
                    queueManager.getCurrentItem()?.song?.let { song ->
                        TagEditorAlertDialog.newInstance(listOf(song)).show(childFragmentManager)
                    }
                    true
                }
                else -> false
            }
        }
        favoriteButton = toolbar.menu.findItem(R.id.favorite).actionView.findViewById(R.id.favoritesButton)
        favoriteButton.setOnClickListener {
            favoriteButton.toggle()
            presenter.setFavorite(favoriteButton.isChecked)
        }

        CastButtonFactory.setUpMediaRouteButton(requireContext(), toolbar.menu, R.id.media_route_menu_item)

        presenter.bindView(this)
    }

    override fun onDestroyView() {

        presenter.unbindView()
        super.onDestroyView()
    }


    // PlaybackContract.View

    override fun setPlaybackState(playbackState: PlaybackState) {
        playStateView.playbackState = playbackState
    }

    override fun setShuffleMode(shuffleMode: QueueManager.ShuffleMode) {
        shuffleButton.shuffleMode = shuffleMode
    }

    override fun setRepeatMode(repeatMode: QueueManager.RepeatMode) {
        repeatButton.repeatMode = repeatMode
    }

    override fun setCurrentSong(song: Song?) {
        song?.let { song ->
            titleTextView.text = song.name
            artistTextView.text = song.friendlyArtistName
            albumTextView.text = song.album

            when (song.type) {
                Song.Type.Audiobook, Song.Type.Podcast -> {
                    seekBackwardButton.isVisible = true
                    seekForwardButton.isVisible = true
                    skipPrevButton.isVisible = false
                    skipNextButton.isVisible = false
                }
                else -> {
                    seekBackwardButton.isVisible = false
                    seekForwardButton.isVisible = false
                    skipPrevButton.isVisible = true
                    skipNextButton.isVisible = true
                }
            }

            if (lyricsView.isVisible) {
                lyricsText.text = song.lyrics
                if (song.lyrics == null) {
                    lyricsView.fadeOut()
                }
            }

            toolbar.menu.findItem(R.id.lyrics)?.let { menuItem ->
                menuItem.title = song.lyrics?.let { "Lyrics" } ?: "QuickLyric"
            }
        }
    }

    override fun setQueuePosition(position: Int?, total: Int, smoothScroll: Boolean) {
        position?.let {
            if (smoothScroll) {
                recyclerView.smoothScrollToPosition(position)
            } else {
                recyclerView.scrollToPosition(position)
            }
        }
    }

    override fun setQueue(queue: List<QueueItem>, position: Int?) {
        adapter.update(
            queue.map { queueItem ->
                ArtworkBinder(queueItem, imageLoader)
            },
            completion = {
                position?.let { position ->
                    recyclerView.scrollToPosition(position)
                }
            }
        )
    }

    override fun setProgress(position: Int, duration: Int) {
        if (!isSeeking) {
            if (position == 0 || abs(position - this.position) >= 1000) {
                currentTimeTextView.text = position.toHms()
                this.position = position
            }
            if (abs(duration - this.duration) >= 1000) {
                durationTextView.text = duration.toHms("--:--")
                this.duration = duration
            }
            seekBar.progress = ((position.toFloat() / duration) * 1000).toInt()
        }
    }

    override fun presentSleepTimer() {
        SleepTimerDialogFragment().show(childFragmentManager)
    }

    override fun setIsFavorite(isFavorite: Boolean) {
        favoriteButton.isChecked = isFavorite
    }

    override fun goToAlbum(album: Album) {
        if (findNavController().currentDestination?.id != R.id.libraryFragment) {
            findNavController().navigate(R.id.libraryFragment)
        }
        findNavController().navigate(R.id.albumDetailFragment, AlbumDetailFragmentArgs(album, animateTransition = false).toBundle())
        view?.postDelayed({
            view?.findParentMultiSheetView()?.goToSheet(MultiSheetView.Sheet.NONE)
        }, 200)
    }

    override fun goToArtist(artist: AlbumArtist) {
        if (findNavController().currentDestination?.id != R.id.libraryFragment) {
            findNavController().navigate(R.id.libraryFragment)
        }
        findNavController().navigate(R.id.albumArtistDetailFragment, AlbumArtistDetailFragmentArgs(artist, animateTransition = false).toBundle())
        view?.postDelayed({
            view?.findParentMultiSheetView()?.goToSheet(MultiSheetView.Sheet.NONE)
        }, 200)
    }

    override fun launchQuickLyric(artistName: String, songName: String) {
        val intent = QuickLyricManager.buildLyricsIntent(artistName, songName)
        if (intent.resolveActivity(requireContext().packageManager) != null) {
            requireContext().startActivity(intent)
        }
    }

    override fun getQuickLyric() {
        requireContext().startActivity(QuickLyricManager.quickLyricIntent)
    }

    override fun showQuickLyricUnavailable() {
        Toast.makeText(requireContext(), "Failed to download QuickLyric", Toast.LENGTH_LONG).show()
    }

    override fun showSongInfoDialog(song: Song) {
        SongInfoDialogFragment.newInstance(song).show(childFragmentManager)
    }

    override fun displayLyrics(lyrics: String) {
        lyricsText.text = lyrics
        lyricsView.fadeIn()
    }


    // SeekBar.OnSeekBarChangeListener Implementation

    private var isSeeking = false
    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
        if (fromUser) {
            // A little hack - temporarily allow us to update the progress text.
            isSeeking = false
            presenter.updateProgress(seekBar.progress / 1000f)
            isSeeking = true
        }
    }

    override fun onStartTrackingTouch(seekBar: SeekBar) {
        isSeeking = true
    }

    override fun onStopTrackingTouch(seekBar: SeekBar) {
        presenter.seek(seekBar.progress / 1000f)
        isSeeking = false
    }
}