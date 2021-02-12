package com.simplecityapps.playback

import com.simplecityapps.mediaprovider.model.Song
import timber.log.Timber

interface PlaybackWatcherCallback {

    fun onProgressChanged(position: Int, duration: Int, fromUser: Boolean) {

    }

    fun onPlaystateChanged(isPlaying: Boolean) {

    }

    fun onTrackEnded(song: Song) {

    }
}


class PlaybackWatcher : PlaybackWatcherCallback {

    private var callbacks: MutableSet<PlaybackWatcherCallback> = mutableSetOf()

    fun addCallback(callback: PlaybackWatcherCallback) {
        callbacks.add(callback)
    }

    fun removeCallback(callback: PlaybackWatcherCallback) {
        callbacks.remove(callback)
    }


    // PlaybackWatcherCallback Implementation

    override fun onProgressChanged(position: Int, duration: Int, fromUser: Boolean) {
        callbacks.forEach { callback -> callback.onProgressChanged(position, duration, fromUser) }
    }

    override fun onPlaystateChanged(isPlaying: Boolean) {
        Timber.v("onPlaystateChanged(isPlaying: ${isPlaying})")
        callbacks.forEach { callback -> callback.onPlaystateChanged(isPlaying) }
    }

    override fun onTrackEnded(song: Song) {
        Timber.v("onPlaybackComplete(song: ${song.name})")
        callbacks.forEach { callback -> callback.onTrackEnded(song) }
    }
}