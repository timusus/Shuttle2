package com.simplecityapps.shuttle.ui.screens.playback.mini

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.dagger.Injectable
import kotlinx.android.synthetic.main.fragment_mini_player.*
import javax.inject.Inject

class MiniPlaybackFragment : Fragment(), Injectable, MiniPlayerContract.View {

    @Inject lateinit var presenter: MiniPlayerPresenter

    @Inject lateinit var imageLoader: ArtworkImageLoader

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_mini_player, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        presenter.bindView(this)

        playPauseButton.setOnClickListener { presenter.togglePlayback() }
        skipNextButton.setOnClickListener { presenter.skipToNext() }
    }

    override fun onDestroyView() {
        presenter.unbindView()

        imageLoader.clear(imageView)

        super.onDestroyView()
    }


    // MiniPlayerContract.View Implementation

    override fun setPlayState(isPlaying: Boolean) {
        when {
            isPlaying -> playPauseButton.setImageDrawable(ContextCompat.getDrawable(context!!, R.drawable.ic_pause_black_24dp))
            else -> playPauseButton.setImageDrawable(ContextCompat.getDrawable(context!!, R.drawable.ic_play_arrow_black_24dp))
        }
    }

    override fun setCurrentSong(song: Song?) {
        song?.let {
            titleTextView.text = song.name
            subtitleTextView.text = "${song.albumArtistName} • ${song.albumName}"
            imageLoader.loadArtwork(imageView, song, ArtworkImageLoader.Options.RoundedCorners(8))
        }
    }

    override fun setProgress(position: Int, duration: Int) {
        progressBar.setProgress((position.toFloat() / duration))
    }
}