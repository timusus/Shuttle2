package com.simplecityapps.playback.queue

import com.simplecityapps.mediaprovider.model.Song

class PlaybackManager(
    private val queueManager: QueueManager
) {

    fun play(songs: List<Song>, position: Int = 0) {
        queueManager.set(songs, position)
    }
}