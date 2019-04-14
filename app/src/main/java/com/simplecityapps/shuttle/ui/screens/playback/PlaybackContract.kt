package com.simplecityapps.shuttle.ui.screens.playback

import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.playback.queue.QueueItem
import com.simplecityapps.playback.queue.QueueManager

interface PlaybackContract {

    interface View {

        fun setPlayState(isPlaying: Boolean)

        fun setShuffleMode(shuffleMode: QueueManager.ShuffleMode)

        fun setRepeatMode(repeatMode: QueueManager.RepeatMode)

        fun setCurrentSong(song: Song?)

        fun setQueue(queue: List<QueueItem>, position: Int?)

        fun setQueuePosition(position: Int?, total: Int, smoothScroll: Boolean)

        fun setProgress(position: Int, duration: Int)

        fun presentSleepTimer()
    }

    interface Presenter {

        fun togglePlayback()

        fun toggleShuffle()

        fun toggleRepeat()

        fun skipNext()

        fun skipPrev()

        fun skipTo(position: Int)

        fun seekForward(seconds: Int)

        fun seekBackward(seconds: Int)

        fun seek(fraction: Float)

        fun sleepTimerClicked()
    }
}