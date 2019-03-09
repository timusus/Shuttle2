package com.simplecityapps.playback.local

import android.media.MediaPlayer
import android.net.Uri
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.playback.Playback
import com.simplecityapps.playback.queue.QueueItem
import com.simplecityapps.playback.queue.QueueManager
import timber.log.Timber
import java.io.File

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


class MediaPlayerHelper {

    var mediaPlayer: MediaPlayer? = null

    var callback: Playback.Callback? = null

    var tag: String? = "MediaPlayerHelper"

    var isPrepared: Boolean = false
        private set

    var seekPosition: Int = 0

    private var isPreparing: Boolean = false

    private var playOnPrepared: Boolean = false

    fun load(song: Song, seekPosition: Int = 0, playOnPrepared: Boolean) {

        Timber.v("$tag load() song: ${song.path}, playOnPrepared: $playOnPrepared")

        this.playOnPrepared = playOnPrepared

        this.seekPosition = seekPosition

        isPrepared = false

        if (mediaPlayer == null) {
            mediaPlayer = MediaPlayer()
        } else {
            Timber.v("$tag MediaPlayer.reset()")
            pause()
            mediaPlayer!!.reset()
        }

        mediaPlayer!!.setOnCompletionListener(onCompletionListener)
        mediaPlayer!!.setOnErrorListener(onErrorListener)
        mediaPlayer!!.setOnPreparedListener(onPreparedListener)
        mediaPlayer!!.setDataSource(Uri.fromFile(File(song.path)).toString())

        isPreparing = true
        Timber.v("$tag MediaPlayer.prepareAsync()")
        mediaPlayer!!.prepareAsync()
    }

    fun play() {
        when {
            isPrepared -> {
                Timber.v("$tag play() called. Attempting to play.")
                mediaPlayer?.let { mediaPlayer ->
                    if (!isPlaying()) {
                        mediaPlayer.start()
                        callback?.onPlaystateChanged(true)
                    }
                } ?: run {
                    Timber.v("$tag play() called, Media player null")
                }
                playOnPrepared = false
            }
            isPreparing -> {
                Timber.v("$tag play() called. preparing..")
                playOnPrepared = true
            }
            else -> Timber.v("$tag play() called. Not prepared or preparing...")
        }
    }

    fun isPlaying(): Boolean {
        return isPrepared && mediaPlayer?.isPlaying ?: false
    }

    fun pause() {
        Timber.v("$tag pause()")
        if (isPlaying()) {
            mediaPlayer?.pause()
            callback?.onPlaystateChanged(false)
        }
        if (isPreparing) {
            Timber.v("pause() called while preparing. Cancelling playOnPrepared.")
            playOnPrepared = false
        }
    }

    fun seek(position: Int) {
        Timber.v("seekTo() $position")
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
        Timber.v("$tag setNextMediaPlayer()")
        if (isPrepared) {
            mediaPlayer?.setNextMediaPlayer(nextMediaPlayer)
        } else {
            Timber.v("$tag setNextMediaPlayer() current MediaPlayer not prepared")
        }
    }

    private val onPreparedListener = MediaPlayer.OnPreparedListener {
        Timber.v("$tag onPrepared()")

        isPreparing = false
        isPrepared = true

        if (seekPosition != 0) {
            seek(seekPosition)
            seekPosition = 0
        }

        if (playOnPrepared) {
            play()
        }

        callback?.onPlaybackPrepared()
    }

    private val onErrorListener = object : MediaPlayer.OnErrorListener {
        override fun onError(mp: MediaPlayer?, what: Int, extra: Int): Boolean {
            Timber.v("$tag onError()")

            isPreparing = false
            isPrepared = false
            mediaPlayer?.release()
            mediaPlayer = null

            return false
        }
    }

    private val onCompletionListener = MediaPlayer.OnCompletionListener {
        Timber.v("$tag onCompletion()")
        callback?.onPlaybackComplete(null)
    }
}