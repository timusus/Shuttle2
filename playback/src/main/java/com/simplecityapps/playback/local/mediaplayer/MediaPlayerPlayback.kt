package com.simplecityapps.playback.local.mediaplayer

import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.playback.Playback
import com.simplecityapps.playback.queue.QueueItem
import com.simplecityapps.playback.queue.QueueManager
import timber.log.Timber

class MediaPlayerPlayback(
    private val queueManager: QueueManager
) : Playback {

    private var currentMediaPlayerHelper = MediaPlayerHelper()

    private var nextMediaPlayerHelper = MediaPlayerHelper()

    private var currentQueueItem: QueueItem? = null

    private var nextQueueItem: QueueItem? = null

    override var callback: Playback.Callback? = null

    init {
        currentMediaPlayerHelper.tag = "CurrentMediaPlayer"
        nextMediaPlayerHelper.tag = "NextMediaPlayer"
    }

    override fun load(seekPosition: Int, playOnPrepared: Boolean) {
        Timber.v("load() position: $seekPosition, playOnPrepared: $playOnPrepared")

        loadCurrent(seekPosition, playOnPrepared)
        loadNext()
    }

    private fun loadCurrent(seekPosition: Int, playOnPrepared: Boolean) {
        Timber.v("loadCurrent()")
        currentMediaPlayerHelper.callback = currentPlayerCallback
        currentQueueItem = queueManager.getCurrentItem()
        currentQueueItem?.let { currentQueueItem ->
            currentMediaPlayerHelper.load(currentQueueItem.song, seekPosition, playOnPrepared)
        } ?: Timber.v("loadCurrent() current song null")
    }


    private fun loadNext() {
        Timber.v("loadNext()")
        nextMediaPlayerHelper.callback = nextPlayerCallback
        nextQueueItem = queueManager.getNext()
        nextQueueItem?.let { nextQueueItem ->
            nextMediaPlayerHelper.load(nextQueueItem.song, 0 ,false)
        } ?: Timber.v("loadNext() next song null")
    }

    override fun play() {
        Timber.v("play()")
        currentMediaPlayerHelper.play()
    }

    override fun pause() {
        Timber.v("pause()")
        currentMediaPlayerHelper.pause()
    }

    override fun isPlaying(): Boolean {
        return currentMediaPlayerHelper.isPlaying()
    }

    override fun seek(position: Int) {
        currentMediaPlayerHelper.seek(position)
    }

    override fun getPosition(): Int? {
        return currentMediaPlayerHelper.getPosition()
    }

    override fun getDuration(): Int? {
        return currentMediaPlayerHelper.getDuration()
    }

    override fun setVolume(volume: Float) {
        currentMediaPlayerHelper.volume = volume
        nextMediaPlayerHelper.volume = volume
    }

    private val currentPlayerCallback = object : Playback.Callback {

        override fun onPlaystateChanged(isPlaying: Boolean) {
            callback?.onPlaystateChanged(isPlaying)
        }

        override fun onPlaybackPrepared() {
            if (nextMediaPlayerHelper.isPrepared) {
                currentMediaPlayerHelper.setNextMediaPlayer(nextMediaPlayerHelper.mediaPlayer)
            }

            callback?.onPlaybackPrepared()
        }

        override fun onPlaybackComplete(song: Song?) {
            callback?.onPlaybackComplete(currentQueueItem?.song)

            // Release current media player
            Timber.v("Releasing current player")
            currentMediaPlayerHelper.callback = null
            currentMediaPlayerHelper.mediaPlayer?.reset()
            currentMediaPlayerHelper.mediaPlayer?.release()

            // Make next media player current
            Timber.v("Setting next player as current player")
            currentMediaPlayerHelper = nextMediaPlayerHelper
            currentMediaPlayerHelper.tag = "CurrentMediaPlayer"
            currentMediaPlayerHelper.callback = this

            // Update queue
            Timber.v("Updating queue")
            currentQueueItem = nextQueueItem
            currentQueueItem?.let { currentQueueItem ->
                queueManager.setCurrentItem(currentQueueItem)
            }

            // Load next song
            nextMediaPlayerHelper = MediaPlayerHelper()
            nextMediaPlayerHelper.tag = "NextMediaPlayer"

            if (nextQueueItem != null) {
                Timber.v("Loading next song")
                loadNext()
            } else {
                Timber.v("No next song. Playback complete")
                callback?.onPlaystateChanged(false)
            }
        }
    }

    private val nextPlayerCallback = object : Playback.Callback {

        override fun onPlaystateChanged(isPlaying: Boolean) {

        }

        override fun onPlaybackPrepared() {
            currentMediaPlayerHelper.setNextMediaPlayer(nextMediaPlayerHelper.mediaPlayer)
        }

        override fun onPlaybackComplete(song: Song?) {

        }
    }
}