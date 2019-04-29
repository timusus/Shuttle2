package com.simplecityapps.shuttle.ui.screens.playback

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.PagerSnapHelper
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import com.simplecityapps.adapter.RecyclerAdapter
import com.simplecityapps.adapter.RecyclerListener
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.playback.queue.QueueItem
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.dagger.Injectable
import com.simplecityapps.shuttle.ui.common.recyclerview.SnapOnScrollListener
import com.simplecityapps.shuttle.ui.common.recyclerview.SpacesItemDecoration
import com.simplecityapps.shuttle.ui.common.recyclerview.attachSnapHelperWithListener
import com.simplecityapps.shuttle.ui.common.toHms
import com.simplecityapps.shuttle.ui.common.view.SeekButton
import com.simplecityapps.shuttle.ui.screens.sleeptimer.SleepTimerDialogFragment
import kotlinx.android.synthetic.main.fragment_playback.*
import javax.inject.Inject

class PlaybackFragment :
    Fragment(),
    Injectable,
    PlaybackContract.View,
    SeekBar.OnSeekBarChangeListener {

    @Inject lateinit var presenter: PlaybackPresenter

    @Inject lateinit var imageLoader: ArtworkImageLoader

    private val adapter = RecyclerAdapter()


    // Lifecycle

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_playback, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        presenter.bindView(this)

        playPauseButton.setOnClickListener { presenter.togglePlayback() }
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

        recyclerView.adapter = adapter
        recyclerView.setRecyclerListener(RecyclerListener())

        val snapHelper = PagerSnapHelper()
        snapHelper.attachToRecyclerView(recyclerView)
        recyclerView.attachSnapHelperWithListener(snapHelper, SnapOnScrollListener.Behavior.NOTIFY_ON_SCROLL_STATE_IDLE) { position ->
            presenter.skipTo(position)
        }

        recyclerView.addItemDecoration(SpacesItemDecoration(8))

        playPauseButton.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context!!, R.color.colorPrimary))

        toolbar.inflateMenu(R.menu.playback_fragment_menu)
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.sleepTimer -> {
                    presenter.sleepTimerClicked()
                    true
                }
                else -> false
            }
        }
    }

    override fun onDestroyView() {
        presenter.unbindView()

        super.onDestroyView()
    }


    // PlaybackContract.View

    override fun setPlayState(isPlaying: Boolean) {
        when {
            isPlaying -> playPauseButton.setImageDrawable(ContextCompat.getDrawable(context!!, R.drawable.ic_pause_black_24dp))
            else -> playPauseButton.setImageDrawable(ContextCompat.getDrawable(context!!, R.drawable.ic_play_arrow_black_24dp))
        }
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
            subtitleTextView.text = "${song.albumArtistName} • ${song.albumName}"

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
        }
    }

    override fun setQueuePosition(position: Int?, total: Int, smoothScroll: Boolean) {
        position?.let { position ->
            if (smoothScroll) {
                recyclerView.smoothScrollToPosition(position)
            } else {
                recyclerView.scrollToPosition(position)
            }
        }
    }

    override fun setQueue(queue: List<QueueItem>, position: Int?) {
        adapter.setData(
            queue.map { ArtworkBinder(it.song, imageLoader) },
            completion = {
                position?.let { position ->
                    recyclerView?.scrollToPosition(position)
                }
            },
            animateChanges = false
        )
    }

    override fun setProgress(position: Int, duration: Int) {
        currentTimeTextView.text = position.toHms()
        durationTextView.text = duration.toHms()
        seekBar.progress = ((position.toFloat() / duration) * 1000).toInt()
    }

    override fun presentSleepTimer() {
        SleepTimerDialogFragment().show(childFragmentManager)
    }


    // SeekBar.OnSeekBarChangeListener Implementation

    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {

    }

    override fun onStartTrackingTouch(seekBar: SeekBar) {

    }

    override fun onStopTrackingTouch(seekBar: SeekBar) {
        presenter.seek(seekBar.progress / 1000f)
    }


    // Static

    companion object {
        fun newInstance() = PlaybackFragment()
    }
}