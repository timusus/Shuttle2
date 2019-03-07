package com.simplecityapps.playback.local

import android.media.MediaPlayer
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

    override fun load(playOnPrepared: Boolean) {
        Timber.d("load()")

        loadCurrent(playOnPrepared)
        loadNext()
    }

    private fun loadCurrent(playOnPrepared: Boolean) {
        Timber.d("loadCurrent()")
        currentMediaPlayerHelper.callback = currentPlayerCallback
        currentQueueItem = queueManager.getCurrentItem()
        currentQueueItem?.let { currentQueueItem ->
            currentMediaPlayerHelper.load(currentQueueItem.song, playOnPrepared)
        } ?: Timber.d("loadCurrent() current song null")
    }

    private fun loadNext() {
        Timber.d("loadNext()")
        nextMediaPlayerHelper.callback = nextPlayerCallback
        nextQueueItem = queueManager.getNext()
        nextQueueItem?.let { nextQueueItem ->
            nextMediaPlayerHelper.load(nextQueueItem.song, false)
        } ?: Timber.d("loadNext() next song null")
    }

    override fun play() {
        Timber.d("play()")
        currentMediaPlayerHelper.play()
    }

    override fun pause() {
        Timber.d("pause()")
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
            Timber.d("Releasing current player")
            currentMediaPlayerHelper.callback = null
            currentMediaPlayerHelper.mediaPlayer?.reset()
            currentMediaPlayerHelper.mediaPlayer?.release()

            // Make next media player current
            Timber.d("Setting next player as current player")
            currentMediaPlayerHelper = nextMediaPlayerHelper
            currentMediaPlayerHelper.tag = "CurrentMediaPlayer"
            currentMediaPlayerHelper.callback = this

            // Update queue
            Timber.d("Updating queue")
            currentQueueItem = nextQueueItem
            currentQueueItem?.let { currentQueueItem ->
                queueManager.setCurrentItem(currentQueueItem)
            }

            // Load next song
            nextMediaPlayerHelper = MediaPlayerHelper()
            nextMediaPlayerHelper.tag = "NextMediaPlayer"

            if (nextQueueItem != null) {
                Timber.d("Loading next song")
                loadNext()
            } else {
                Timber.d("No next song. Playback complete")
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


class MediaPlayerHelper {

    var mediaPlayer: MediaPlayer? = null

    var callback: Playback.Callback? = null

    var tag: String? = "MediaPlayerHelper"

    var isPrepared: Boolean = false
        private set

    private var isPreparing: Boolean = false

    private var playOnPrepared: Boolean = false

    fun load(song: Song, playOnPrepared: Boolean) {

        Timber.d("$tag load() song: ${song.path}, playOnPrepared: $playOnPrepared")

        this.playOnPrepared = playOnPrepared

        isPrepared = false

        mediaPlayer = mediaPlayer?.apply {
            Timber.d("$tag MediaPlayer.reset()")
            this@MediaPlayerHelper.pause()
            reset()
        } ?: MediaPlayer()

        mediaPlayer!!.setOnCompletionListener(onCompletionListener)
        mediaPlayer!!.setOnErrorListener(onErrorListener)
        mediaPlayer!!.setOnPreparedListener(onPreparedListener)
        mediaPlayer!!.setDataSource(song.path)

        isPreparing = true
        Timber.d("$tag MediaPlayer.prepareAsync()")
        mediaPlayer!!.prepareAsync()
    }

    fun play() {
        when {
            isPrepared -> {
                Timber.d("$tag play() called. Attempting to play.")
                mediaPlayer?.let { mediaPlayer ->
                    if (!isPlaying()) {
                        mediaPlayer.start()
                        callback?.onPlaystateChanged(true)
                    }
                } ?: run {
                    Timber.d("$tag play() called, Media player null")
                }
                playOnPrepared = false
            }
            isPreparing -> {
                Timber.d("$tag play() called. preparing..")
                playOnPrepared = true
            }
            else -> Timber.d("$tag play() called. Not prepared or preparing...")
        }
    }

    fun isPlaying(): Boolean {
        return isPrepared && mediaPlayer?.isPlaying ?: false
    }

    fun pause() {
        Timber.d("$tag pause()")
        if (isPlaying()) {
            mediaPlayer?.pause()
            callback?.onPlaystateChanged(false)
        }
    }

    fun seek(position: Int) {
        Timber.d("seekTo() $position")
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

    fun setNextMediaPlayer(nextMediaPlayer: MediaPlayer?) {
        Timber.d("$tag setNextMediaPlayer()")
        if (isPrepared) {
            mediaPlayer?.setNextMediaPlayer(nextMediaPlayer)
        } else {
            Timber.d("$tag setNextMediaPlayer() current MediaPlayer not prepared")
        }
    }

    private val onPreparedListener = object : MediaPlayer.OnPreparedListener {
        override fun onPrepared(mp: MediaPlayer?) {
            Timber.d("$tag onPrepared()")

            isPreparing = false
            isPrepared = true

            if (playOnPrepared) {
                play()
            }

            callback?.onPlaybackPrepared()
        }
    }

    private val onErrorListener = object : MediaPlayer.OnErrorListener {
        override fun onError(mp: MediaPlayer?, what: Int, extra: Int): Boolean {
            Timber.d("$tag onError()")

            isPreparing = false
            isPrepared = false
            mediaPlayer?.release()
            mediaPlayer = null

            return false
        }
    }

    private val onCompletionListener = object : MediaPlayer.OnCompletionListener {
        override fun onCompletion(mp: MediaPlayer?) {
            Timber.d("$tag onCompletion()")
            callback?.onPlaybackComplete(null)
        }
    }
}