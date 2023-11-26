package com.simplecityapps.shuttle.ui.screens.playback

import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.queue.QueueItem
import com.simplecityapps.playback.queue.QueueManager

interface PlaybackContract {

    interface View {
        fun setPlaybackState(playbackState: PlaybackState)
        fun setShuffleMode(shuffleMode: QueueManager.ShuffleMode)
        fun setRepeatMode(repeatMode: QueueManager.RepeatMode)
        fun setCurrentSong(song: com.simplecityapps.shuttle.model.Song?)
        fun setQueue(queue: List<QueueItem>)
        fun clearQueue()
        fun setQueuePosition(position: Int?, total: Int)
        fun setProgress(position: Int, duration: Int)
        fun setIsFavorite(isFavorite: Boolean)
        fun presentSleepTimer()
        fun goToAlbum(album: com.simplecityapps.shuttle.model.Album)
        fun goToArtist(artist: com.simplecityapps.shuttle.model.AlbumArtist)
        fun launchQuickLyric(artistName: String, songName: String)
        fun getQuickLyric()
        fun showQuickLyricUnavailable()
        fun showSongInfoDialog(song: com.simplecityapps.shuttle.model.Song)
        fun displayLyrics(lyrics: String)
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
        fun updateProgress(fraction: Float)
        fun sleepTimerClicked()
        fun setFavorite(isFavorite: Boolean)
        fun goToAlbum()
        fun goToArtist()
        fun showSongInfo()
        fun showOrLaunchLyrics()
        fun launchQuickLyric()
        fun clearQueue()
    }
}
