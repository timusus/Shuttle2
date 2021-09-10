package com.simplecityapps.shuttle.ui.screens.playback.mini

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.autoCleared
import com.simplecityapps.shuttle.ui.common.phrase.joinSafely
import com.simplecityapps.shuttle.ui.common.utils.dp
import com.simplecityapps.shuttle.ui.common.view.PlayStateView
import com.simplecityapps.shuttle.ui.common.view.ProgressView
import com.simplecityapps.shuttle.ui.common.view.SkipButton
import com.squareup.phrase.ListPhrase
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MiniPlaybackFragment : Fragment(), MiniPlayerContract.View {

    @Inject
    lateinit var presenter: MiniPlayerPresenter

    @Inject
    lateinit var imageLoader: ArtworkImageLoader

    private var imageView: ImageView by autoCleared()
    private var playStateView: PlayStateView by autoCleared()
    private var skipNextButton: SkipButton by autoCleared()
    private var titleTextView: TextView by autoCleared()
    private var subtitleTextView: TextView by autoCleared()
    private var progressView: ProgressView by autoCleared()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_mini_playback, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        imageView = view.findViewById(R.id.imageView)
        playStateView = view.findViewById(R.id.playPauseView)
        skipNextButton = view.findViewById(R.id.skipNextButton)
        titleTextView = view.findViewById(R.id.titleTextView)
        subtitleTextView = view.findViewById(R.id.subtitleTextView)
        progressView = view.findViewById(R.id.progressView)

        presenter.bindView(this)

        playStateView.setOnClickListener { presenter.togglePlayback() }
        skipNextButton.setOnClickListener { presenter.skipToNext() }
        skipNextButton.listener = object : SkipButton.OnSeekListener {
            override fun onSeek(seekAmount: Int) {
                presenter.seekForward(seekAmount)
            }
        }
    }

    override fun onDestroyView() {
        presenter.unbindView()

        super.onDestroyView()
    }


    // MiniPlayerContract.View Implementation

    override fun setPlaybackState(playbackState: PlaybackState) {
        playStateView.playbackState = playbackState
    }

    override fun setCurrentSong(song: com.simplecityapps.shuttle.model.Song?) {
        song?.let {
            titleTextView.text = song.name ?: getString(R.string.unknown)
            subtitleTextView.text = ListPhrase.from(" • ")
                .joinSafely(
                    items = listOf(song.friendlyArtistName ?: song.albumArtist, song.album),
                    defaultValue = getString(R.string.unknown)
                )
            imageLoader.loadArtwork(
                imageView = imageView,
                data = song,
                options = listOf(ArtworkImageLoader.Options.RoundedCorners(8.dp)),
            )
        }
    }

    override fun setProgress(position: Int, duration: Int) {
        progressView.setProgress((position.toFloat() / duration))
    }
}