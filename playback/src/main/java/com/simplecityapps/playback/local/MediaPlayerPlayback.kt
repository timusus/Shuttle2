package com.simplecityapps.playback.local

import android.media.MediaPlayer
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.playback.Playback
import timber.log.Timber

class MediaPlayerPlayback : Playback {

    private var mediaPlayer: MediaPlayer? = null

    private var isPrepared: Boolean = false

    private var isPreparing: Boolean = false

    private var playOnPrepared: Boolean = false

    private var currentSong: Song? = null

    override var callback: Playback.Callback? = null

    override fun load(song: Song, playOnPrepared: Boolean) {

        Timber.i("load() called. Song: ${song.path}")

        if (song == currentSong && (isPrepared || isPlaying())) {
            if (playOnPrepared) {
                Timber.d("load() called. Song is already playing or prepared")
                play()
                return
            }
        }

        // Reset if not null, else create a new instance
        mediaPlayer = mediaPlayer?.apply { reset() } ?: MediaPlayer()

        isPrepared = false

        mediaPlayer?.let { mediaPlayer ->

            currentSong = song

            mediaPlayer.setOnCompletionListener(onCompletionListener)
            mediaPlayer.setOnErrorListener(onErrorListener)
            mediaPlayer.setOnPreparedListener(onPreparedListener)
            mediaPlayer.setDataSource(song.path)

            this.playOnPrepared = playOnPrepared
            this.isPreparing = true
            mediaPlayer.prepareAsync()
        }
    }

    override fun play() {
        if (isPrepared) {
            Timber.d("play() called. Attempting to play.")
            mediaPlayer?.let { mediaPlayer ->
                if (!isPlaying()) {
                    mediaPlayer.start()
                    callback?.onPlaystateChanged(true)
                }
            } ?: run {
                Timber.e("play() called, Media player null")
            }
            playOnPrepared = false
        } else if (isPreparing) {
            Timber.d("play() called. preparing..")
            playOnPrepared = true
        } else {
            Timber.d("play() called. Not prepared or preparing...")
        }
    }

    override fun pause() {
        if (isPlaying()) {
            mediaPlayer?.pause()
            callback?.onPlaystateChanged(false)
        }
    }

    override fun isPlaying(): Boolean {
        return isPrepared && mediaPlayer?.isPlaying ?: false
    }

    private val onCompletionListener = object : MediaPlayer.OnCompletionListener {
        override fun onCompletion(mp: MediaPlayer?) {
            Timber.d("MediaPlayer onCompletion()")
        }
    }

    private val onErrorListener = object : MediaPlayer.OnErrorListener {
        override fun onError(mp: MediaPlayer?, what: Int, extra: Int): Boolean {
            Timber.d("MediaPlayer onError()")

            isPreparing = false
            isPrepared = false
            mediaPlayer?.release()
            mediaPlayer = null

            return false
        }
    }

    private val onPreparedListener = object : MediaPlayer.OnPreparedListener {
        override fun onPrepared(mp: MediaPlayer?) {

            isPreparing = false
            isPrepared = true

            Timber.d("MediaPlayer onPrepared()")

            if (playOnPrepared) {
                play()
            }
        }
    }
}