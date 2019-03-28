package com.simplecityapps.playback

import com.simplecityapps.mediaprovider.model.Song

interface PlaybackWatcherCallback {

    fun onProgressChanged(position: Int, total: Int) {

    }

    fun onPlaystateChanged(isPlaying: Boolean) {

    }

    fun onPlaybackComplete(song: Song) {

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

    override fun onProgressChanged(position: Int, total: Int) {
        callbacks.forEach { callback -> callback.onProgressChanged(position, total) }
    }

    override fun onPlaystateChanged(isPlaying: Boolean) {
        callbacks.forEach { callback -> callback.onPlaystateChanged(isPlaying) }
    }

    override fun onPlaybackComplete(song: Song) {
        callbacks.forEach { callback -> callback.onPlaybackComplete(song) }
    }
}