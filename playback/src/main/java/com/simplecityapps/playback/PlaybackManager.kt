package com.simplecityapps.playback

import android.content.Context
import android.content.Intent
import android.support.v4.media.session.MediaSessionCompat
import androidx.core.content.ContextCompat
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.playback.queue.QueueManager
import timber.log.Timber

class PlaybackManager(
    private val context: Context,
    private val queueManager: QueueManager,
    val playback: Playback
) : Playback.Callback {

    val mediaSession: MediaSessionCompat by lazy {
        MediaSessionCompat(context, "ShuttleMediaSession")
    }

    var callbacks: MutableList<Playback.Callback> = mutableListOf()

    init {
        playback.callback = this
    }

    fun togglePlayback() {
        if (playback.isPlaying()) {
            playback.pause()
        } else {
            // Todo: This should call load(), in case there is no queue
            playback.play()
        }
    }

    fun play(songs: List<Song>, position: Int = 0) {

        Timber.d("play() called, position: $position")

        queueManager.set(songs, position)

        ContextCompat.startForegroundService(context, Intent(context, PlaybackService::class.java))

        playback.load(songs[position], true)

        mediaSession.isActive = true

        mediaSession.setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() {
                super.onPlay()

                playback.play()
            }

            override fun onPause() {
                super.onPause()

                playback.pause()
            }
        })
    }

    fun pause() {
        playback.pause()

        mediaSession.isActive = false
    }

    fun addCallback(callback: Playback.Callback) {
        if (!callbacks.contains(callback)) {
            callbacks.add(callback)
        }
    }

    fun removeCallback(callback: Playback.Callback) {
        callbacks.remove(callback)
    }


    // Playback.Callback implementation

    override fun onPlaystateChanged(isPlaying: Boolean) {
        callbacks.forEach { it.onPlaystateChanged(isPlaying) }
    }
}