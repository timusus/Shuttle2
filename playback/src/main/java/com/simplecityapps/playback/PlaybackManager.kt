package com.simplecityapps.playback

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.support.v4.media.session.MediaSessionCompat
import androidx.core.content.ContextCompat
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.playback.queue.QueueManager
import timber.log.Timber

class PlaybackManager(
    private val context: Context,
    private val queueManager: QueueManager,
    private val playback: Playback
) : Playback.Callback {

    interface ProgressCallback {

        fun onPregressChanged(position: Int, total: Int)
    }

    internal val mediaSession: MediaSessionCompat by lazy {
        MediaSessionCompat(context, "ShuttleMediaSession")
    }

    private var handler: PlaybackManager.ProgressHandler = PlaybackManager.ProgressHandler()

    private var callbacks: MutableList<Playback.Callback> = mutableListOf()

    private var progressCallbacks: MutableList<ProgressCallback> = mutableListOf()

    init {
        playback.callback = this
    }

    fun togglePlayback() {
        if (playback.isPlaying()) {
            playback.pause()
        } else {
            play()
        }
    }

    fun load(songs: List<Song>, position: Int = 0, playOnComplete: Boolean) {
        queueManager.set(songs, position)

        if (playOnComplete) {
            playback.load(playOnComplete)
        }
    }

    fun play() {
        Timber.d("play() called")

        playback.play()

        ContextCompat.startForegroundService(context, Intent(context, PlaybackService::class.java))

        mediaSession.setCallback(mediaSessionCallback)
        mediaSession.isActive = true
    }

    fun pause() {
        playback.pause()

        mediaSession.isActive = false
    }

    fun skipToNext() {
        queueManager.skipToNext()
        playback.load(true)
    }

    fun skipToPrev() {
        queueManager.skipToPrevious()
        playback.load(true)
    }

    fun isPlaying(): Boolean {
        return playback.isPlaying()
    }

    fun addCallback(callback: Playback.Callback) {
        if (!callbacks.contains(callback)) {
            callbacks.add(callback)
        }
    }

    fun removeCallback(callback: Playback.Callback) {
        callbacks.remove(callback)
    }

    fun addProgressCallback(callback: ProgressCallback) {
        if (!progressCallbacks.contains(callback)) {
            progressCallbacks.add(callback)
        }

        monitorProgress(playback.isPlaying())
    }

    fun removeProgressCallback(callback: ProgressCallback) {
        progressCallbacks.remove(callback)

        monitorProgress(playback.isPlaying())
    }

    fun getPosition(): Int? {
        return playback.getPosition()
    }

    fun getDuration(): Int? {
        return playback.getDuration()
    }

    fun seekTo(position: Int) {
        playback.seek(position)
    }


    // Private

    private fun monitorProgress(isPlaying: Boolean) {
        if (isPlaying && progressCallbacks.isNotEmpty()) {
            handler.start { updateProgress() }
        } else {
            handler.stop()
        }
    }

    private fun updateProgress() {
        progressCallbacks.forEach { callback -> callback.onPregressChanged(playback.getPosition() ?: 0, playback.getDuration() ?: 0) }
    }


    // MediaSessionCompat.Callback Implementation

    private val mediaSessionCallback = object : MediaSessionCompat.Callback() {
        override fun onPlay() {
            playback.play()
        }

        override fun onPause() {
            playback.pause()
        }

        override fun onSkipToPrevious() {
            skipToPrev()
        }

        override fun onSkipToNext() {
            skipToNext()
        }

        override fun onSeekTo(pos: Long) {
            playback.seek(pos.toInt())
        }
    }


    // Playback.Callback Implementation

    override fun onPlaystateChanged(isPlaying: Boolean) {
        callbacks.forEach { callback -> callback.onPlaystateChanged(isPlaying) }

        monitorProgress(isPlaying)
    }

    override fun onPlaybackPrepared() {
        callbacks.forEach { callback -> callback.onPlaybackPrepared() }
    }

    override fun onPlaybackComplete(song: Song?) {
        callbacks.forEach { callback -> callback.onPlaybackComplete(song) }
    }


    /**
     * A simple handler which executes continuously between start() and stop()
     */
    class ProgressHandler : Handler(Looper.getMainLooper()) {

        var callback: (() -> Unit)? = null

        private val runnable = object : Runnable {
            override fun run() {
                callback?.invoke()
                postDelayed(this, 100)
            }
        }

        fun start(callback: () -> Unit) {
            Timber.d("start()")
            this.callback = callback
            post(runnable)
        }

        fun stop() {
            Timber.d("stop()")
            this.callback = null
            removeCallbacks(runnable)
        }
    }
}