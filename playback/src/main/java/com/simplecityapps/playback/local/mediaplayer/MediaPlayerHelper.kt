package com.simplecityapps.playback.local.mediaplayer

import android.media.MediaPlayer
import android.net.Uri
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.playback.Playback
import timber.log.Timber
import java.io.File

class MediaPlayerHelper {

    var mediaPlayer: MediaPlayer? = null

    var callback: Playback.Callback? = null

    var tag: String? = "MediaPlayerHelper"

    var isPrepared: Boolean = false
        private set

    var seekPosition: Int = 0

    private var isPreparing: Boolean = false

    private var playOnPrepared: Boolean = false

    lateinit var currentSong: Song

    fun load(song: Song, seekPosition: Int = 0, playOnPrepared: Boolean) {

        Timber.v("$tag load() song: ${song.path}, playOnPrepared: $playOnPrepared")

        currentSong = song

        this.playOnPrepared = playOnPrepared

        this.seekPosition = seekPosition

        isPrepared = false

        if (mediaPlayer == null) {
            mediaPlayer = MediaPlayer()
        } else {
            Timber.v("$tag MediaPlayer.reset()")
            pause()
            mediaPlayer!!.reset()
        }

        mediaPlayer!!.setOnCompletionListener(onCompletionListener)
        mediaPlayer!!.setOnErrorListener(onErrorListener)
        mediaPlayer!!.setOnPreparedListener(onPreparedListener)
        mediaPlayer!!.setDataSource(Uri.fromFile(File(song.path)).toString())

        isPreparing = true
        Timber.v("$tag MediaPlayer.prepareAsync()")
        mediaPlayer!!.prepareAsync()
    }

    fun play() {
        when {
            isPrepared -> {
                Timber.v("$tag play() called. Attempting to play.")
                mediaPlayer?.let { mediaPlayer ->
                    if (!isPlaying()) {
                        mediaPlayer.start()
                        callback?.onPlaystateChanged(true)
                    }
                } ?: run {
                    Timber.v("$tag play() called, Media player null")
                }
                playOnPrepared = false
            }
            isPreparing -> {
                Timber.v("$tag play() called. preparing..")
                playOnPrepared = true
            }
            else -> Timber.v("$tag play() called. Not prepared or preparing...")
        }
    }

    fun isPlaying(): Boolean {
        return isPrepared && mediaPlayer?.isPlaying ?: false
    }

    fun pause() {
        Timber.v("$tag pause()")
        if (isPlaying()) {
            mediaPlayer?.pause()
        }
        if (isPreparing) {
            Timber.v("pause() called while preparing. Cancelling playOnPrepared.")
            playOnPrepared = false
        }
        callback?.onPlaystateChanged(false)
    }

    fun seek(position: Int) {
        Timber.v("seekTo() $position")
        if (isPrepared) {
            mediaPlayer?.seekTo(position)
        }
    }

    fun getPosition(): Int? {
        if (isPrepared) {
            return mediaPlayer?.currentPosition
        }

        return null
    }

    fun getDuration(): Int? {
        if (isPrepared) {
            return mediaPlayer?.duration
        }

        return null
    }

    var volume: Float = 1.0f
        set(value) {
            field = value
            if (isPrepared) {
                mediaPlayer?.setVolume(volume, volume)
            }
        }

    fun setNextMediaPlayer(nextMediaPlayer: MediaPlayer?) {
        Timber.v("$tag setNextMediaPlayer()")
        if (isPrepared) {
            mediaPlayer?.setNextMediaPlayer(nextMediaPlayer)
        } else {
            Timber.v("$tag setNextMediaPlayer() current MediaPlayer not prepared")
        }
    }

    private val onPreparedListener = MediaPlayer.OnPreparedListener {
        Timber.v("$tag onPrepared()")

        isPreparing = false
        isPrepared = true

        if (seekPosition != 0) {
            seek(seekPosition)
            seekPosition = 0
        }

        volume = volume

        if (playOnPrepared) {
            play()
        }

        callback?.onPlaybackPrepared()
    }

    private val onErrorListener = object : MediaPlayer.OnErrorListener {
        override fun onError(mp: MediaPlayer?, what: Int, extra: Int): Boolean {
            Timber.v("$tag onError()")

            isPreparing = false
            isPrepared = false
            mediaPlayer?.release()
            mediaPlayer = null

            return false
        }
    }

    private val onCompletionListener = MediaPlayer.OnCompletionListener {
        Timber.v("$tag onCompletion()")
        callback?.onPlaybackComplete(currentSong)
    }
}