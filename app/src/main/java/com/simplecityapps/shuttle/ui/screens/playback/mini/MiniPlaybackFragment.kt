package com.simplecityapps.shuttle.ui.screens.playback.mini

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import au.com.simplecityapps.shuttle.imageloading.glide.GlideImageLoader
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.dagger.Injectable
import com.simplecityapps.shuttle.ui.common.autoCleared
import com.simplecityapps.shuttle.ui.common.view.ProgressView
import javax.inject.Inject

class MiniPlaybackFragment : Fragment(), Injectable, MiniPlayerContract.View {

    @Inject lateinit var presenter: MiniPlayerPresenter

    private var imageLoader: ArtworkImageLoader by autoCleared()

    private var imageView: ImageView by autoCleared()
    private var playPauseButton: ImageButton by autoCleared()
    private var skipNextButton: ImageButton by autoCleared()
    private var titleTextView: TextView by autoCleared()
    private var subtitleTextView: TextView by autoCleared()
    private var progressBar: ProgressView by autoCleared()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_mini_playback, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        imageLoader = GlideImageLoader(this)

        imageView = view.findViewById(R.id.imageView)
        playPauseButton = view.findViewById(R.id.playPauseButton)
        skipNextButton = view.findViewById(R.id.skipNextButton)
        titleTextView = view.findViewById(R.id.titleTextView)
        subtitleTextView = view.findViewById(R.id.subtitleTextView)
        progressBar = view.findViewById(R.id.progressBar)

        presenter.bindView(this)

        playPauseButton.setOnClickListener { presenter.togglePlayback() }
        skipNextButton.setOnClickListener { presenter.skipToNext() }
    }

    override fun onDestroyView() {
        presenter.unbindView()

        super.onDestroyView()
    }


    // MiniPlayerContract.View Implementation

    override fun setPlayState(isPlaying: Boolean) {
        when {
            isPlaying -> playPauseButton.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.ic_pause_black_24dp))
            else -> playPauseButton.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.ic_play_arrow_black_24dp))
        }
    }

    override fun setCurrentSong(song: Song?) {
        song?.let {
            titleTextView.text = song.name
            subtitleTextView.text = "${song.albumArtistName} • ${song.albumName}"
            imageLoader.loadArtwork(imageView, song, ArtworkImageLoader.Options.RoundedCorners(16), completionHandler = null)
        }
    }

    override fun setProgress(position: Int, duration: Int) {
        progressBar.setProgress((position.toFloat() / duration))
    }
}