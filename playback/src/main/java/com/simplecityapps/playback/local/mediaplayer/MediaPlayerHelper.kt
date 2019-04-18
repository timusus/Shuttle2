package com.simplecityapps.playback.local.mediaplayer

import android.media.MediaPlayer
import android.net.Uri
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.playback.Playback
import timber.log.Timber
import java.io.File
import java.io.IOException

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

    /**
     * @param completion
     * - Failure called with an [Error] value if the [Song] cannot be loaded.
     * - Success called with a null value if the [Song] is successfully loaded and the MediaPlayer has begun
     */
    fun load(song: Song, seekPosition: Int = 0, playOnPrepared: Boolean, completion: ((Result<Any?>) -> Unit)?) {

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

        mediaPlayer!!.setOnErrorListener { _, what, extra ->
            Timber.e("$tag error ($what, $extra)")
            release()
            completion?.invoke(Result.failure(Error("Media player error occurred. ($what, $extra). Path: ${currentSong.path}")))
            true
        }

        mediaPlayer!!.setOnPreparedListener {
            Timber.v("$tag onPrepared()")

            isPreparing = false
            isPrepared = true

            if (seekPosition != 0) {
                seek(seekPosition)
                this.seekPosition = 0
            }

            volume = volume

            if (playOnPrepared) {
                play()
            }

            completion?.invoke(Result.success(null))
        }

        try {
            mediaPlayer!!.setDataSource(Uri.fromFile(File(song.path)).toString())
        } catch (exception: IOException) {
            Timber.e(exception, "Failed to load ${song.path}")
            completion?.invoke(Result.failure(Error("$tag MediaPlayer.setData() failed", exception)))
            return
        }

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
            callback?.onPlaystateChanged(false)
        }
        if (isPreparing) {
            Timber.v("pause() called while preparing. Cancelling playOnPrepared.")
            playOnPrepared = false
        }
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

    fun release() {
        Timber.v("$tag releasing MediaPlayer")

        callback = null

        isPreparing = false
        isPrepared = false
        playOnPrepared = false

        mediaPlayer?.release()
        mediaPlayer = null
    }

    private val onCompletionListener = MediaPlayer.OnCompletionListener {
        Timber.v("$tag onCompletion()")
        callback?.onPlaybackComplete(currentSong)
    }
}