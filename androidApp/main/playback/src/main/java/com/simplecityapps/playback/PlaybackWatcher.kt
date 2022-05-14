package com.simplecityapps.playback

import com.simplecityapps.shuttle.model.Song
import timber.log.Timber

interface PlaybackWatcherCallback {

    fun onProgressChanged(position: Int, duration: Int, fromUser: Boolean) {
    }

    fun onPlaybackStateChanged(playbackState: PlaybackState) {
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

    override fun onPlaybackStateChanged(playbackState: PlaybackState) {
        Timber.v("onPlaybackStateChanged(playbackState: $playbackState)")
        callbacks.forEach { callback -> callback.onPlaybackStateChanged(playbackState) }
    }

    override fun onTrackEnded(song: Song) {
        Timber.v("onTrackEnded(song: ${song.name})")
        callbacks.forEach { callback -> callback.onTrackEnded(song) }
    }
}
